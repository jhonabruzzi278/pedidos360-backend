package cl.pedidos360.orders;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Reglas de las solicitudes de acceso: una por usuario, idempotente, y el administrador decide. */
@Service
class AccessRequestService {
  private static final Comparator<AccessRequest> PENDING_FIRST_THEN_NEWEST = Comparator
      .comparing((AccessRequest request) -> request.getStatus() != AccessStatus.PENDING)
      .thenComparing(AccessRequest::getRequestedAt, Comparator.reverseOrder());

  private final AccessRequestRepository repository;

  AccessRequestService(AccessRequestRepository repository) {
    this.repository = repository;
  }

  List<AccessRequest> list() {
    return repository.findAll().stream().sorted(PENDING_FIRST_THEN_NEWEST).toList();
  }

  Optional<AccessRequest> find(String userId) {
    return repository.findByUserId(userId);
  }

  /**
   * Pedir acceso es idempotente: si ya esta pendiente o aprobada no cambia nada, y una rechazada vuelve a
   * pendiente. Sin @Transactional a proposito: si dos peticiones del mismo usuario chocan en la restriccion de
   * unicidad, se recupera la fila que gano en vez de dejar la transaccion marcada para revertir.
   */
  AccessRequest request(String userId, String userName, String userEmail) {
    Optional<AccessRequest> existing = repository.findByUserId(userId);
    if (existing.isPresent()) {
      AccessRequest current = existing.get();
      if (current.getStatus() != AccessStatus.REJECTED) return current;
      current.renew(userName, userEmail);
      return repository.save(current);
    }
    try {
      return repository.saveAndFlush(new AccessRequest(userId, userName, userEmail));
    } catch (DataIntegrityViolationException concurrentRequest) {
      return repository.findByUserId(userId).orElseThrow(() -> concurrentRequest);
    }
  }

  @Transactional
  AccessRequest decide(Long id, AccessStatus decision, String decidedBy) {
    AccessRequest request = repository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud inexistente"));
    request.decide(decision, decidedBy);
    return repository.save(request);
  }
}
