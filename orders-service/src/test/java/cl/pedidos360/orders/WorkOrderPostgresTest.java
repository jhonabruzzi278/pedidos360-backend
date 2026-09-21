package cl.pedidos360.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Year;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Arranca el servicio con el perfil "rds" (el de la nube) contra un PostgreSQL real, la misma familia que RDS.
 * Comprueba que application-rds.yml conecta, que Hibernate crea el esquema y que las entidades se guardan y
 * se leen bien (tildes incluidas). Sin Docker la prueba se omite.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("rds")
class WorkOrderPostgresTest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @DynamicPropertySource
  static void connection(DynamicPropertyRegistry registry) {
    registry.add("DB_URL", POSTGRES::getJdbcUrl);
    registry.add("DB_USERNAME", POSTGRES::getUsername);
    registry.add("DB_PASSWORD", POSTGRES::getPassword);
  }

  @Autowired WorkOrderRepository repository;
  @Autowired AccessRequestRepository accessRequests;
  @Autowired JdbcTemplate jdbc;

  @Test
  void usesPostgreSqlAndNotTheInMemoryDatabase() {
    assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL 16");
  }

  @Test
  void seedsTheWorkshopOrdersOnceAndTheirItems() {
    List<WorkOrder> orders = repository.findAll();

    assertThat(orders).extracting(WorkOrder::getId).contains("OT-" + Year.now().getValue() + "-000101");
    assertThat(orders.stream().filter(order -> order.getId().endsWith("-000101")).findFirst().orElseThrow().getItems())
        .hasSize(4);
  }

  @Test
  void savesAnOrderWithItsItemsAndReadsItBackWithAccents() {
    WorkOrder order = new WorkOrder("OT-PG-TEST-1", "CLI-9001", "PGTE57", "Revisión de frenos y batería", 81_900);
    order.addItem("Pastillas de freno delanteras", 1, 48_900);
    order.addItem("Mano de obra frenos (horas)", 1.5, 22_000);
    repository.saveAndFlush(order);

    WorkOrder stored = repository.findById("OT-PG-TEST-1").orElseThrow();
    assertThat(stored.getDescription()).isEqualTo("Revisión de frenos y batería");
    assertThat(stored.getItems()).extracting(WorkOrderItem::getConcept)
        .containsExactlyInAnyOrder("Pastillas de freno delanteras", "Mano de obra frenos (horas)");
    assertThat(stored.getItems().stream().mapToLong(WorkOrderItem::getSubtotal).sum()).isEqualTo(81_900);
    assertThat(stored.getItems()).allSatisfy(item -> assertThat(item.getId()).isNotNull());
  }

  @Test
  void createsTheTablesThatTheCloudSchemaNeeds() {
    List<String> tables = jdbc.queryForList(
        "select lower(table_name) from information_schema.tables where table_schema = 'public'", String.class);

    assertThat(tables).contains("ot", "ot_item", "access_request");
  }

  @Test
  void storesWhoIssuedTheQuote() {
    WorkOrder order = new WorkOrder("OT-PG-TEST-2", "CLI-9002", "PGTE58", "Cotización con autor", 1_000);
    order.addItem("Revisión", 1, 1_000);
    order.recordCreator("Camila Rojas");
    repository.saveAndFlush(order);

    assertThat(repository.findById("OT-PG-TEST-2").orElseThrow().getCreatedBy()).isEqualTo("Camila Rojas");
    assertThat(jdbc.queryForObject(
        "select count(*) from information_schema.columns where table_name = 'ot' and column_name = 'created_by'",
        Integer.class)).isEqualTo(1);
  }

  @Test
  void seedsTheAccessRequestsForTheAdministratorToReview() {
    assertThat(accessRequests.findByUserId("demo-camila-rojas")).get()
        .extracting(AccessRequest::getStatus).isEqualTo(AccessStatus.PENDING);
    assertThat(accessRequests.findByUserId("demo-valentina-soto")).get()
        .extracting(AccessRequest::getStatus).isEqualTo(AccessStatus.APPROVED);
  }

  @Test
  void allowsOneAccessRequestPerUserOnly() {
    accessRequests.saveAndFlush(new AccessRequest("pg-user-1", "Ana Pérez", "ana@ejemplo.cl"));

    assertThatThrownBy(() -> accessRequests.saveAndFlush(new AccessRequest("pg-user-1", "Ana otra vez", null)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(accessRequests.findByUserId("pg-user-1")).get()
        .extracting(AccessRequest::getUserName).isEqualTo("Ana Pérez");
  }
}
