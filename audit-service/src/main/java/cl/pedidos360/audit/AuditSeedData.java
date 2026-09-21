package cl.pedidos360.audit;

import java.time.Year;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Historial de las ordenes de ejemplo del servicio de ordenes (mismos ids OT-AAAA-NNNNNN y mismos totales:
 * si se cambia OrdersSeedData hay que actualizar esta lista). Solo se carga si la tabla esta vacia.
 */
@Configuration
class AuditSeedData {
  private record Seed(int number, String eventType, long total) {}

  @Bean CommandLineRunner seedAudit(AuditEventRepository repository) {
    return args -> {
      if (repository.count() == 0) repository.saveAll(workshopEvents(Year.now().getValue()));
    };
  }

  static List<AuditEvent> workshopEvents(int year) {
    return List.of(
        new Seed(101, "OtCreada", 91_400),
        new Seed(102, "OtCreada", 111_900),
        new Seed(103, "OtCreada", 243_500),
        new Seed(104, "OtCreada", 51_000),
        new Seed(104, "OtFinalizada", 51_000),
        new Seed(105, "OtCreada", 90_900),
        new Seed(106, "OtCreada", 35_300)).stream()
        .map(seed -> {
          String orderId = "OT-%d-%06d".formatted(year, seed.number());
          String payload = "{\"eventType\":\"%s\",\"total\":%d}".formatted(seed.eventType(), seed.total());
          return new AuditEvent(orderId, seed.eventType(), payload);
        })
        .toList();
  }
}
