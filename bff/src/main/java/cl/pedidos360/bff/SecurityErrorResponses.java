package cl.pedidos360.bff;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Respuestas 401/403 con cuerpo JSON y cabecera WWW-Authenticate minima (RFC 6750). No se usan los
 * manejadores por defecto de Spring: incluyen el motivo exacto del rechazo (expirado, firma invalida,
 * audience...) y una URL construida con la cabecera Host de la peticion.
 */
final class SecurityErrorResponses {
  private static final String BEARER = "Bearer";

  private SecurityErrorResponses() {}

  static AuthenticationEntryPoint unauthorized() {
    return (request, response, exception) -> {
      // Sin credenciales solo se anuncia el esquema; con un token rechazado, el codigo generico.
      boolean tokenPresented = request.getHeader(HttpHeaders.AUTHORIZATION) != null;
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
          tokenPresented ? BEARER + " error=\"invalid_token\"" : BEARER);
      writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized",
          "Token ausente, invalido o expirado");
    };
  }

  static AccessDeniedHandler forbidden() {
    return (request, response, exception) -> {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER + " error=\"insufficient_scope\"");
      writeJson(response, HttpServletResponse.SC_FORBIDDEN, "forbidden",
          "Permisos insuficientes para este recurso");
    };
  }

  private static void writeJson(HttpServletResponse response, int status, String error, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(ApiError.json(status, error, message));
  }
}
