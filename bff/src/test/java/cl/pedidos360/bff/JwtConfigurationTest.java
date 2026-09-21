package cl.pedidos360.bff;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "security.jwt.local-secret=" + TestToken.LOCAL_SECRET)
@ActiveProfiles("local")
class JwtConfigurationTest {
  @Autowired JwtEncoder encoder;
  @Autowired JwtDecoder decoder;

  @Test
  void acceptsTokenWithExpectedIssuerAndAudience() {
    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder().issuer("pedidos360-local").subject("test")
        .audience(List.of("api://pedidos360-local")).issuedAt(now).expiresAt(now.plus(5, ChronoUnit.MINUTES)).build();
    String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    assertThat(decoder.decode(token).getSubject()).isEqualTo("test");
  }
}
