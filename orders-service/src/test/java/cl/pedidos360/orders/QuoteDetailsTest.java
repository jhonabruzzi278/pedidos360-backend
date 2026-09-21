package cl.pedidos360.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.AdditionalAnswers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Lo que el frontend necesita para la vista previa y el PDF: los items de cada cotizacion y quien la emitio. */
class QuoteDetailsTest {
  private static final String BODY = """
      {"clientId":"CLI-0158","licensePlate":"JKLM45","description":"Frenos",
       "items":[{"concept":"Pastillas de freno delanteras","quantity":1,"unitPrice":48900},
                {"concept":"Mano de obra frenos (horas)","quantity":1.5,"unitPrice":22000}]}""";

  private final WorkOrderRepository repository = mock(WorkOrderRepository.class);
  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkOrderController(repository)).build();

  @Test
  void create_returnsTheItemsWithTheirSubtotalsAndTheAuthor() throws Exception {
    when(repository.save(any(WorkOrder.class))).then(AdditionalAnswers.returnsFirstArg());

    mvc.perform(post("/internal/work-orders").contentType(MediaType.APPLICATION_JSON).content(BODY)
            .header(WorkOrderController.CALLER_NAME_HEADER, "Camila%20Rojas%20P%C3%A9rez"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.createdBy", is("Camila Rojas Pérez")))
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[0].concept", is("Pastillas de freno delanteras")))
        .andExpect(jsonPath("$.items[0].unitPrice", is(48900)))
        .andExpect(jsonPath("$.items[0].subtotal", is(48900)))
        .andExpect(jsonPath("$.items[1].quantity", is(1.5)))
        .andExpect(jsonPath("$.items[1].subtotal", is(33000)))
        .andExpect(jsonPath("$.total", is(81900)));
  }

  @Test
  void create_withoutTheHeader_leavesTheAuthorEmpty() throws Exception {
    when(repository.save(any(WorkOrder.class))).then(AdditionalAnswers.returnsFirstArg());

    mvc.perform(post("/internal/work-orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.createdBy", nullValue()));
  }

  @Test
  void list_includesTheItemsOfEveryQuote() throws Exception {
    WorkOrder order = new WorkOrder("OT-2026-000101", "CLI-0142", "KLXP42", "Mantención", 45_900);
    order.addItem("Filtro de aceite", 1, 7_900);
    order.addItem("Aceite motor (litros)", 4, 9_500);
    order.recordCreator("Equipo de taller");
    when(repository.findAll()).thenReturn(List.of(order));

    mvc.perform(get("/internal/work-orders"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].createdBy", is("Equipo de taller")))
        .andExpect(jsonPath("$[0].items", hasSize(2)))
        .andExpect(jsonPath("$[0].itemCount", is(2)))
        .andExpect(jsonPath("$[0].calculatedSubtotal", is(45900)));
  }

  @Test
  void aQuoteWithoutAuthor_isListedWithANullAuthor() throws Exception {
    WorkOrder order = new WorkOrder("OT-2026-000102", "CLI-0087", "BRTC63", "Frenos", 10);
    order.addItem("Ítem", 1, 10);
    when(repository.findAll()).thenReturn(List.of(order));

    mvc.perform(get("/internal/work-orders")).andExpect(jsonPath("$[0].createdBy", nullValue()));
  }

  @Test
  void callerName_isDecodedTrimmedAndStrippedOfControlCharacters() {
    assertThat(WorkOrderController.cleanCallerName("Mar%C3%ADa%20Jos%C3%A9")).isEqualTo("María José");
    assertThat(WorkOrderController.cleanCallerName("%0A%20Juan%09")).isEqualTo("Juan");
    assertThat(WorkOrderController.cleanCallerName("Ana+Soto")).isEqualTo("Ana Soto");
  }

  @Test
  void callerName_isLimitedToTheColumnLength() {
    assertThat(WorkOrderController.cleanCallerName("a".repeat(150))).hasSize(100);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "%0A%0D", "%zz", "%"})
  void callerName_isNullWhenThereIsNothingUsable(String value) {
    assertThat(WorkOrderController.cleanCallerName(value)).isNull();
  }
}
