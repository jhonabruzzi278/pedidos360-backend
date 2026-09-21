package cl.pedidos360.audit;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/events")
public class AuditEventController {
  private final AuditEventRepository repository;
  public AuditEventController(AuditEventRepository repository) { this.repository = repository; }
  @GetMapping public List<AuditEvent> findAll() { return repository.findAll(); }
}
