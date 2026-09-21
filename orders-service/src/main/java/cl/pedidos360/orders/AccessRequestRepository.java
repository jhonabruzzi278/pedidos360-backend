package cl.pedidos360.orders;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {
  Optional<AccessRequest> findByUserId(String userId);
}
