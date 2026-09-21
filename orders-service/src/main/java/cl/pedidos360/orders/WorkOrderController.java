package cl.pedidos360.orders;

import jakarta.validation.Valid;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/work-orders")
public class WorkOrderController {
  /** Encabezado con el nombre de quien emite la cotizacion, codificado como URL: lo agrega el BFF. */
  static final String CALLER_NAME_HEADER = "X-Caller-Name";
  private static final int MAX_CALLER_NAME = 100;

  private final WorkOrderRepository repository;

  public WorkOrderController(WorkOrderRepository repository) {
    this.repository = repository;
  }

  @GetMapping
  public List<WorkOrderResponse> findAll() {
    return repository.findAll().stream().map(WorkOrderResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public WorkOrderResponse create(@Valid @RequestBody CreateWorkOrderRequest request,
      @RequestHeader(value = CALLER_NAME_HEADER, required = false) String callerName) {
    WorkOrder order = request.toEntity(WorkOrderIds.next(repository::existsById));
    order.recordCreator(cleanCallerName(callerName));
    return WorkOrderResponse.from(repository.save(order));
  }

  /** El nombre llega codificado (los encabezados HTTP no admiten tildes); se acota y se descartan controles. */
  static String cleanCallerName(String encoded) {
    if (encoded == null) return null;
    String decoded;
    try {
      decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException malformed) {
      return null;
    }
    String cleaned = decoded.replaceAll("\\p{Cntrl}", "").strip();
    if (cleaned.isEmpty()) return null;
    return cleaned.length() > MAX_CALLER_NAME ? cleaned.substring(0, MAX_CALLER_NAME) : cleaned;
  }

  public record ItemResponse(String concept, double quantity, long unitPrice, long subtotal) {
    static ItemResponse from(WorkOrderItem item) {
      return new ItemResponse(item.getConcept(), item.getQuantity(), item.getUnitPrice(), item.getSubtotal());
    }
  }

  public record WorkOrderResponse(String id, String clientId, String licensePlate,
      String description, long total, Instant createdAt, int itemCount, long calculatedSubtotal,
      String createdBy, List<ItemResponse> items) {
    static WorkOrderResponse from(WorkOrder order) {
      List<ItemResponse> items = order.getItems().stream().map(ItemResponse::from).toList();
      long subtotal = items.stream().mapToLong(ItemResponse::subtotal).sum();
      return new WorkOrderResponse(order.getId(), order.getClientId(), order.getLicensePlate(),
          order.getDescription(), order.getTotal(), order.getCreatedAt(), items.size(), subtotal,
          order.getCreatedBy(), items);
    }
  }
}
