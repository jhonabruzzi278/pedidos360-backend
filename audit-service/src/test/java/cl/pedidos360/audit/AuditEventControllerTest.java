package cl.pedidos360.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class AuditEventControllerTest {
  @Test
  void returnsRepositoryEvents() {
    AuditEventRepository repository = mock(AuditEventRepository.class);
    when(repository.findAll()).thenReturn(List.of(new AuditEvent("OT-TEST", "OtCreada", "{}")));
    var result = new AuditEventController(repository).findAll();
    assertThat(result).singleElement().extracting(AuditEvent::getEventType).isEqualTo("OtCreada");
  }
}
