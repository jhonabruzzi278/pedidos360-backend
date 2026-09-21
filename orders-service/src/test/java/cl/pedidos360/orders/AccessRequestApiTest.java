package cl.pedidos360.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Contrato HTTP de las solicitudes de acceso sobre H2: cada prueba usa su propio usuario. */
@SpringBootTest
class AccessRequestApiTest {
  private static final String URL = "/internal/access-requests";
  private static final AtomicInteger USERS = new AtomicInteger();

  @Autowired WebApplicationContext context;
  MockMvc mvc;
  String userId;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).build();
    userId = "user-" + USERS.incrementAndGet();
  }

  @Test
  void aUserWhoNeverAsked_hasNoAccess() throws Exception {
    mvc.perform(get(URL + "/me").param("userId", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("NONE")))
        .andExpect(jsonPath("$.id", nullValue()));
  }

  @Test
  void asking_createsAPendingRequestThatShowsInTheList() throws Exception {
    ResultActions created = ask(userId, "Camila Rojas", "camila@ejemplo.cl")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("PENDING")))
        .andExpect(jsonPath("$.userName", is("Camila Rojas")))
        .andExpect(jsonPath("$.requestedAt", notNullValue()))
        .andExpect(jsonPath("$.decidedAt", nullValue()));
    Number id = idOf(created);

    mvc.perform(get(URL)).andExpect(jsonPath("$[?(@.id == " + id + ")].userId", is(List.of(userId))));
    mvc.perform(get(URL + "/me").param("userId", userId)).andExpect(jsonPath("$.status", is("PENDING")));
  }

  @Test
  void askingTwiceWhilePending_keepsASingleRequest() throws Exception {
    Number first = idOf(ask(userId, "Diego", "diego@ejemplo.cl"));
    Number second = idOf(ask(userId, "Diego", "diego@ejemplo.cl"));

    assertThat(second).isEqualTo(first);
    String list = mvc.perform(get(URL)).andReturn().getResponse().getContentAsString();
    List<String> owners = JsonPath.read(list, "$[?(@.userId == '" + userId + "')].userId");
    assertThat(owners).hasSize(1);
  }

  @Test
  void approving_recordsTheDecisionAndWhoTookIt() throws Exception {
    Number id = idOf(ask(userId, "Valeria", "valeria@ejemplo.cl"));

    decide(id, "APPROVED", "Admin Taller")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("APPROVED")))
        .andExpect(jsonPath("$.decidedBy", is("Admin Taller")))
        .andExpect(jsonPath("$.decidedAt", notNullValue()));

    mvc.perform(get(URL + "/me").param("userId", userId)).andExpect(jsonPath("$.status", is("APPROVED")));
  }

  @Test
  void askingAgainAfterBeingApproved_doesNotResetTheAccess() throws Exception {
    Number id = idOf(ask(userId, "Pablo", "pablo@ejemplo.cl"));
    decide(id, "APPROVED", "Admin Taller");

    ask(userId, "Pablo", "pablo@ejemplo.cl").andExpect(jsonPath("$.status", is("APPROVED")));
  }

  @Test
  void askingAgainAfterBeingRejected_goesBackToPendingAndForgetsTheOldDecision() throws Exception {
    Number id = idOf(ask(userId, "Sofia", "sofia@ejemplo.cl"));
    decide(id, "REJECTED", "Admin Taller").andExpect(jsonPath("$.status", is("REJECTED")));

    ask(userId, "Sofia Vidal", "sofia.vidal@ejemplo.cl")
        .andExpect(jsonPath("$.id", is(id.intValue())))
        .andExpect(jsonPath("$.status", is("PENDING")))
        .andExpect(jsonPath("$.userName", is("Sofia Vidal")))
        .andExpect(jsonPath("$.decidedAt", nullValue()))
        .andExpect(jsonPath("$.decidedBy", nullValue()));
  }

  @Test
  void rejectingAnApprovedUser_revokesTheAccess() throws Exception {
    Number id = idOf(ask(userId, "Marta", "marta@ejemplo.cl"));
    decide(id, "APPROVED", "Admin Taller");

    decide(id, "REJECTED", "Admin Taller");

    mvc.perform(get(URL + "/me").param("userId", userId)).andExpect(jsonPath("$.status", is("REJECTED")));
  }

  @Test
  void deciding_onAnUnknownRequest_returns404() throws Exception {
    decide(987_654_321L, "APPROVED", "Admin Taller").andExpect(status().isNotFound());
  }

  @Test
  void deciding_withAnInvalidDecision_returns400() throws Exception {
    Number id = idOf(ask(userId, "Ines", "ines@ejemplo.cl"));

    decide(id, "PENDING", "Admin Taller").andExpect(status().isBadRequest());
    decide(id, "TALVEZ", "Admin Taller").andExpect(status().isBadRequest());
  }

  @Test
  void deciding_withoutWhoDecides_returns400() throws Exception {
    Number id = idOf(ask(userId, "Rosa", "rosa@ejemplo.cl"));

    mvc.perform(post(URL + "/decision").contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":" + id + ",\"decision\":\"APPROVED\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void asking_withoutUserIdOrName_returns400() throws Exception {
    mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"userName\":\"Sin id\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + userId + "\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"" + "x".repeat(65) + "\",\"userName\":\"Largo\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void checkingAUserWithABlankId_returns400() throws Exception {
    mvc.perform(get(URL + "/me").param("userId", "  ")).andExpect(status().isBadRequest());
  }

  @Test
  void theList_showsPendingRequestsBeforeTheDecidedOnes() throws Exception {
    Number decided = idOf(ask(userId, "Decidida", "decidida@ejemplo.cl"));
    decide(decided, "APPROVED", "Admin Taller");
    ask(userId + "-b", "Pendiente", "pendiente@ejemplo.cl");

    String list = mvc.perform(get(URL)).andReturn().getResponse().getContentAsString();
    List<String> statuses = JsonPath.read(list, "$[*].status");

    int firstDecided = -1;
    for (int i = 0; i < statuses.size(); i++) {
      if (!statuses.get(i).equals("PENDING")) {
        firstDecided = i;
        break;
      }
    }
    assertThat(firstDecided).isPositive();
    assertThat(statuses.subList(firstDecided, statuses.size())).doesNotContain("PENDING");
  }

  @Test
  void theSeedRequests_areThereForTheAdministratorToReview() throws Exception {
    mvc.perform(get(URL))
        .andExpect(content().string(containsString("demo-camila-rojas")))
        .andExpect(content().string(containsString("demo-diego-fuentes")))
        .andExpect(content().string(containsString("demo-valentina-soto")));
  }

  private ResultActions ask(String id, String name, String email) throws Exception {
    return mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":\"" + id + "\",\"userName\":\"" + name + "\",\"userEmail\":\"" + email + "\"}"));
  }

  private ResultActions decide(Number id, String decision, String by) throws Exception {
    return mvc.perform(post(URL + "/decision").contentType(MediaType.APPLICATION_JSON)
        .content("{\"id\":" + id + ",\"decision\":\"" + decision + "\",\"decidedBy\":\"" + by + "\"}"));
  }

  private Number idOf(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
  }
}
