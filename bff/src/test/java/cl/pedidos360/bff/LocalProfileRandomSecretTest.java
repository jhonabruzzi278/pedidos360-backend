package cl.pedidos360.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Perfil local SIN secreto configurado: el BFF genera uno aleatorio al arrancar. Asi ningun secreto
 * conocido (ni el que antes venia en el repositorio) permite emitir tokens aunque el perfil se active
 * por error.
 */
@SpringBootTest
@ActiveProfiles("local")
class LocalProfileRandomSecretTest {
  private static final FakeDownstream DOWNSTREAM = FakeDownstream.start();

  @Autowired WebApplicationContext context;
  MockMvc mvc;

  @DynamicPropertySource
  static void downstreamUrls(DynamicPropertyRegistry registry) {
    registry.add("services.orders-url", DOWNSTREAM::url);
    registry.add("services.audit-url", DOWNSTREAM::url);
  }

  @AfterAll
  static void stopDownstream() {
    DOWNSTREAM.close();
  }

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void devTokenWorksWithoutAnyConfiguredSecret() throws Exception {
    String json = mvc.perform(post("/dev/token")).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    String marker = "\"accessToken\":\"";
    int start = json.indexOf(marker) + marker.length();
    String token = json.substring(start, json.indexOf('"', start));

    mvc.perform(get("/api/work-orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void tokenSignedWithTheFormerRepositoryDefaultSecretIsRejected() throws Exception {
    assertThat(TestToken.LOCAL_SECRET).isEqualTo("local-only-secret-must-have-at-least-32-bytes");

    mvc.perform(get("/api/work-orders").header(HttpHeaders.AUTHORIZATION, TestToken.admin().bearer()))
        .andExpect(status().isUnauthorized());
  }
}
