package cl.pedidos360.bff;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Fabrica tokens HS256 para pruebas; cada "with" devuelve una copia con un solo cambio. */
record TestToken(String issuer, String audience, String secret, Duration ttl,
    List<String> roles, String scopeClaim, String scopes) {

  static final String LOCAL_SECRET = "local-only-secret-must-have-at-least-32-bytes";
  static final String OTHER_SECRET = "another-secret-that-is-at-least-32-bytes!";

  static TestToken viewer() {
    return new TestToken("pedidos360-local", "api://pedidos360-local", LOCAL_SECRET,
        Duration.ofMinutes(5), List.of("viewer"), "scope", "orders.read events.read");
  }

  static TestToken admin() {
    return viewer().withRoles(List.of("admin")).withScopes("orders.read orders.write events.read");
  }

  TestToken withIssuer(String value) {
    return new TestToken(value, audience, secret, ttl, roles, scopeClaim, scopes);
  }

  TestToken withAudience(String value) {
    return new TestToken(issuer, value, secret, ttl, roles, scopeClaim, scopes);
  }

  TestToken withoutAudience() {
    return withAudience(null);
  }

  TestToken withSecret(String value) {
    return new TestToken(issuer, audience, value, ttl, roles, scopeClaim, scopes);
  }

  TestToken withTtl(Duration value) {
    return new TestToken(issuer, audience, secret, value, roles, scopeClaim, scopes);
  }

  TestToken withoutExpiry() {
    return withTtl(null);
  }

  TestToken withRoles(List<String> value) {
    return new TestToken(issuer, audience, secret, ttl, value, scopeClaim, scopes);
  }

  TestToken withScopes(String value) {
    return new TestToken(issuer, audience, secret, ttl, roles, scopeClaim, value);
  }

  TestToken withScopeClaim(String value) {
    return new TestToken(issuer, audience, secret, ttl, roles, value, scopes);
  }

  String value() {
    Instant now = Instant.now();
    JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(issuer).subject("test-user")
        .issuedAt(now.minus(Duration.ofMinutes(10)))
        .claim("roles", roles).claim(scopeClaim, scopes);
    if (ttl != null) {
      claims.expiresAt(now.plus(ttl));
    }
    if (audience != null) {
      claims.audience(List.of(audience));
    }
    NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
        new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
    return encoder.encode(JwtEncoderParameters.from(
        JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
  }

  String bearer() {
    return "Bearer " + value();
  }
}
