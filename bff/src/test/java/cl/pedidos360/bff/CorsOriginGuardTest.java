package cl.pedidos360.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.mock.web.MockHttpServletRequest;

class CorsOriginGuardTest {
  private final SecurityConfiguration configuration = new SecurityConfiguration();

  @ParameterizedTest(name = "rejects origin \"{0}\"")
  @ValueSource(strings = {"*", "https://*.example.com", "", "  "})
  void corsOrigin_thatIsNotAnExplicitOrigin_failsAtStartup(String origin) {
    assertThatThrownBy(() -> configuration.corsConfigurationSource(origin, new StandardEnvironment()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("FRONTEND_ORIGIN");
  }

  @Test
  void corsOrigin_explicit_isAllowedOnApiRoutes() {
    CorsConfigurationSource source =
        configuration.corsConfigurationSource("https://app.example.com", new StandardEnvironment());

    CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/work-orders"));

    assertThat(cors).isNotNull();
    assertThat(cors.getAllowedOrigins()).containsExactly("https://app.example.com");
  }
}
