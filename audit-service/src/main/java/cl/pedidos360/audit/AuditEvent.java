package cl.pedidos360.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "OT_EVENT")
public class AuditEvent {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "EVENT_ID") private Long id;
  @Column(name = "OT_ID", length = 24) private String workOrderId;
  @Column(name = "EVENT_TYPE", nullable = false, length = 40) private String eventType;
  @Column(name = "PAYLOAD_JSON", nullable = false, length = 4000) private String payloadJson;
  @Column(name = "CREATED_AT", nullable = false) private Instant createdAt;

  protected AuditEvent() {}
  public AuditEvent(String workOrderId, String eventType, String payloadJson) {
    this.workOrderId = workOrderId; this.eventType = eventType; this.payloadJson = payloadJson; this.createdAt = Instant.now();
  }
  public Long getId() { return id; }
  public String getWorkOrderId() { return workOrderId; }
  public String getEventType() { return eventType; }
  public String getPayloadJson() { return payloadJson; }
  public Instant getCreatedAt() { return createdAt; }
}
