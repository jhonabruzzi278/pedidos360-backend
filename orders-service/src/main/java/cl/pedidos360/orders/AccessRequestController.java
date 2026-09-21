package cl.pedidos360.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Solicitudes de acceso para generar cotizaciones. Interno: el BFF autentica y decide quien puede llamar. */
@RestController
@RequestMapping("/internal/access-requests")
public class AccessRequestController {
  private final AccessRequestService service;

  AccessRequestController(AccessRequestService service) {
    this.service = service;
  }

  @GetMapping
  public List<AccessRequestResponse> list() {
    return service.list().stream().map(AccessRequestResponse::from).toList();
  }

  /** Estado de un usuario; "NONE" si nunca pidio acceso. El BFF lo consulta antes de dejarlo cotizar. */
  @GetMapping("/me")
  public AccessRequestResponse mine(@RequestParam String userId) {
    if (userId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId es obligatorio");
    return service.find(userId).map(AccessRequestResponse::from).orElseGet(AccessRequestResponse::none);
  }

  @PostMapping
  public AccessRequestResponse request(@Valid @RequestBody CreateAccessRequest body) {
    return AccessRequestResponse.from(service.request(body.userId(), body.userName(), body.userEmail()));
  }

  @PostMapping("/decision")
  public AccessRequestResponse decide(@Valid @RequestBody DecisionRequest body) {
    return AccessRequestResponse.from(service.decide(body.id(), body.decision().status(), body.decidedBy()));
  }

  public enum Decision {
    APPROVED(AccessStatus.APPROVED),
    REJECTED(AccessStatus.REJECTED);

    private final AccessStatus status;

    Decision(AccessStatus status) {
      this.status = status;
    }

    AccessStatus status() {
      return status;
    }
  }

  public record CreateAccessRequest(
      @NotBlank @Size(max = 64) String userId,
      @NotBlank @Size(max = 100) String userName,
      @Size(max = 150) String userEmail) {}

  public record DecisionRequest(
      @NotNull Long id,
      @NotNull Decision decision,
      @NotBlank @Size(max = 100) String decidedBy) {}

  public record AccessRequestResponse(Long id, String userId, String userName, String userEmail, String status,
      Instant requestedAt, Instant decidedAt, String decidedBy) {
    static AccessRequestResponse from(AccessRequest request) {
      return new AccessRequestResponse(request.getId(), request.getUserId(), request.getUserName(),
          request.getUserEmail(), request.getStatus().name(), request.getRequestedAt(), request.getDecidedAt(),
          request.getDecidedBy());
    }

    static AccessRequestResponse none() {
      return new AccessRequestResponse(null, null, null, null, "NONE", null, null, null);
    }
  }
}
