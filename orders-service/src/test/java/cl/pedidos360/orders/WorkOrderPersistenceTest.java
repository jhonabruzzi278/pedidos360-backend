package cl.pedidos360.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Contexto completo con H2: la orden y sus items se guardan de verdad y vuelven a leerse. */
@SpringBootTest
class WorkOrderPersistenceTest {
  @Autowired WebApplicationContext context;
  @Autowired WorkOrderRepository repository;
  MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void savingAnExistingId_failsInsteadOfSilentlyOverwritingTheOrder() {
    repository.saveAndFlush(new WorkOrder("OT-DUP-TEST", "CLI-ORIGINAL", "AA11BB", "original", 100));

    assertThatThrownBy(() -> repository.saveAndFlush(
        new WorkOrder("OT-DUP-TEST", "CLI-OVERWRITE", "ZZ99ZZ", "sobrescrita", 5)))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(repository.findById("OT-DUP-TEST").orElseThrow().getClientId()).isEqualTo("CLI-ORIGINAL");
  }

  @Test
  void createdWorkOrder_isPersistedWithItsItemsAndListed() throws Exception {
    long before = repository.count();

    mvc.perform(post("/internal/work-orders").contentType(MediaType.APPLICATION_JSON).content("""
            {"clientId":"CLI-77","licensePlate":"QQ77RR","description":"Prueba de persistencia",
             "items":[{"concept":"MO-HH","quantity":1.5,"unitPrice":20000},
                      {"concept":"ACEITE","quantity":4,"unitPrice":8000}]}"""))
        .andExpect(status().isCreated());

    assertThat(repository.count()).isEqualTo(before + 1);
    WorkOrder stored = repository.findAll().stream()
        .filter(order -> "QQ77RR".equals(order.getLicensePlate())).findFirst().orElseThrow();
    assertThat(stored.getItems()).hasSize(2);
    assertThat(stored.getTotal()).isEqualTo(62000);

    mvc.perform(get("/internal/work-orders"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(stored.getId())));
  }
}
