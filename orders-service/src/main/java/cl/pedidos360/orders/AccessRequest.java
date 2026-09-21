package cl.pedidos360.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Solicitud de un usuario para generar cotizaciones. Hay una por usuario ({@code userId}, el id estable del
 * token): al rechazarla y volver a pedir, la misma fila vuelve a PENDING. El administrador la aprueba o rechaza.
 */
@Entity
@Table(name = "ACCESS_REQUEST", uniqueConstraints = @UniqueConstraint(columnNames = "USER_ID"))
public class AccessRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "REQUEST_ID")
  private Long id;
  @Column(name = "USER_ID", nullable = false, length = 64)
  private String userId;
  @Column(name = "USER_NAME", nullable = false, length = 100)
  private String userName;
  @Column(name = "USER_EMAIL", length = 150)
  private String userEmail;
  @Enumerated(EnumType.STRING)
  @Column(name = "STATUS", nullable = false, length = 12)
  private AccessStatus status;
  @Column(name = "REQUESTED_AT", nullable = false)
  private Instant requestedAt;
  @Column(name = "DECIDED_AT")
  private Instant decidedAt;
  @Column(name = "DECIDED_BY", length = 100)
  private String decidedBy;

  protected AccessRequest() {}

  public AccessRequest(String userId, String userName, String userEmail) {
    this.userId = userId;
    this.userName = userName;
    this.userEmail = userEmail;
    this.status = AccessStatus.PENDING;
    this.requestedAt = Instant.now();
  }

  /** Nueva solicitud sobre la misma fila (tras un rechazo): vuelve a pendiente y se olvida la decision anterior. */
  public void renew(String userName, String userEmail) {
    this.userName = userName;
    this.userEmail = userEmail;
    this.status = AccessStatus.PENDING;
    this.requestedAt = Instant.now();
    this.decidedAt = null;
    this.decidedBy = null;
  }

  public void decide(AccessStatus decision, String decidedBy) {
    this.status = decision;
    this.decidedAt = Instant.now();
    this.decidedBy = decidedBy;
  }

  public Long getId() { return id; }
  public String getUserId() { return userId; }
  public String getUserName() { return userName; }
  public String getUserEmail() { return userEmail; }
  public AccessStatus getStatus() { return status; }
  public Instant getRequestedAt() { return requestedAt; }
  public Instant getDecidedAt() { return decidedAt; }
  public String getDecidedBy() { return decidedBy; }
}
