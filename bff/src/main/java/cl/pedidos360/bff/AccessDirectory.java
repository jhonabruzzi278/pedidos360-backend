package cl.pedidos360.bff;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Solicitudes de acceso para generar cotizaciones, que guarda orders-service. El BFF decide quien puede llamar y
 * de quien es cada solicitud (siempre sale del token, nunca de lo que envia el cliente); el servicio solo persiste.
 */
@Component
class AccessDirectory {
  private static final String BASE = "/internal/access-requests";

  private final RestClient orders;

  AccessDirectory(@Qualifier("ordersClient") RestClient orders) {
    this.orders = orders;
  }

  boolean hasApprovedAccess(String userId) {
    AccessSummary summary = orders.get().uri(BASE + "/me?userId={userId}", userId).retrieve().body(AccessSummary.class);
    return summary != null && "APPROVED".equals(summary.status());
  }

  String mine(String userId) {
    return orders.get().uri(BASE + "/me?userId={userId}", userId).retrieve().body(String.class);
  }

  String list() {
    return orders.get().uri(BASE).retrieve().body(String.class);
  }

  String request(CallerIdentity caller) {
    return orders.post().uri(BASE).contentType(MediaType.APPLICATION_JSON)
        .body(new AccessPayload(caller.id(), caller.name(), caller.email())).retrieve().body(String.class);
  }

  String decide(Long id, String decision, String decidedBy) {
    return orders.post().uri(BASE + "/decision").contentType(MediaType.APPLICATION_JSON)
        .body(new DecisionPayload(id, decision, decidedBy)).retrieve().body(String.class);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record AccessSummary(String status) {}

  record AccessPayload(String userId, String userName, String userEmail) {}

  record DecisionPayload(Long id, String decision, String decidedBy) {}
}
