package cl.pedidos360.bff;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Sobre un servidor real. MockMvc no realiza el "error dispatch" del contenedor, asi que no puede
 * detectar que los errores del framework (400, 406, 415) terminen enmascarados como 401/403.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "security.jwt.local-secret=" + TestToken.LOCAL_SECRET)
@ActiveProfiles("local")
class RealServerErrorHandlingTest {
  private static final FakeDownstream DOWNSTREAM = FakeDownstream.start();
  private static final String ORDERS = "/api/work-orders";

  @Value("${local.server.port}") int port;
  private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

  @DynamicPropertySource
  static void downstreamUrls(DynamicPropertyRegistry registry) {
    registry.add("services.orders-url", DOWNSTREAM::url);
    registry.add("services.audit-url", DOWNSTREAM::url);
  }

  @AfterAll
  static void stopDownstream() {
    DOWNSTREAM.close();
  }

  @Test
  void unsupportedContentType_returns415NotAnAuthorizationError() throws Exception {
    HttpRequest request = request(ORDERS).header("Authorization", TestToken.admin().bearer())
        .header("Content-Type", "text/plain").POST(BodyPublishers.ofString("hola")).build();

    assertThat(send(request).statusCode()).isEqualTo(415);
  }

  @Test
  void unacceptableMediaType_returns406NotAnAuthorizationError() throws Exception {
    HttpRequest request = request(ORDERS).header("Authorization", TestToken.viewer().bearer())
        .header("Accept", "text/xml").GET().build();

    assertThat(send(request).statusCode()).isEqualTo(406);
  }

  @Test
  void unknownDevRole_returns400NotUnauthorized() throws Exception {
    HttpRequest request = request("/dev/token?role=superuser").POST(BodyPublishers.noBody()).build();

    assertThat(send(request).statusCode()).isEqualTo(400);
  }

  @Test
  void authenticationAndAuthorizationFailuresKeepTheirOwnStatus() throws Exception {
    HttpRequest anonymous = request(ORDERS).GET().build();
    HttpRequest viewerWriting = request(ORDERS).header("Authorization", TestToken.viewer().withScopes(
            "orders.read orders.write").bearer())
        .header("Content-Type", "application/json").POST(BodyPublishers.ofString("{}")).build();

    assertThat(send(anonymous).statusCode()).isEqualTo(401);
    assertThat(send(viewerWriting).statusCode()).isEqualTo(403);
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
  }

  private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
    return http.send(request, BodyHandlers.ofString());
  }
}
