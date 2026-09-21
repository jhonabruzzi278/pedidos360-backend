package cl.pedidos360.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ordenes de un taller mecanico para que la aplicacion arranque con datos creibles. Solo se cargan si la
 * tabla esta vacia. Los ids siguen el formato de la secuencia de la base (OT-AAAA-NNNNNN); las ordenes
 * nuevas usan el sufijo hexadecimal de {@link WorkOrderIds}, asi que no chocan con estas.
 */
@Configuration
class OrdersSeedData {
  private static final long LABOR_HOUR = 22_000;

  private record Line(String concept, double quantity, long unitPrice) {
    /** Misma regla que WorkOrderItem.getSubtotal: el total de la orden es la suma de sus subtotales. */
    long subtotal() {
      return BigDecimal.valueOf(quantity).multiply(BigDecimal.valueOf(unitPrice))
          .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
  }

  @Bean
  CommandLineRunner seedOrders(WorkOrderRepository repository) {
    return args -> {
      if (repository.count() > 0) return;
      repository.saveAll(workshopOrders(Year.now().getValue()));
    };
  }

  static List<WorkOrder> workshopOrders(int year) {
    return List.of(
        order(year, 101, "CLI-0142", "KLXP42", "Mantención de 30.000 km",
            new Line("Aceite motor 5W-30 sintético (litros)", 4, 9_500),
            new Line("Filtro de aceite", 1, 7_900),
            new Line("Filtro de aire", 1, 12_500),
            new Line("Mano de obra mantención (horas)", 1.5, LABOR_HOUR)),
        order(year, 102, "CLI-0087", "BRTC63", "Ruido al frenar: cambio de pastillas delanteras",
            new Line("Pastillas de freno delanteras", 1, 48_900),
            new Line("Rectificado de discos", 2, 15_000),
            new Line("Mano de obra frenos (horas)", 1.5, LABOR_HOUR)),
        order(year, 103, "CLI-0215", "FGHJ21", "Cambio de correa de distribución y bomba de agua",
            new Line("Kit correa de distribución", 1, 96_000),
            new Line("Bomba de agua", 1, 38_500),
            new Line("Refrigerante (litros)", 5, 4_200),
            new Line("Mano de obra distribución (horas)", 4, LABOR_HOUR)),
        order(year, 104, "CLI-0033", "PL4589", "Revisión antes de viajar y alineación",
            new Line("Alineación y balanceo", 1, 28_000),
            new Line("Revisión de 20 puntos", 1, 15_000),
            new Line("Rotación de neumáticos", 1, 8_000)),
        order(year, 105, "CLI-0199", "WKZT08", "Batería descargada, el auto no enciende",
            new Line("Batería 12V 60Ah", 1, 74_900),
            new Line("Diagnóstico eléctrico (horas)", 0.5, LABOR_HOUR),
            new Line("Instalación de batería", 1, 5_000)),
        order(year, 106, "CLI-0142", "KLXP42", "Preparación para revisión técnica",
            new Line("Ampolletas H7 (par)", 1, 9_800),
            new Line("Escobillas limpiaparabrisas", 1, 14_500),
            new Line("Mano de obra revisión (horas)", 0.5, LABOR_HOUR)));
  }

  private static WorkOrder order(int year, int number, String clientId, String licensePlate,
      String description, Line... lines) {
    long total = 0;
    for (Line line : lines) total += line.subtotal();
    WorkOrder order = new WorkOrder("OT-%d-%06d".formatted(year, number), clientId, licensePlate, description, total);
    for (Line line : lines) order.addItem(line.concept(), line.quantity(), line.unitPrice());
    return order;
  }
}
