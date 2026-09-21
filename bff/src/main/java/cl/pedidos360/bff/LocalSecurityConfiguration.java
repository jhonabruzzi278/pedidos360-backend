package cl.pedidos360.bff;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Solo desarrollo local: tokens HMAC propios y el endpoint /dev/token que los emite.
 * Si el perfil se activara por error fuera del equipo de desarrollo, application-local.yml enlaza el
 * servidor a 127.0.0.1 y la clave de firma no esta en el repositorio (ver {@link #localSigningKey}).
 */
@Configuration
@Profile("local")
class LocalSecurityConfiguration {
  private static final String HMAC_SHA256 = "HmacSHA256";
  private static final int MIN_SECRET_BYTES = 32;

  /** Una sola clave para firmar y verificar: la configurada, o una aleatoria distinta en cada arranque. */
  @Bean
  SecretKey localSigningKey(@Value("${security.jwt.local-secret:}") String configuredSecret) {
    if (configuredSecret.isBlank()) {
      byte[] random = new byte[MIN_SECRET_BYTES];
      new SecureRandom().nextBytes(random);
      return new SecretKeySpec(random, HMAC_SHA256);
    }
    byte[] configured = configuredSecret.getBytes(StandardCharsets.UTF_8);
    if (configured.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException("JWT_LOCAL_SECRET debe tener al menos " + MIN_SECRET_BYTES + " bytes");
    }
    return new SecretKeySpec(configured, HMAC_SHA256);
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKey localSigningKey, @Value("${security.jwt.issuer}") String issuer,
      @Value("${security.jwt.audience}") String audience) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(localSigningKey).build();
    decoder.setJwtValidator(JwtTokenValidation.validator(issuer, audience));
    return decoder;
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey localSigningKey) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(localSigningKey));
  }

  /** /dev/** queda fuera de la cadena principal, que sigue siendo "denegar todo salvo lo declarado". */
  @Bean
  @Order(1)
  SecurityFilterChain devTokenChain(HttpSecurity http) throws Exception {
    return http
        .securityMatcher("/dev/**")
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .build();
  }
}
