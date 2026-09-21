package cl.pedidos360.bff;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
  /** Error que la interfaz reconoce para ofrecer "Solicitar acceso al administrador". */
  static final String ACCESS_REQUIRED = "access_required";
  static final String ACCESS_REQUIRED_MESSAGE = "Necesitas la autorizacion del administrador para generar cotizaciones";
  /** Nombre de quien emite la cotizacion, codificado como URL (los encabezados HTTP no admiten tildes). */
  static final String CALLER_NAME_HEADER = "X-Caller-Name";

  private final RestClient ordersClient;
  private final RestClient auditClient;
  private final AccessDirectory access;

  public GatewayController(@Qualifier("ordersClient") RestClient ordersClient,
      @Qualifier("auditClient") RestClient auditClient, AccessDirectory access) {
    this.ordersClient = ordersClient;
    this.auditClient = auditClient;
    this.access = access;
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
   * Generar una cotizacion exige el scope orders.write (lo verifica la cadena de seguridad) y ademas ser admin o
   * tener el acceso aprobado por un admin. El BFF autoriza, acota el tamano y reenvia los bytes tal cual; la
   * validacion del contenido es responsabilidad del microservicio. El nombre de quien emite viaja en un
   * encabezado que arma el BFF: nunca se reenvia nada que venga del cliente.
   */
  @PostMapping(path = "/work-orders", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<String> createWorkOrder(HttpServletRequest request, Authentication authentication,
      @AuthenticationPrincipal Jwt jwt) throws IOException {
    CallerIdentity caller = CallerIdentity.from(jwt);
    if (!isAdmin(authentication) && !access.hasApprovedAccess(caller.id())) {
      return ApiError.response(HttpStatus.FORBIDDEN, ACCESS_REQUIRED, ACCESS_REQUIRED_MESSAGE);
    }
    byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
    if (body.length == 0) {
      return ApiError.response(HttpStatus.BAD_REQUEST, "bad_request", "El cuerpo de la solicitud es obligatorio");
    }
    if (body.length > MAX_BODY_BYTES) {
      return ApiError.response(HttpStatusCode.valueOf(413), "payload_too_large",
          "El cuerpo de la solicitud excede el tamano permitido");
    }
    ResponseEntity<String> created = ordersClient.post().uri("/internal/work-orders")
        .contentType(MediaType.APPLICATION_JSON)
        .header(CALLER_NAME_HEADER, URLEncoder.encode(caller.name(), StandardCharsets.UTF_8))
        .body(body).retrieve().toEntity(String.class);
    return ResponseEntity.status(created.getStatusCode()).body(created.getBody());
  }

  private static boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> ("ROLE_" + SecurityConfiguration.ADMIN_ROLE).equals(authority.getAuthority()));
  }
}
