package cl.pedidos360.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "OT_ITEM")
public class WorkOrderItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ITEM_ID")
  private Long id;
  @ManyToOne(optional = false)
  @JoinColumn(name = "OT_ID", nullable = false)
  private WorkOrder workOrder;
  @Column(name = "CONCEPTO", nullable = false, length = 40)
  private String concept;
  @Column(name = "CANTIDAD", nullable = false)
  private double quantity;
  @Column(name = "PRECIO_UNIT", nullable = false)
  private long unitPrice;

  protected WorkOrderItem() {}

  WorkOrderItem(WorkOrder workOrder, String concept, double quantity, long unitPrice) {
    this.workOrder = workOrder;
    this.concept = concept;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
  }

  public Long getId() { return id; }
  public String getConcept() { return concept; }
  public double getQuantity() { return quantity; }
  public long getUnitPrice() { return unitPrice; }
  /** Igual que la columna virtual SUBTOTAL de Oracle: ROUND(CANTIDAD * PRECIO_UNIT), medio hacia arriba. */
  public long getSubtotal() {
    return BigDecimal.valueOf(quantity).multiply(BigDecimal.valueOf(unitPrice))
        .setScale(0, RoundingMode.HALF_UP).longValueExact();
  }
}
