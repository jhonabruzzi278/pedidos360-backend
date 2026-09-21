package cl.pedidos360.orders;

/** Estado de la solicitud de un usuario para generar cotizaciones. Sin solicitud se informa "NONE" (no se guarda). */
public enum AccessStatus {
  PENDING,
  APPROVED,
  REJECTED
}
