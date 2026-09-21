package cl.pedidos360.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorkOrderControllerTest {
  @Test
  void mapsRepositoryEntitiesToResponses() {
    WorkOrderRepository repository = mock(WorkOrderRepository.class);
    WorkOrder order = new WorkOrder("OT-TEST", "CLI-TEST", "TEST01", "Prueba", 1000);
    order.addItem("ITEM", 2, 500);
    when(repository.findAll()).thenReturn(List.of(order));
    var result = new WorkOrderController(repository).findAll();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).calculatedSubtotal()).isEqualTo(1000);
    assertThat(result.get(0).itemCount()).isEqualTo(1);
  }
}
