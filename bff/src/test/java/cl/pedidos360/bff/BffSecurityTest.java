package cl.pedidos360.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "security.jwt.local-secret=" + TestToken.LOCAL_SECRET)
@ActiveProfiles("local")
class BffSecurityTest {
  private static final FakeDownstream DOWNSTREAM = FakeDownstream.start();
  private static final String ORDERS = "/api/work-orders";
  private static final String EVENTS = "/api/events";
  // Un pedido valido de verdad: el servicio real rechazaria uno sin items.
  private static final String NEW_ORDER = "{\"clientId\":\"CLI-9\",\"licensePlate\":\"ZZ99AA\","
      + "\"items\":[{\"concept\":\"MO-HH\",\"quantity\":1,\"unitPrice\":1000}]}";
  private static final String FRONTEND_ORIGIN = "http://localhost:4200";

  @Autowired WebApplicationContext context;
  MockMvc mvc;

  @DynamicPropertySource
  static void downstreamUrls(DynamicPropertyRegistry registry) {
    registry.add("services.orders-url", DOWNSTREAM::url);
    registry.add("services.audit-url", DOWNSTREAM::url);
    registry.add("services.read-timeout", () -> "1s");
  }

  @AfterAll
  static void stopDownstream() {
    DOWNSTREAM.close();
  }

  @BeforeEach
  void setUp() {
    DOWNSTREAM.reset();
    mvc =MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  // --- 401: el token no es aceptable ---

  @Test
  void getWorkOrders_withoutToken_returns401WithJsonError() throws Exception {
    mvc.perform(get(ORDERS))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().string(containsString("\"error\":\"unauthorized\"")));
  }

  @Test
  void getWorkOrders_withExpiredToken_returns401() throws Exception {
    expectUnauthorized(TestToken.viewer().withTtl(Duration.ofMinutes(-5)).bearer());
  }

  @Test
  void getWorkOrders_withWrongAudience_returns401() throws Exception {
    expectUnauthorized(TestToken.viewer().withAudience("api://otra-api").bearer());
  }

  @Test
  void getWorkOrders_withoutAudienceClaim_returns401() throws Exception {
    expectUnauthorized(TestToken.viewer().withoutAudience().bearer());
  }

  @Test
  void getWorkOrders_withWrongIssuer_returns401() throws Exception {
    expectUnauthorized(TestToken.viewer().withIssuer("https://emisor-falso.example").bearer());
  }

  @Test
  void getWorkOrders_withTamperedSignature_returns401() throws Exception {
    expectUnauthorized(TestToken.viewer().withSecret(TestToken.OTHER_SECRET).bearer());
  }

  @Test
  void getWorkOrders_withoutExpirationClaim_returns401() throws Exception {
    expectUnauthorized(TestToken.viewer().withoutExpiry().bearer());
  }

  @Test
  void getWorkOrders_withUnsignedAlgNoneToken_returns401() throws Exception {
    expectUnauthorized("Bearer " + unsignedToken());
  }

  @Test
  void getWorkOrders_withMalformedToken_returns401() throws Exception {
    expectUnauthorized("Bearer esto-no-es-un-jwt");
  }

  // --- 200: token valido ---

  @Test
  void getWorkOrders_withValidToken_returns200AndJson() throws Exception {
    mvc.perform(get(ORDERS).header(HttpHeaders.AUTHORIZATION, TestToken.viewer().bearer()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().string(containsString("OT-1")));
  }

  @Test
  void getEvents_withValidToken_returns200() throws Exception {
    mvc.perform(get(EVENTS).header(HttpHeaders.AUTHORIZATION, TestToken.viewer().bearer()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("OtCreada")));
  }

  @Test
  void getWorkOrders_withEntraStyleScpClaim_returns200() throws Exception {
    String token = TestToken.viewer().withScopeClaim("scp").bearer();
    mvc.perform(get(ORDERS).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
  }

  // --- 403: token valido pero sin permiso ---

  @Test
  void getEvents_withoutEventsScope_returns403WithJsonError() throws Exception {
    String token = TestToken.viewer().withScopes("orders.read").bearer();
    mvc.perform(get(EVENTS).header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isForbidden())
        .andExpect(content().string(containsString("\"error\":\"forbidden\"")));
  }

  @Test
  void getWorkOrders_withoutOrdersReadScope_returns403() throws Exception {
    String token = TestToken.viewer().withScopes("events.read").bearer();
    mvc.perform(get(ORDERS).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
  }

  @Test
  void postWorkOrders_withViewerRoleAndNoApprovedAccess_returns403AccessRequired() throws Exception {
    String token = TestToken.viewer().withScopes("orders.read orders.write").bearer();
    postOrder(token).andExpect(status().isForbidden())
        .andExpect(content().string(containsString("\"error\":\"access_required\"")));
    assertThat(DOWNSTREAM.createCalls()).isZero();
  }

  @Test
  void postWorkOrders_adminWithoutWriteScope_returns403() throws Exception {
    String token = TestToken.admin().withScopes("orders.read").bearer();
    postOrder(token).andExpect(status().isForbidden());
  }

  @Test
  void postWorkOrders_withoutToken_returns401() throws Exception {
    mvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON).content(NEW_ORDER))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unknownApiRoute_withValidToken_isDeniedByDefault() throws Exception {
    mvc.perform(get("/api/no-existe").header(HttpHeaders.AUTHORIZATION, TestToken.admin().bearer()))
        .andExpect(status().isForbidden());
  }

  // --- POST autorizado: el BFF reenvia al microservicio ---

  @Test
  void postWorkOrders_withAdminAndWriteScope_forwardsBodyAndReturns201() throws Exception {
    postOrder(TestToken.admin().bearer())
        .andExpect(status().isCreated())
        .andExpect(content().string(containsString("OT-NEW")));
    assertThat(DOWNSTREAM.lastCreateBody()).isEqualTo(NEW_ORDER);
    assertThat(DOWNSTREAM.lastCreateContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    // El admin no necesita aprobacion: no se consulta el servicio de accesos.
    assertThat(DOWNSTREAM.lookupCalls()).isZero();
  }

  // --- Errores del microservicio: no se convierten en 500 ---

  @Test
  void postWorkOrders_whenServiceRejectsInput_returnsOwnBodyNotTheServiceOne() throws Exception {
    DOWNSTREAM.setCreateResponse(400, "{\"trace\":\"secreto-interno\",\"path\":\"/internal/work-orders\"}");

    postOrder(TestToken.admin().bearer())
        .andExpect(status().isBadRequest())
        .andExpect(content().string(containsString("\"error\":\"bad_request\"")))
        .andExpect(content().string(not(containsString("secreto-interno"))))
        .andExpect(content().string(not(containsString("/internal"))));
  }

  @ParameterizedTest(name = "service answering {0} becomes 502")
  @ValueSource(ints = {401, 403, 405, 415, 429, 500, 503})
  void postWorkOrders_whenServiceAnswersOutsideTheAllowList_returns502WithoutLeakingDetails(int serviceStatus)
      throws Exception {
    DOWNSTREAM.setCreateResponse(serviceStatus, "{\"detail\":\"secreto-interno\"}");

    postOrder(TestToken.admin().bearer())
        .andExpect(status().isBadGateway())
        .andExpect(content().string(containsString("\"error\":\"bad_gateway\"")))
        .andExpect(content().string(not(containsString("secreto-interno"))));
  }

  @Test
  void getEvents_whenServiceFails_returns502WithoutLeakingDetails() throws Exception {
    DOWNSTREAM.setEventsStatus(500);   // el fake responde {"trace":"java.lang.NullPointerException"}
    mvc.perform(get(EVENTS).header(HttpHeaders.AUTHORIZATION, TestToken.viewer().bearer()))
        .andExpect(status().isBadGateway())
        .andExpect(content().string(containsString("\"error\":\"bad_gateway\"")))
        .andExpect(content().string(not(containsString("NullPointerException"))))
        .andExpect(content().string(not(containsString("trace"))));
  }

  @Test
  void getEvents_whenServiceHangs_returns503InsteadOfWaitingForever() throws Exception {
    DOWNSTREAM.setEventsDelay(Duration.ofSeconds(4));   // el timeout de lectura de la prueba es 1 s

    mvc.perform(get(EVENTS).header(HttpHeaders.AUTHORIZATION, TestToken.viewer().bearer()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().string(containsString("\"error\":\"service_unavailable\"")));
  }

  // --- Limites de entrada del proxy ---

  @Test
  void postWorkOrders_withBodyOverTheLimit_returns413AndNeverReachesTheService() throws Exception {
    String tooBig = "{\"note\":\"" + "x".repeat(70 * 1024) + "\"}";

    mvc.perform(post(ORDERS).header(HttpHeaders.AUTHORIZATION, TestToken.admin().bearer())
            .contentType(MediaType.APPLICATION_JSON).content(tooBig))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(content().string(containsString("\"error\":\"payload_too_large\"")));
    assertThat(DOWNSTREAM.createCalls()).isZero();
  }

  @Test
  void postWorkOrders_withEmptyBody_returns400AndNeverReachesTheService() throws Exception {
    mvc.perform(post(ORDERS).header(HttpHeaders.AUTHORIZATION, TestToken.admin().bearer())
            .contentType(MediaType.APPLICATION_JSON).content(""))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(containsString("\"error\":\"bad_request\"")));
    assertThat(DOWNSTREAM.createCalls()).isZero();
  }

  // --- Cabecera WWW-Authenticate: solo el esquema, sin el motivo del rechazo ni URLs internas ---

  @Test
  void unauthorized_withoutToken_advertisesOnlyTheBearerScheme() throws Exception {
    mvc.perform(get(ORDERS))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
  }

  @Test
  void unauthorized_withExpiredToken_doesNotRevealTheReasonOrInternalUrls() throws Exception {
    mvc.perform(get(ORDERS).header(HttpHeaders.AUTHORIZATION,
            TestToken.viewer().withTtl(Duration.ofMinutes(-5)).bearer()))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""));
  }

  @Test
  void forbidden_advertisesInsufficientScopeWithoutInternalUrls() throws Exception {
    String token = TestToken.viewer().withScopes("orders.read").bearer();
    mvc.perform(get(EVENTS).header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isForbidden())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\""));
  }

  // --- CORS ---

  @Test
  void preflight_fromFrontendOrigin_isAllowedWithoutToken() throws Exception {
    mvc.perform(options(ORDERS).header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN));
  }

  @Test
  void preflight_fromOtherOrigin_isRejected() throws Exception {
    mvc.perform(options(ORDERS).header(HttpHeaders.ORIGIN, "https://sitio-malicioso.example")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isForbidden());
  }

  // --- Endpoint de desarrollo (solo perfil local) ---

  @Test
  void devToken_defaultsToAViewerWhoStillNeedsTheAdminToApproveHisAccess() throws Exception {
    String token = issueDevToken("");
    mvc.perform(get(ORDERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk());
    // Trae el scope de escritura (como en Entra ID), asi que el rechazo es por falta de aprobacion, no de scope.
    postOrder("Bearer " + token).andExpect(status().isForbidden())
        .andExpect(content().string(containsString("access_required")));
    // Su identidad local es distinta de la del admin: aprobar a uno no abre la puerta al otro.
    DOWNSTREAM.setAccessStatus("local-viewer", "APPROVED");
    postOrder("Bearer " + token).andExpect(status().isCreated());
  }

  @Test
  void devToken_forAdminRole_canCreateWorkOrders() throws Exception {
    postOrder("Bearer " + issueDevToken("?role=admin")).andExpect(status().isCreated());
  }

  @Test
  void devToken_withUnknownRole_returns400() throws Exception {
    mvc.perform(post("/dev/token?role=superuser")).andExpect(status().isBadRequest());
  }

  private String issueDevToken(String query) throws Exception {
    String json = mvc.perform(post("/dev/token" + query)).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    String marker = "\"accessToken\":\"";
    int start = json.indexOf(marker) + marker.length();
    return json.substring(start, json.indexOf('"', start));
  }

  private ResultActions postOrder(String bearer) throws Exception {
    return mvc.perform(post(ORDERS).header(HttpHeaders.AUTHORIZATION, bearer)
        .contentType(MediaType.APPLICATION_JSON).content(NEW_ORDER));
  }

  private void expectUnauthorized(String bearer) throws Exception {
    mvc.perform(get(ORDERS).header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isUnauthorized())
        .andExpect(content().string(containsString("\"error\":\"unauthorized\"")));
  }

  private static String unsignedToken() {
    Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
    String header = base64.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    String payload = base64.encodeToString(("{\"iss\":\"pedidos360-local\",\"aud\":[\"api://pedidos360-local\"],"
        + "\"scope\":\"orders.read\",\"exp\":" + Instant.now().plusSeconds(300).getEpochSecond() + "}")
        .getBytes(StandardCharsets.UTF_8));
    return header + "." + payload + ".";
  }
}
