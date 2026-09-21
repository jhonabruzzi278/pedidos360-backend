package cl.pedidos360.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class AccessSeedDataTest {
  @Test
  void seedRequests_leaveTwoPendingAndOneApprovedForTheAdministratorToSee() {
    List<AccessRequest> requests = AccessSeedData.workshopRequests();

    assertThat(requests).extracting(AccessRequest::getStatus)
        .containsExactly(AccessStatus.PENDING, AccessStatus.PENDING, AccessStatus.APPROVED);
    assertThat(requests.get(2).getDecidedBy()).isEqualTo("Administrador");
    assertThat(requests).extracting(AccessRequest::getUserId).doesNotHaveDuplicates();
  }

  @Test
  void everyValueFitsItsColumn() {
    for (AccessRequest request : AccessSeedData.workshopRequests()) {
      assertThat(request.getUserId()).hasSizeLessThanOrEqualTo(64);
      assertThat(request.getUserName()).hasSizeLessThanOrEqualTo(100);
      assertThat(request.getUserEmail()).hasSizeLessThanOrEqualTo(150);
    }
  }

  @Test
  void seedsAnEmptyTable() throws Exception {
    AccessRequestRepository repository = mock(AccessRequestRepository.class);
    when(repository.count()).thenReturn(0L);

    new AccessSeedData().seedAccessRequests(repository).run();

    verify(repository).saveAll(anyIterable());
  }

  @Test
  void leavesAnExistingTableUntouched() throws Exception {
    AccessRequestRepository repository = mock(AccessRequestRepository.class);
    when(repository.count()).thenReturn(5L);

    new AccessSeedData().seedAccessRequests(repository).run();

    verify(repository, never()).saveAll(anyIterable());
  }
}
