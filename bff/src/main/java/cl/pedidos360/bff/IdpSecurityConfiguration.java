package cl.pedidos360.bff;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Configuracion por defecto: firma verificada con las claves publicas del emisor (Entra ID). */
@Configuration
@Profile("!local")
class IdpSecurityConfiguration {
  @Bean
  JwtDecoder jwtDecoder(@Value("${security.jwt.issuer}") String issuer,
      @Value("${security.jwt.audience}") String audience) {
    // Consulta el discovery del emisor al construirse: un issuer mal escrito o inaccesible impide
    // arrancar (falla rapido) en vez de rechazar todas las peticiones en silencio.
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
    decoder.setJwtValidator(JwtTokenValidation.validator(issuer, audience));
    return decoder;
  }
}
