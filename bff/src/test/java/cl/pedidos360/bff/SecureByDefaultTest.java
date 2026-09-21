package cl.pedidos360.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Sin perfil explicito el BFF debe comportarse como en la nube: sin emisor de tokens local, sin
 * /dev/token, y verificando firmas RS256 contra las claves publicas del emisor (aqui, un IdP falso).
 */
// "test" fija un perfil no-local aunque el shell tenga SPRING_PROFILES_ACTIVE=local (uso normal en desarrollo).
@SpringBootTest
@ActiveProfiles("test")
class SecureByDefaultTest {
  private static final FakeIdp IDP = FakeIdp.start();
  private static final FakeDownstream DOWNSTREAM = FakeDownstream.start();
  private static final String AUDIENCE = "api://pedidos360-test";
  private static final Duration VALID_FOR = Duration.ofMinutes(5);

  @Autowired WebApplicationContext context;
  @Autowired Environment environment;
  MockMvc mvc;

  @DynamicPropertySource
  static void idpAndDownstream(DynamicPropertyRegistry registry) {
    registry.add("security.jwt.issuer", IDP::issuer);
    registry.add("security.jwt.audience", () -> AUDIENCE);
    registry.add("services.orders-url", DOWNSTREAM::url);
    registry.add("services.audit-url", DOWNSTREAM::url);
  }

  @AfterAll
  static void stopServers() {
    IDP.close();
    DOWNSTREAM.close();
  }

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void localIsNotADefaultProfile() {
    assertThat(environment.getDefaultProfiles()).doesNotContain("local");
    assertThat(Arrays.asList(environment.getActiveProfiles())).doesNotContain("local");
  }

  @Test
  void localTokenIssuerBeansAreNotPresent() {
    assertThat(context.containsBean("localTokenController")).isFalse();
    assertThat(context.getBeanNamesForType(JwtEncoder.class)).isEmpty();
  }

  @Test
  void devTokenEndpointIsNotAvailable() throws Exception {
    mvc.perform(post("/dev/token"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().string(not(containsString("accessToken"))));
  }

  @Test
  void tokenSignedByTheIdp_isAccepted() throws Exception {
    String token = IDP.token(AUDIENCE, VALID_FOR, List.of("viewer"), "orders.read");
    mvc.perform(get("/api/work-orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void rolesAndScopesFromIdpToken_authorizeTheAdminRoute() throws Exception {
    String token = IDP.token(AUDIENCE, VALID_FOR, List.of("admin"), "orders.read orders.write");
    mvc.perform(post("/api/work-orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"clientId\":\"CLI-9\",\"licensePlate\":\"ZZ99AA\","
                + "\"items\":[{\"concept\":\"MO-HH\",\"quantity\":1,\"unitPrice\":1000}]}"))
        .andExpect(status().isCreated());
  }

  @Test
  void tokenWithValidSignatureButWrongAudience_isRejected() throws Exception {
    expectUnauthorized(IDP.token("api://otra-api", VALID_FOR, List.of("viewer"), "orders.read"));
  }

  @Test
  void expiredTokenFromTheIdp_isRejected() throws Exception {
    expectUnauthorized(IDP.token(AUDIENCE, Duration.ofMinutes(-5), List.of("viewer"), "orders.read"));
  }

  @Test
  void tokenSignedWithAnUntrustedKey_isRejected() throws Exception {
    expectUnauthorized(IDP.forgedToken(AUDIENCE, VALID_FOR, List.of("admin"), "orders.read orders.write"));
  }

  @Test
  void tokenSignedWithTheLocalSecret_isRejected() throws Exception {
    String forged = TestToken.admin().withIssuer(IDP.issuer()).withAudience(AUDIENCE).value();
    expectUnauthorized(forged);
  }

  private void expectUnauthorized(String token) throws Exception {
    mvc.perform(get("/api/work-orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }
}
