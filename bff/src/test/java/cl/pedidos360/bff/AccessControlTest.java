package cl.pedidos360.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/**
 * Quien puede generar cotizaciones (admin, o usuario con acceso aprobado) y las rutas de solicitudes de acceso.
 * El acceso se decide con datos del microservicio y la identidad sale siempre del token, nunca del cliente.
 */
@SpringBootTest(properties = "security.jwt.local-secret=" + TestToken.LOCAL_SECRET)
@ActiveProfiles("local")
class AccessControlTest {
  private static final FakeDownstream DOWNSTREAM = FakeDownstream.start();
  private static final String ORDERS = "/api/work-orders";
  private static final String ACCESS = "/api/access-requests";
  private static final String QUOTE = "{\"clientId\":\"CLI-9\",\"licensePlate\":\"ZZ99AA\","
      + "\"items\":[{\"concept\":\"MO-HH\",\"quantity\":1,\"unitPrice\":1000}]}";
  private static final String WRITE_SCOPES = "orders.read orders.write events.read";

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
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private static TestToken user(String id) {
    return TestToken.viewer().withScopes(WRITE_SCOPES).withSubject(id);
  }

  // --- Generar cotizaciones: admin, o acceso aprobado ---

  @Test
  void quote_byAUserWhoNeverAskedForAccess_isRefusedWithAccessRequiredAndNeverReachesTheService() throws Exception {
    postQuote(user("ana").bearer())
        .andExpect(status().isForbidden())
        .andExpect(content().string(containsString("\"error\":\"access_required\"")))
        .andExpect(content().string(containsString("autorizacion del administrador")));
    assertThat(DOWNSTREAM.createCalls()).isZero();
  }

  @Test
  void quote_byAUserWithAPendingRequest_isRefused() throws Exception {
    DOWNSTREAM.setAccessStatus("ana", "PENDING");
    postQuote(user("ana").bearer()).andExpect(status().isForbidden())
        .andExpect(content().string(containsString("access_required")));
    assertThat(DOWNSTREAM.createCalls()).isZero();
  }

  @Test
  void quote_byAUserWhoseRequestWasRejected_isRefused() throws Exception {
    DOWNSTREAM.setAccessStatus("ana", "REJECTED");
    postQuote(user("ana").bearer()).andExpect(status().isForbidden())
        .andExpect(content().string(containsString("access_required")));
    assertThat(DOWNSTREAM.createCalls()).isZero();
  }

  @Test
  void quote_byAUserWithApprovedAccess_isForwardedAndCreated() throws Exception {
    DOWNSTREAM.setAccessStatus("ana", "APPROVED");

    postQuote(user("ana").bearer())
        .andExpect(status().isCreated())
        .andExpect(content().string(containsString("OT-NEW")));

    assertThat(DOWNSTREAM.lastCreateBody()).isEqualTo(QUOTE);
    assertThat(DOWNSTREAM.createCalls()).isEqualTo(1);
  }

  @Test
  void quote_byAnAdmin_needsNoApprovalAndSkipsTheAccessLookup() throws Exception {
    postQuote(TestToken.admin().bearer()).andExpect(status().isCreated());

    assertThat(DOWNSTREAM.lookupCalls()).isZero();
    assertThat(DOWNSTREAM.createCalls()).isEqualTo(1);
  }

  @Test
  void access_isPerUser_soApprovingOneDoesNotOpenTheDoorForAnother() throws Exception {
    DOWNSTREAM.setAccessStatus("ana", "APPROVED");

    postQuote(user("ana").bearer()).andExpect(status().isCreated());
    postQuote(user("beto").bearer()).andExpect(status().isForbidden());
  }

  @Test
  void theLookup_usesTheIdentityInTheToken_notAnythingTheClientSends() throws Exception {
    DOWNSTREAM.setAccessStatus("aprobado", "APPROVED");

    mvc.perform(post(ORDERS + "?userId=aprobado").header(HttpHeaders.AUTHORIZATION, user("intruso").bearer())
            .header("X-Caller-Name", "aprobado").header("X-User-Id", "aprobado")
            .contentType(MediaType.APPLICATION_JSON).content(QUOTE))
        .andExpect(status().isForbidden())
        .andExpect(content().string(containsString("access_required")));

    assertThat(DOWNSTREAM.lastLookupUserId()).isEqualTo("intruso");
  }

  @Test
  void theIdentity_prefersTheOidClaimOverTheSubject() throws Exception {
    DOWNSTREAM.setAccessStatus("oid-de-ana", "APPROVED");

    postQuote(user("sub-de-ana").withClaim("oid", "oid-de-ana").bearer()).andExpect(status().isCreated());

    assertThat(DOWNSTREAM.lastLookupUserId()).isEqualTo("oid-de-ana");
  }

  @Test
  void quote_forwardsTheNameOfWhoIssuedItEncodedForTheHeader() throws Exception {
    DOWNSTREAM.setAccessStatus("ana", "APPROVED");

    postQuote(user("ana").withClaim("name", "María Pérez").bearer()).andExpect(status().isCreated());

    assertThat(DOWNSTREAM.lastCreateCallerName()).isEqualTo("Mar%C3%ADa+P%C3%A9rez");
  }

  @Test
  void quote_whenTheNameIsMissing_fallsBackToTheUsernameAndThenTheId() throws Exception {
    postQuote(TestToken.admin().withClaim("preferred_username", "admin@taller.cl").bearer())
        .andExpect(status().isCreated());
    assertThat(DOWNSTREAM.lastCreateCallerName()).isEqualTo("admin%40taller.cl");

    postQuote(TestToken.admin().withSubject("sin-nombre").bearer()).andExpect(status().isCreated());
    assertThat(DOWNSTREAM.lastCreateCallerName()).isEqualTo("sin-nombre");
  }

  @Test
  void quote_whenTheAccessServiceFails_returns502WithoutLeakingDetails() throws Exception {
    DOWNSTREAM.setAccessLookupStatus(500);

    postQuote(user("ana").bearer())
        .andExpect(status().isBadGateway())
        .andExpect(content().string(not(containsString("IllegalStateException"))));
    assertThat(DOWNSTREAM.createCalls()).isZero();
  }

  @Test
  void quote_withoutTheWriteScope_isForbiddenByTheSecurityChainNotByTheAccessCheck() throws Exception {
    DOWNSTREAM.setAccessStatus("ana", "APPROVED");

    postQuote(TestToken.viewer().withSubject("ana").bearer())
        .andExpect(status().isForbidden())
        .andExpect(content().string(containsString("\"error\":\"forbidden\"")));
    assertThat(DOWNSTREAM.lookupCalls()).isZero();
  }

  @Test
  void quote_withATokenThatDoesNotIdentifyTheUser_returns401() throws Exception {
    postQuote(user(null).bearer()).andExpect(status().isUnauthorized())
        .andExpect(content().string(containsString("\"error\":\"unauthorized\"")));
    assertThat(DOWNSTREAM.createCalls()).isZero();
  }

  // --- Mi solicitud ---

  @Test
  void myAccess_returnsTheStatusOfTheCaller() throws Exception {
    DOWNSTREAM.setAccessStatus("ana", "PENDING");

    mvc.perform(get(ACCESS + "/me").header(HttpHeaders.AUTHORIZATION, user("ana").bearer()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().string(containsString("PENDING")));
    assertThat(DOWNSTREAM.lastLookupUserId()).isEqualTo("ana");
  }

  @Test
  void myAccess_ignoresAnyUserIdSentByTheClient() throws Exception {
    DOWNSTREAM.setAccessStatus("otra", "APPROVED");

    mvc.perform(get(ACCESS + "/me?userId=otra").header(HttpHeaders.AUTHORIZATION, user("ana").bearer()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("NONE")));
    assertThat(DOWNSTREAM.lastLookupUserId()).isEqualTo("ana");
  }

  @Test
  void myAccess_needsAValidTokenWithTheReadScope() throws Exception {
    mvc.perform(get(ACCESS + "/me")).andExpect(status().isUnauthorized());
    mvc.perform(get(ACCESS + "/me").header(HttpHeaders.AUTHORIZATION,
            TestToken.viewer().withScopes("events.read").bearer()))
        .andExpect(status().isForbidden());
  }

  // --- Pedir acceso ---

  @Test
  void requestAccess_sendsTheIdentityOfTheTokenToTheService() throws Exception {
    String token = user("ana").withClaim("name", "Ana Pérez").withClaim("preferred_username", "ana@taller.cl").bearer();

    mvc.perform(post(ACCESS).header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("PENDING")));

    assertThat(DOWNSTREAM.lastAccessRequestBody())
        .contains("\"userId\":\"ana\"").contains("\"userName\":\"Ana Pérez\"").contains("\"userEmail\":\"ana@taller.cl\"");
  }

  @Test
  void requestAccess_ignoresWhateverBodyTheClientSends() throws Exception {
    mvc.perform(post(ACCESS).header(HttpHeaders.AUTHORIZATION, user("ana").bearer())
            .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"admin\",\"userName\":\"Otro\"}"))
        .andExpect(status().isOk());

    assertThat(DOWNSTREAM.lastAccessRequestBody()).contains("\"userId\":\"ana\"").doesNotContain("Otro");
  }

  @Test
  void requestAccess_needsAValidTokenWithTheWriteScope() throws Exception {
    mvc.perform(post(ACCESS)).andExpect(status().isUnauthorized());
    mvc.perform(post(ACCESS).header(HttpHeaders.AUTHORIZATION, TestToken.viewer().bearer()))
        .andExpect(status().isForbidden());
  }

  // --- Solo el administrador ve y decide ---

  @Test
  void listingRequests_isForAdminsOnly() throws Exception {
    mvc.perform(get(ACCESS)).andExpect(status().isUnauthorized());
    mvc.perform(get(ACCESS).header(HttpHeaders.AUTHORIZATION, user("ana").bearer()))
        .andExpect(status().isForbidden());
    mvc.perform(get(ACCESS).header(HttpHeaders.AUTHORIZATION, TestToken.admin().withScopes("events.read").bearer()))
        .andExpect(status().isForbidden());
    mvc.perform(get(ACCESS).header(HttpHeaders.AUTHORIZATION, TestToken.admin().bearer()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("PENDING")));
  }

  @Test
  void deciding_isForAdminsOnly() throws Exception {
    decide(user("ana").bearer(), "{\"id\":7,\"decision\":\"APPROVED\"}").andExpect(status().isForbidden());
    decide(TestToken.admin().withScopes("orders.read").bearer(), "{\"id\":7,\"decision\":\"APPROVED\"}")
        .andExpect(status().isForbidden());
    assertThat(DOWNSTREAM.lastDecisionBody()).isNull();
  }

  @Test
  void deciding_withoutToken_returns401() throws Exception {
    mvc.perform(post(ACCESS + "/decision").contentType(MediaType.APPLICATION_JSON)
        .content("{\"id\":7,\"decision\":\"APPROVED\"}")).andExpect(status().isUnauthorized());
  }

  @Test
  void deciding_forwardsTheDecisionSignedWithTheNameOfTheAdmin() throws Exception {
    String admin = TestToken.admin().withClaim("name", "Admin Taller").bearer();

    decide(admin, "{\"id\":7,\"decision\":\"APPROVED\"}")
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("APPROVED")));

    assertThat(DOWNSTREAM.lastDecisionBody())
        .contains("\"id\":7").contains("\"decision\":\"APPROVED\"").contains("\"decidedBy\":\"Admin Taller\"");
  }

  @Test
  void deciding_neverTrustsAWhoDecidedFieldFromTheClient() throws Exception {
    decide(TestToken.admin().withClaim("name", "Admin Taller").bearer(),
        "{\"id\":7,\"decision\":\"REJECTED\",\"decidedBy\":\"Suplantado\"}").andExpect(status().isOk());

    assertThat(DOWNSTREAM.lastDecisionBody()).contains("Admin Taller").doesNotContain("Suplantado");
  }

  @Test
  void deciding_withAnInvalidBody_returns400WithTheOwnErrorBody() throws Exception {
    for (String body : List.of("{\"id\":7,\"decision\":\"PENDING\"}", "{\"decision\":\"APPROVED\"}",
        "{\"id\":7}", "{\"id\":\"siete\",\"decision\":\"APPROVED\"}", "no es json")) {
      decide(TestToken.admin().bearer(), body)
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString("\"error\":\"bad_request\"")));
    }
    assertThat(DOWNSTREAM.lastDecisionBody()).isNull();
  }

  @Test
  void otherAccessRoutes_areDeniedByDefault() throws Exception {
    String admin = TestToken.admin().bearer();
    mvc.perform(get(ACCESS + "/otra").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isForbidden());
    mvc.perform(get(ACCESS + "/decision").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isForbidden());
  }

  private ResultActions postQuote(String bearer) throws Exception {
    return mvc.perform(post(ORDERS).header(HttpHeaders.AUTHORIZATION, bearer)
        .contentType(MediaType.APPLICATION_JSON).content(QUOTE));
  }

  private ResultActions decide(String bearer, String body) throws Exception {
    return mvc.perform(post(ACCESS + "/decision").header(HttpHeaders.AUTHORIZATION, bearer)
        .contentType(MediaType.APPLICATION_JSON).content(body));
  }
}
