package cl.pedidos360.bff;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Fabrica tokens HS256 para pruebas; cada "with" devuelve una copia con un solo cambio. */
record TestToken(String issuer, String audience, String secret, Duration ttl,
    List<String> roles, String scopeClaim, String scopes, String subject, Map<String, Object> claims) {

  static final String LOCAL_SECRET = "local-only-secret-must-have-at-least-32-bytes";
  static final String OTHER_SECRET = "another-secret-that-is-at-least-32-bytes!";

  static TestToken viewer() {
    return new TestToken("pedidos360-local", "api://pedidos360-local", LOCAL_SECRET,
        Duration.ofMinutes(5), List.of("viewer"), "scope", "orders.read events.read", "test-user", Map.of());
  }

  static TestToken admin() {
    return viewer().withRoles(List.of("admin")).withScopes("orders.read orders.write events.read");
  }

  TestToken withIssuer(String value) {
    return new TestToken(value, audience, secret, ttl, roles, scopeClaim, scopes, subject, claims);
  }

  TestToken withAudience(String value) {
    return new TestToken(issuer, value, secret, ttl, roles, scopeClaim, scopes, subject, claims);
  }

  TestToken withoutAudience() {
    return withAudience(null);
  }

  TestToken withSecret(String value) {
    return new TestToken(issuer, audience, value, ttl, roles, scopeClaim, scopes, subject, claims);
  }

  TestToken withTtl(Duration value) {
    return new TestToken(issuer, audience, secret, value, roles, scopeClaim, scopes, subject, claims);
  }

  TestToken withoutExpiry() {
    return withTtl(null);
  }

  TestToken withRoles(List<String> value) {
    return new TestToken(issuer, audience, secret, ttl, value, scopeClaim, scopes, subject, claims);
  }

  TestToken withScopes(String value) {
    return new TestToken(issuer, audience, secret, ttl, roles, scopeClaim, value, subject, claims);
  }

  TestToken withScopeClaim(String value) {
    return new TestToken(issuer, audience, secret, ttl, roles, value, scopes, subject, claims);
  }

  /** El {@code sub} del token; nulo lo omite. Cada usuario de prueba se distingue por el suyo. */
  TestToken withSubject(String value) {
    return new TestToken(issuer, audience, secret, ttl, roles, scopeClaim, scopes, value, claims);
  }

  /** Agrega un claim (por ejemplo oid, name o preferred_username). */
  TestToken withClaim(String name, Object value) {
    Map<String, Object> copy = new HashMap<>(claims);
    copy.put(name, value);
    return new TestToken(issuer, audience, secret, ttl, roles, scopeClaim, scopes, subject, Map.copyOf(copy));
  }

  String value() {
    Instant now = Instant.now();
    JwtClaimsSet.Builder builder = JwtClaimsSet.builder().issuer(issuer)
        .issuedAt(now.minus(Duration.ofMinutes(10)))
        .claim("roles", roles).claim(scopeClaim, scopes);
    if (subject != null) {
      builder.subject(subject);
    }
    claims.forEach(builder::claim);
    if (ttl != null) {
      builder.expiresAt(now.plus(ttl));
    }
    if (audience != null) {
      builder.audience(List.of(audience));
    }
    NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
        new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
    return encoder.encode(JwtEncoderParameters.from(
        JwsHeader.with(MacAlgorithm.HS256).build(), builder.build())).getTokenValue();
  }

  String bearer() {
    return "Bearer " + value();
  }
}
