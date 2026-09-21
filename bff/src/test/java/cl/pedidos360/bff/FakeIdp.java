package cl.pedidos360.bff;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * IdP OIDC falso sobre HTTP real: publica discovery y JWKS con una clave RSA, como Entra ID.
 * Ademas guarda una segunda clave que NO publica, con el mismo kid, para simular un token falsificado.
 */
final class FakeIdp implements AutoCloseable {
  private static final String KEY_ID = "test-key-1";

  private final HttpServer server;
  private final RSAKey trustedKey = newKey();
  private final RSAKey untrustedKey = newKey();

  private FakeIdp(HttpServer server) {
    this.server = server;
  }

  static FakeIdp start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      FakeIdp idp = new FakeIdp(server);
      server.createContext("/.well-known/openid-configuration", idp::discovery);
      server.createContext("/keys", idp::keys);
      server.start();
      return idp;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  String issuer() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  /** Token firmado con la clave que el IdP publica. Los scopes viajan en "scp", como en Entra. */
  String token(String audience, Duration ttl, List<String> roles, String scopes) {
    return sign(trustedKey, audience, ttl, roles, scopes);
  }

  /** Mismo contenido y mismo kid, pero firmado con una clave que el IdP no publica. */
  String forgedToken(String audience, Duration ttl, List<String> roles, String scopes) {
    return sign(untrustedKey, audience, ttl, roles, scopes);
  }

  private String sign(RSAKey key, String audience, Duration ttl, List<String> roles, String scopes) {
    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer()).subject("idp-user")
        .audience(List.of(audience)).issuedAt(now.minus(Duration.ofMinutes(10))).expiresAt(now.plus(ttl))
        .claim("roles", roles).claim("scp", scopes).build();
    NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private void discovery(HttpExchange exchange) throws IOException {
    respond(exchange, "{\"issuer\":\"" + issuer() + "\",\"jwks_uri\":\"" + issuer() + "/keys\"}");
  }

  private void keys(HttpExchange exchange) throws IOException {
    respond(exchange, new JWKSet(trustedKey.toPublicJWK()).toString());
  }

  private static void respond(HttpExchange exchange, String json) throws IOException {
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  private static RSAKey newKey() {
    try {
      return new RSAKeyGenerator(2048).keyID(KEY_ID).keyUse(KeyUse.SIGNATURE)
          .algorithm(JWSAlgorithm.RS256).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
