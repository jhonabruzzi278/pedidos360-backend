package cl.pedidos360.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.AdditionalAnswers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorkOrderCreationTest {
  private static final String URL = "/internal/work-orders";
  // "total" viene del cliente y debe ignorarse: el total lo calcula el servidor desde los items.
  private static final String VALID = """
      {"clientId":"CLI-9","licensePlate":"ZZ99AA","description":"Revision","total":1,
       "items":[{"concept":"MO-HH","quantity":2,"unitPrice":25000},
                {"concept":"FILTRO","quantity":1,"unitPrice":12000}]}""";

  private final WorkOrderRepository repository = mock(WorkOrderRepository.class);
  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkOrderController(repository)).build();

  @Test
  void create_withValidBody_returns201AndComputesTotalOnServer() throws Exception {
    when(repository.save(any(WorkOrder.class))).then(AdditionalAnswers.returnsFirstArg());

    send(VALID)
        .andExpect(status().isCreated())
        .andExpect(content().string(containsString("\"total\":62000")))
        .andExpect(content().string(containsString("\"itemCount\":2")));

    ArgumentCaptor<WorkOrder> saved = ArgumentCaptor.forClass(WorkOrder.class);
    verify(repository).save(saved.capture());
    assertThat(saved.getValue().getTotal()).isEqualTo(62000);
    assertThat(saved.getValue().getId()).matches("OT-\\d{4}-[0-9A-F]{8}");
  }

  @ParameterizedTest(name = "{0} x {1} = {2}")
  @CsvSource({"1.15, 10, 12", "0.35, 10, 4", "2.5, 3, 8", "0.01, 49, 0", "0.01, 50, 1", "999.99, 9999999, 9999899000"})
  void create_roundsHalfUpAndTotalMatchesTheCalculatedSubtotal(String quantity, String unitPrice, long expected)
      throws Exception {
    when(repository.save(any(WorkOrder.class))).then(AdditionalAnswers.returnsFirstArg());

    send(order("CLI-9", "ZZ99AA", "redondeo", item("MO-HH", quantity, unitPrice)))
        .andExpect(status().isCreated())
        .andExpect(content().string(containsString("\"total\":" + expected)))
        .andExpect(content().string(containsString("\"calculatedSubtotal\":" + expected)));
  }

  @Test
  void create_whenGeneratedIdIsTaken_triesAnotherOne() throws Exception {
    when(repository.existsById(anyString())).thenReturn(true, false);
    when(repository.save(any(WorkOrder.class))).then(AdditionalAnswers.returnsFirstArg());

    send(VALID).andExpect(status().isCreated());

    verify(repository, times(2)).existsById(anyString());
  }

  @ParameterizedTest(name = "rejects: {0}")
  @ValueSource(strings = {
      "{\"licensePlate\":\"ZZ99AA\",\"items\":[{\"concept\":\"A\",\"quantity\":1,\"unitPrice\":1}]}",
      "{\"clientId\":\"CLI-9\",\"licensePlate\":\" \",\"items\":[{\"concept\":\"A\",\"quantity\":1,\"unitPrice\":1}]}",
      "{\"clientId\":\"CLI-9\",\"licensePlate\":\"ABCDEFGHIJK\",\"items\":[{\"concept\":\"A\",\"quantity\":1,\"unitPrice\":1}]}",
      "{\"clientId\":\"CLI-9\",\"licensePlate\":\"ZZ99AA\"}",
      "{\"clientId\":\"CLI-9\",\"licensePlate\":\"ZZ99AA\",\"items\":[]}",
      "{\"clientId\":\"CLI-9\",\"licensePlate\":\"ZZ99AA\",\"items\":[{\"concept\":\"\",\"quantity\":1,\"unitPrice\":1}]}",
      "{\"clientId\":\"CLI-9\",\"licensePlate\":\"ZZ99AA\",\"items\":[{\"concept\":\"A\",\"quantity\":0,\"unitPrice\":1}]}",
      "{\"clientId\":\"CLI-9\",\"licensePlate\":\"ZZ99AA\",\"items\":[{\"concept\":\"A\",\"quantity\":1001,\"unitPrice\":1}]}",
      "{\"clientId\":\"CLI-9\",\"licensePlate\":\"ZZ99AA\",\"items\":[{\"concept\":\"A\",\"quantity\":1,\"unitPrice\":-5}]}",
      "{\"clientId\":\"CLI-9\",\"licensePlate\":\"ZZ99AA\",\"items\":[{\"concept\":\"A\",\"quantity\":1,\"unitPrice\":10000001}]}",
      "{\"clientId\":\"CLI-9\",\"licensePlate\":\"ZZ99AA\",\"items\":[null]}",
      // Decimales que la columna CANTIDAD NUMBER(10,2) no puede guardar, y precios no enteros.
      "{\"clientId\":\"CLI-9\",\"licensePlate\":\"ZZ99AA\",\"items\":[{\"concept\":\"A\",\"quantity\":0.333,\"unitPrice\":10}]}",
      "{\"clientId\":\"CLI-9\",\"licensePlate\":\"ZZ99AA\",\"items\":[{\"concept\":\"A\",\"quantity\":0.004,\"unitPrice\":10}]}",
      "{\"clientId\":\"CLI-9\",\"licensePlate\":\"ZZ99AA\",\"items\":[{\"concept\":\"A\",\"quantity\":1E-300,\"unitPrice\":10}]}",
      "{\"clientId\":\"CLI-9\",\"licensePlate\":\"ZZ99AA\",\"items\":[{\"concept\":\"A\",\"quantity\":1,\"unitPrice\":25000.5}]}",
      "{ esto no es json"})
  void create_withInvalidBody_returns400AndSavesNothing(String body) throws Exception {
    send(body).andExpect(status().isBadRequest());

    verify(repository, never()).save(any());
  }

  @Test
  void create_exactlyAtEveryLimit_returns201AndTheTotalStaysBelowNumber12() throws Exception {
    when(repository.save(any(WorkOrder.class))).then(AdditionalAnswers.returnsFirstArg());
    String items = String.join(",", Collections.nCopies(50, item("C".repeat(40), "1000", "10000000")));
    String body = order("K".repeat(20), "P".repeat(10), "D".repeat(200), items);

    send(body).andExpect(status().isCreated())
        .andExpect(content().string(containsString("\"total\":500000000000")));
  }

  @ParameterizedTest(name = "rejects one over the limit: {0}")
  @ValueSource(strings = {"clientId", "licensePlate", "description", "concept", "quantity", "unitPrice", "items"})
  void create_oneOverASingleLimit_returns400AndSavesNothing(String field) throws Exception {
    String clientId = "K".repeat("clientId".equals(field) ? 21 : 20);
    String plate = "P".repeat("licensePlate".equals(field) ? 11 : 10);
    String description = "D".repeat("description".equals(field) ? 201 : 200);
    String concept = "C".repeat("concept".equals(field) ? 41 : 40);
    String quantity = "quantity".equals(field) ? "1000.01" : "1000";
    String unitPrice = "unitPrice".equals(field) ? "10000001" : "10000000";
    int count = "items".equals(field) ? 51 : 50;
    String items = String.join(",", Collections.nCopies(count, item(concept, quantity, unitPrice)));

    send(order(clientId, plate, description, items)).andExpect(status().isBadRequest());

    verify(repository, never()).save(any());
  }

  private static String item(String concept, String quantity, String unitPrice) {
    return "{\"concept\":\"" + concept + "\",\"quantity\":" + quantity + ",\"unitPrice\":" + unitPrice + "}";
  }

  private static String order(String clientId, String plate, String description, String items) {
    return "{\"clientId\":\"" + clientId + "\",\"licensePlate\":\"" + plate + "\",\"description\":\""
        + description + "\",\"items\":[" + items + "]}";
  }

  private ResultActions send(String body) throws Exception {
    return mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body));
  }
}
