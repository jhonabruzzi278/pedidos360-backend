package cl.pedidos360.bff;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Quien llama, tomado de un token que ya paso la validacion de firma, emisor, audiencia y vigencia. El id es el
 * {@code oid} de Entra ID (estable dentro del tenant) o, si falta, el {@code sub}: sin ninguno no se sabe a quien
 * pertenece un permiso, asi que el token se trata como invalido. Los textos se limpian de controles y se acotan
 * al largo de las columnas donde terminan.
 */
record CallerIdentity(String id, String name, String email) {
  static final int MAX_ID = 64;
  static final int MAX_NAME = 100;
  static final int MAX_EMAIL = 150;

  static CallerIdentity from(Jwt jwt) {
    String id = text(jwt, "oid", MAX_ID);
    if (id.isEmpty()) id = text(jwt, "sub", MAX_ID);
    if (id.isEmpty()) throw new InvalidBearerTokenException("El token no identifica al usuario");

    String preferred = text(jwt, "preferred_username", MAX_EMAIL);
    String email = text(jwt, "email", MAX_EMAIL);
    if (email.isEmpty() && preferred.contains("@")) email = preferred;

    String name = text(jwt, "name", MAX_NAME);
    if (name.isEmpty()) name = cut(preferred, MAX_NAME);
    if (name.isEmpty()) name = cut(email, MAX_NAME);
    if (name.isEmpty()) name = cut(id, MAX_NAME);
    return new CallerIdentity(id, name, email);
  }

  private static String text(Jwt jwt, String claim, int max) {
    Object value = jwt.getClaims().get(claim);
    return value instanceof String text ? cut(text, max) : "";
  }

  private static String cut(String value, int max) {
    String cleaned = value.replaceAll("\\p{Cntrl}", "").strip();
    return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
  }
}
