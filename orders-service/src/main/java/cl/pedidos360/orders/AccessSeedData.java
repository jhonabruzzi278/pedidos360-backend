package cl.pedidos360.orders;

import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Solicitudes de ejemplo para que el administrador tenga algo que revisar desde el primer dia: dos pendientes y
 * una ya aprobada. Solo se cargan si la tabla esta vacia. Los ids no corresponden a usuarios reales del tenant.
 */
@Configuration
class AccessSeedData {
  @Bean
  CommandLineRunner seedAccessRequests(AccessRequestRepository repository) {
    return args -> {
      if (repository.count() > 0) return;
      repository.saveAll(workshopRequests());
    };
  }

  static List<AccessRequest> workshopRequests() {
    AccessRequest approved = new AccessRequest("demo-valentina-soto", "Valentina Soto", "valentina.soto@ejemplo.cl");
    approved.decide(AccessStatus.APPROVED, "Administrador");
    return List.of(
        new AccessRequest("demo-camila-rojas", "Camila Rojas", "camila.rojas@ejemplo.cl"),
        new AccessRequest("demo-diego-fuentes", "Diego Fuentes", "diego.fuentes@ejemplo.cl"),
        approved);
  }
}
