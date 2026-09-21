package cl.pedidos360.bff;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Traduce los fallos de los microservicios internos. Solo se propagan los rechazos de entrada de la
 * lista permitida, y siempre con un cuerpo propio: el del microservicio puede traer rutas o trazas.
 * Cualquier otra respuesta (incluidos 401/403 internos, que el cliente confundiria con su propio
 * token) pasa a 502; los timeouts y las conexiones caidas, a 503. El detalle solo queda en el log.
 */
@RestControllerAdvice
class DownstreamExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(DownstreamExceptionHandler.class);
  private static final Map<Integer, String> PROPAGATED_REJECTIONS = Map.of(
      400, "bad_request", 404, "not_found", 409, "conflict", 422, "unprocessable_entity");

  @ExceptionHandler(RestClientResponseException.class)
  ResponseEntity<String> serviceResponded(RestClientResponseException exception) {
    HttpStatusCode status = exception.getStatusCode();
    String error = PROPAGATED_REJECTIONS.get(status.value());
    if (error != null) {
      return ApiError.response(status, error, "La solicitud fue rechazada por el servicio");
    }
    log.error("Microservicio interno respondio con estado {}", status.value());
    return ApiError.response(HttpStatus.BAD_GATEWAY, "bad_gateway",
        "El servicio interno no pudo procesar la solicitud");
  }

  @ExceptionHandler(ResourceAccessException.class)
  ResponseEntity<String> serviceUnreachable(ResourceAccessException exception) {
    log.error("No fue posible contactar al microservicio interno", exception);
    return ApiError.response(HttpStatus.SERVICE_UNAVAILABLE, "service_unavailable",
        "El servicio interno no esta disponible");
  }
}
