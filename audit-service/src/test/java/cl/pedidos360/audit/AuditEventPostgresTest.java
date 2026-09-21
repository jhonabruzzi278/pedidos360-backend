package cl.pedidos360.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Arranca el servicio con el perfil "rds" (el de la nube) contra un PostgreSQL real, la misma familia que RDS.
 * Sin Docker la prueba se omite.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("rds")
class AuditEventPostgresTest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @DynamicPropertySource
  static void connection(DynamicPropertyRegistry registry) {
    registry.add("DB_URL", POSTGRES::getJdbcUrl);
    registry.add("DB_USERNAME", POSTGRES::getUsername);
    registry.add("DB_PASSWORD", POSTGRES::getPassword);
  }

  @Autowired AuditEventRepository repository;
  @Autowired JdbcTemplate jdbc;

  @Test
  void usesPostgreSqlAndNotTheInMemoryDatabase() {
    assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL 16");
  }

  @Test
  void seedsTheHistoryOfTheWorkshopOrders() {
    assertThat(repository.findAll()).extracting(AuditEvent::getEventType)
        .contains("OtCreada", "OtFinalizada");
  }

  @Test
  void savesAnEventAndReadsItBackWithItsGeneratedId() {
    AuditEvent saved = repository.saveAndFlush(
        new AuditEvent("OT-PG-TEST-1", "OtCreada", "{\"eventType\":\"OtCreada\",\"total\":81900}"));

    AuditEvent stored = repository.findById(saved.getId()).orElseThrow();
    assertThat(stored.getWorkOrderId()).isEqualTo("OT-PG-TEST-1");
    assertThat(stored.getPayloadJson()).contains("81900");
    assertThat(stored.getCreatedAt()).isNotNull();
  }

  @Test
  void createsTheTableThatTheCloudSchemaNeeds() {
    List<String> tables = jdbc.queryForList(
        "select lower(table_name) from information_schema.tables where table_schema = 'public'", String.class);

    assertThat(tables).contains("ot_event");
  }
}
