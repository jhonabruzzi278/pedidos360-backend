package cl.pedidos360.bff;

import java.util.List;
import java.util.Objects;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

/**
 * Reglas de claims comunes a todos los perfiles: vigencia (exp/nbf), issuer y audience. El validador
 * por defecto de Spring acepta un token sin "exp"; aqui es obligatorio.
 */
final class JwtTokenValidation {
  private JwtTokenValidation() {}

  static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer),
        new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
        new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
            audiences -> audiences != null && audiences.contains(audience)));
  }
}
