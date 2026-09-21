package cl.pedidos360.bff;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Profile("local")
@RestController
@RequestMapping("/dev")
class LocalTokenController {
  private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
  private static final Map<String, String> SCOPES_BY_ROLE = Map.of(
      "viewer", "orders.read events.read",
      "admin", "orders.read orders.write events.read");

  private final JwtEncoder encoder;
  private final String issuer;
  private final String audience;

  LocalTokenController(JwtEncoder encoder, @Value("${security.jwt.issuer}") String issuer,
      @Value("${security.jwt.audience}") String audience) {
    this.encoder = encoder;
    this.issuer = issuer;
    this.audience = audience;
  }

  @PostMapping("/token")
  Map<String, Object> token(@RequestParam(defaultValue = "viewer") String role) {
    String scopes = SCOPES_BY_ROLE.get(role);
    if (scopes == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol no soportado");
    }
    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject("local-demo-user")
        .audience(List.of(audience)).issuedAt(now).expiresAt(now.plus(TOKEN_TTL))
        .claim("roles", List.of(role)).claim("scope", scopes).build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return Map.of("accessToken", value, "tokenType", "Bearer", "expiresIn", TOKEN_TTL.toSeconds());
  }
}
