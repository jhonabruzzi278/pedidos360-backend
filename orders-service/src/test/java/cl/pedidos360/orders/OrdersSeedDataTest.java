package cl.pedidos360.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class OrdersSeedDataTest {
  @Test
  void workshopOrdersUseTheDatabaseIdFormatAndHaveRealisticTotals() {
    List<WorkOrder> orders = OrdersSeedData.workshopOrders(2026);

    assertThat(orders).extracting(WorkOrder::getId).containsExactly(
        "OT-2026-000101", "OT-2026-000102", "OT-2026-000103", "OT-2026-000104", "OT-2026-000105", "OT-2026-000106");
    // Los mismos totales estan en AuditSeedDataTest: los dos servicios deben contar la misma historia.
    assertThat(orders).extracting(WorkOrder::getTotal)
        .containsExactly(91_400L, 111_900L, 243_500L, 51_000L, 90_900L, 35_300L);
  }

  @Test
  void theTotalOfEachOrderIsTheSumOfItsItems() {
    for (WorkOrder order : OrdersSeedData.workshopOrders(2026)) {
      long sum = order.getItems().stream().mapToLong(WorkOrderItem::getSubtotal).sum();
      assertThat(order.getTotal()).as(order.getId()).isEqualTo(sum);
      assertThat(order.getItems()).as(order.getId()).isNotEmpty();
    }
  }

  @Test
  void everyValueFitsItsColumnAndNoneIsMarkedAsLocal() {
    for (WorkOrder order : OrdersSeedData.workshopOrders(2026)) {
      assertThat(order.getId()).hasSizeLessThanOrEqualTo(24).doesNotContain("LOCAL");
      assertThat(order.getClientId()).hasSizeLessThanOrEqualTo(20);
      assertThat(order.getLicensePlate()).hasSizeLessThanOrEqualTo(10);
      assertThat(order.getDescription()).hasSizeLessThanOrEqualTo(200);
      order.getItems().forEach(item -> assertThat(item.getConcept()).hasSizeLessThanOrEqualTo(40));
    }
  }

  @Test
  void seedsAnEmptyTable() throws Exception {
    WorkOrderRepository repository = mock(WorkOrderRepository.class);
    when(repository.count()).thenReturn(0L);

    new OrdersSeedData().seedOrders(repository).run();

    verify(repository).saveAll(anyIterable());
  }

  @Test
  void leavesAnExistingTableUntouched() throws Exception {
    WorkOrderRepository repository = mock(WorkOrderRepository.class);
    when(repository.count()).thenReturn(3L);

    new OrdersSeedData().seedOrders(repository).run();

    verify(repository, never()).saveAll(anyIterable());
  }
}
