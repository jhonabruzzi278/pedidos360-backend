package cl.pedidos360.bff;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Clientes hacia los microservicios internos, con timeouts para que un servicio colgado no agote los hilos. */
@Configuration
class DownstreamConfiguration {
  @Bean RestClient ordersClient(@Value("${services.orders-url}") String url,
      @Value("${services.connect-timeout:2s}") Duration connectTimeout,
      @Value("${services.read-timeout:10s}") Duration readTimeout) {
    return client(url, connectTimeout, readTimeout);
  }

  @Bean RestClient auditClient(@Value("${services.audit-url}") String url,
      @Value("${services.connect-timeout:2s}") Duration connectTimeout,
      @Value("${services.read-timeout:10s}") Duration readTimeout) {
    return client(url, connectTimeout, readTimeout);
  }

  private static RestClient client(String url, Duration connectTimeout, Duration readTimeout) {
    HttpClient httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1).connectTimeout(connectTimeout).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(readTimeout);
    return RestClient.builder().baseUrl(url).requestFactory(requestFactory).build();
  }
}
