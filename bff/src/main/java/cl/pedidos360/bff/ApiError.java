package cl.pedidos360.bff;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Cuerpo de error unico del BFF: {status, error, message}. Los textos son fijos, nunca del entorno. */
final class ApiError {
  private ApiError() {}

  static String json(int status, String error, String message) {
    return "{\"status\":" + status + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\"}";
  }

  static ResponseEntity<String> response(HttpStatusCode status, String error, String message) {
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
        .body(json(status.value(), error, message));
  }
}
