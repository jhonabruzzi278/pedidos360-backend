package cl.pedidos360.orders;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/work-orders")
public class WorkOrderController {
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
  public WorkOrderResponse create(@Valid @RequestBody CreateWorkOrderRequest request) {
    WorkOrder saved = repository.save(request.toEntity(WorkOrderIds.next(repository::existsById)));
    return WorkOrderResponse.from(saved);
  }

  public record WorkOrderResponse(String id, String clientId, String licensePlate,
      String description, long total, Instant createdAt, int itemCount, long calculatedSubtotal) {
    static WorkOrderResponse from(WorkOrder order) {
      long subtotal = order.getItems().stream().mapToLong(WorkOrderItem::getSubtotal).sum();
      return new WorkOrderResponse(order.getId(), order.getClientId(), order.getLicensePlate(),
          order.getDescription(), order.getTotal(), order.getCreatedAt(), order.getItems().size(), subtotal);
    }
  }
}
