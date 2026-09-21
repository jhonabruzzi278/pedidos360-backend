package cl.pedidos360.orders;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OrdersSeedData {
  @Bean
  CommandLineRunner seedOrders(WorkOrderRepository repository) {
    return args -> {
      if (repository.count() > 0) return;
      WorkOrder first = new WorkOrder("OT-LOCAL-000001", "CLI-001", "XXYY11", "Mantencion 10k", 62000);
      first.addItem("MO-HH", 2, 25000);
      first.addItem("FILTRO-ACEITE", 1, 12000);
      WorkOrder second = new WorkOrder("OT-LOCAL-000002", "CLI-002", "BBCC22", "Cambio pastillas freno", 80000);
      second.addItem("PASTILLA-FRENO-DEL", 1, 55000);
      second.addItem("MO-HH", 1, 25000);
      repository.save(first);
      repository.save(second);
    };
  }
}
