package cl.pedidos360.orders;

import java.time.Year;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Genera ids OT-AAAA-XXXXXXXX (8 hex) que caben en OT_ID VARCHAR2(24). El formato propio de la base
 * (trigger BIU_OT: OT-AAAA-NNNNNN con SEQ_OT) solo se usa cuando OT_ID llega nulo, cosa que esta
 * entidad no hace. La comprobacion de existencia es una cortesia: la garantia real de unicidad es
 * la clave primaria (ver Persistable en WorkOrder).
 */
final class WorkOrderIds {
  private WorkOrderIds() {}

  static String next(Predicate<String> alreadyExists) {
    String id;
    do {
      String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
      id = "OT-" + Year.now().getValue() + "-" + suffix;
    } while (alreadyExists.test(id));
    return id;
  }
}
