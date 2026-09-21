package cl.pedidos360.bff;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solicitudes de acceso para generar cotizaciones. Cualquier usuario autenticado consulta y pide la suya; solo el
 * rol admin lista todas y decide (lo exige {@link SecurityConfiguration}). La identidad sale siempre del token.
 */
@RestController
@RequestMapping(path = "/api/access-requests", produces = MediaType.APPLICATION_JSON_VALUE)
class AccessController {
  private final AccessDirectory access;

  AccessController(AccessDirectory access) {
    this.access = access;
  }

  @GetMapping("/me")
  ResponseEntity<String> mine(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(access.mine(CallerIdentity.from(jwt).id()));
  }

  @PostMapping
  ResponseEntity<String> request(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(access.request(CallerIdentity.from(jwt)));
  }

  @GetMapping
  ResponseEntity<String> list() {
    return ResponseEntity.ok(access.list());
  }

  @PostMapping(path = "/decision", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<String> decide(@Valid @RequestBody DecisionRequest body, @AuthenticationPrincipal Jwt jwt) {
    CallerIdentity admin = CallerIdentity.from(jwt);
    return ResponseEntity.ok(access.decide(body.id(), body.decision(), admin.name()));
  }

  record DecisionRequest(@NotNull Long id, @NotNull @Pattern(regexp = "APPROVED|REJECTED") String decision) {}
}
