package cl.pedidos360.audit;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AuditSeedData {
  @Bean CommandLineRunner seedAudit(AuditEventRepository repository) {
    return args -> {
      if (repository.count() == 0) {
        repository.save(new AuditEvent("OT-LOCAL-000001", "OtCreada", "{\"eventType\":\"OtCreada\",\"total\":62000}"));
      }
    };
  }
}
