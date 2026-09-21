package cl.pedidos360.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class AuditSeedDataTest {
  @Test
  void eventsPointToTheSeedOrdersOfTheOrdersService() {
    List<AuditEvent> events = AuditSeedData.workshopEvents(2026);

    assertThat(events).extracting(AuditEvent::getWorkOrderId).containsExactly(
        "OT-2026-000101", "OT-2026-000102", "OT-2026-000103", "OT-2026-000104", "OT-2026-000104",
        "OT-2026-000105", "OT-2026-000106");
    assertThat(events).extracting(AuditEvent::getEventType).containsExactly(
        "OtCreada", "OtCreada", "OtCreada", "OtCreada", "OtFinalizada", "OtCreada", "OtCreada");
  }

  @Test
  void payloadsCarryTheSameTotalsAsTheSeedOrders() {
    // Los mismos totales estan en OrdersSeedDataTest: los dos servicios deben contar la misma historia.
    List<AuditEvent> created = AuditSeedData.workshopEvents(2026).stream()
        .filter(event -> event.getEventType().equals("OtCreada")).toList();

    assertThat(created).extracting(AuditEvent::getPayloadJson).containsExactly(
        "{\"eventType\":\"OtCreada\",\"total\":91400}",
        "{\"eventType\":\"OtCreada\",\"total\":111900}",
        "{\"eventType\":\"OtCreada\",\"total\":243500}",
        "{\"eventType\":\"OtCreada\",\"total\":51000}",
        "{\"eventType\":\"OtCreada\",\"total\":90900}",
        "{\"eventType\":\"OtCreada\",\"total\":35300}");
  }

  @Test
  void noEventIsMarkedAsLocal() {
    AuditSeedData.workshopEvents(2026).forEach(event -> assertThat(event.getWorkOrderId()).doesNotContain("LOCAL"));
  }

  @Test
  void seedsAnEmptyTable() throws Exception {
    AuditEventRepository repository = mock(AuditEventRepository.class);
    when(repository.count()).thenReturn(0L);

    new AuditSeedData().seedAudit(repository).run();

    verify(repository).saveAll(anyIterable());
  }

  @Test
  void leavesAnExistingTableUntouched() throws Exception {
    AuditEventRepository repository = mock(AuditEventRepository.class);
    when(repository.count()).thenReturn(1L);

    new AuditSeedData().seedAudit(repository).run();

    verify(repository, never()).saveAll(anyIterable());
  }
}
