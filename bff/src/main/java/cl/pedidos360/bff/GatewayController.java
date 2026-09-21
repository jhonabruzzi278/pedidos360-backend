package cl.pedidos360.bff;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class GatewayController {
  /** Un pedido cabe holgadamente: 50 items de ~100 bytes. Un cuerpo mayor se rechaza sin leerlo entero. */
  static final int MAX_BODY_BYTES = 64 * 1024;

  private final RestClient ordersClient;
  private final RestClient auditClient;

  public GatewayController(@Qualifier("ordersClient") RestClient ordersClient,
      @Qualifier("auditClient") RestClient auditClient) {
    this.ordersClient = ordersClient;
    this.auditClient = auditClient;
  }

  @GetMapping("/work-orders")
  ResponseEntity<String> workOrders() {
    return ResponseEntity.ok(ordersClient.get().uri("/internal/work-orders").retrieve().body(String.class));
  }

  @GetMapping("/events")
  ResponseEntity<String> events() {
    return ResponseEntity.ok(auditClient.get().uri("/internal/events").retrieve().body(String.class));
  }

  /**
   * El BFF autoriza, acota el tamano y reenvia los bytes tal cual; la validacion del contenido es
   * responsabilidad del microservicio.
   */
  @PostMapping(path = "/work-orders", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<String> createWorkOrder(HttpServletRequest request) throws IOException {
    byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
    if (body.length == 0) {
      return ApiError.response(HttpStatus.BAD_REQUEST, "bad_request", "El cuerpo de la solicitud es obligatorio");
    }
    if (body.length > MAX_BODY_BYTES) {
      return ApiError.response(HttpStatusCode.valueOf(413), "payload_too_large",
          "El cuerpo de la solicitud excede el tamano permitido");
    }
    ResponseEntity<String> created = ordersClient.post().uri("/internal/work-orders")
        .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(String.class);
    return ResponseEntity.status(created.getStatusCode()).body(created.getBody());
  }
}
