package cl.pedidos360.orders;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Persistable;

/**
 * El id se asigna en la aplicacion, asi que sin {@link Persistable} Spring Data creeria que toda
 * orden con id es existente y haria merge: un id repetido sobrescribiria la orden en silencio en
 * vez de violar la clave primaria.
 */
@Entity
@Table(name = "OT")
public class WorkOrder implements Persistable<String> {
  @Id
  @Column(name = "OT_ID", length = 24)
  private String id;
  @Column(name = "CLIENTE_ID", nullable = false, length = 20)
  private String clientId;
  @Column(name = "PATENTE", nullable = false, length = 10)
  private String licensePlate;
  @Column(name = "DESCRIPCION", length = 200)
  private String description;
  @Column(name = "TOTAL", nullable = false)
  private long total;
  @Column(name = "CREATED_AT", nullable = false)
  private Instant createdAt;
  /** Quien emitio la cotizacion (nombre que el BFF toma del token). Nulo en las anteriores a este campo. */
  @Column(name = "CREATED_BY", length = 100)
  private String createdBy;
  @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
  private List<WorkOrderItem> items = new ArrayList<>();
  @Transient
  private boolean fresh = true;

  protected WorkOrder() {}

  public WorkOrder(String id, String clientId, String licensePlate, String description, long total) {
    this.id = id;
    this.clientId = clientId;
    this.licensePlate = licensePlate;
    this.description = description;
    this.total = total;
    this.createdAt = Instant.now();
  }

  public void addItem(String concept, double quantity, long unitPrice) {
    items.add(new WorkOrderItem(this, concept, quantity, unitPrice));
  }

  public void recordCreator(String name) {
    this.createdBy = name;
  }

  @Override
  public boolean isNew() { return fresh; }

  @PostPersist
  @PostLoad
  void markPersisted() { fresh = false; }

  @Override
  public String getId() { return id; }
  public String getClientId() { return clientId; }
  public String getLicensePlate() { return licensePlate; }
  public String getDescription() { return description; }
  public long getTotal() { return total; }
  public Instant getCreatedAt() { return createdAt; }
  public String getCreatedBy() { return createdBy; }
  public List<WorkOrderItem> getItems() { return List.copyOf(items); }
}
