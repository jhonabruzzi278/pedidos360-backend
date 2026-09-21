package cl.pedidos360.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Entrada de POST /internal/work-orders. Los limites reflejan las columnas de OT/OT_ITEM
 * (CANTIDAD NUMBER(10,2), PRECIO_UNIT y TOTAL enteros) y acotan el total maximo
 * (50 items x 1000 x 10.000.000 = 5e11) por debajo de NUMBER(12,0). Se usa BigDecimal para rechazar,
 * en vez de truncar en silencio, una cantidad con mas de 2 decimales o un precio no entero.
 * El total no se recibe: se calcula aqui a partir de los items.
 */
public record CreateWorkOrderRequest(
    @NotBlank @Size(max = 20) String clientId,
    @NotBlank @Size(max = 10) String licensePlate,
    @Size(max = 200) String description,
    @NotEmpty @Size(max = 50) List<@NotNull @Valid Item> items) {

  public record Item(
      @NotBlank @Size(max = 40) String concept,
      @NotNull @DecimalMin("0.01") @DecimalMax("1000") @Digits(integer = 4, fraction = 2) BigDecimal quantity,
      @NotNull @PositiveOrZero @Max(10_000_000) @Digits(integer = 8, fraction = 0) BigDecimal unitPrice) {

    /** Misma regla que WorkOrderItem.getSubtotal y que la columna SUBTOTAL de Oracle. */
    long subtotal() {
      return quantity.multiply(unitPrice).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
  }

  long total() {
    return items.stream().mapToLong(Item::subtotal).sum();
  }

  WorkOrder toEntity(String id) {
    WorkOrder order = new WorkOrder(id, clientId, licensePlate, description, total());
    items.forEach(item -> order.addItem(item.concept(), item.quantity().doubleValue(),
        item.unitPrice().longValueExact()));
    return order;
  }
}
