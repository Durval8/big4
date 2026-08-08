package com.financedash.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financedash.domain.Budget;
import com.financedash.domain.Category;
import com.financedash.dto.BudgetProgressResponse;
import com.financedash.dto.BudgetRequest;
import com.financedash.service.BudgetService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BudgetController.class)
class BudgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BudgetService service;

    private static Budget sample() {
        Budget b = new Budget("Food", new BigDecimal("400.00"), EnumSet.of(Category.GROCERIES));
        ReflectionTestUtils.setField(b, "id", 1L);
        return b;
    }

    @Test
    void createReturns201() throws Exception {
        when(service.create(any())).thenReturn(sample());
        BudgetRequest req = new BudgetRequest("Food", new BigDecimal("400.00"), EnumSet.of(Category.GROCERIES));

        mockMvc.perform(post("/api/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Food"))
                .andExpect(jsonPath("$.categories[0]").value("GROCERIES"));
    }

    @Test
    void createReturns400OnBlankName() throws Exception {
        String body = """
                {"name":"  ","value":400.00,"categories":["GROCERIES"]}
                """;
        mockMvc.perform(post("/api/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createReturns400OnEmptyCategories() throws Exception {
        String body = """
                {"name":"Food","value":400.00,"categories":[]}
                """;
        mockMvc.perform(post("/api/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns400OnNonPositiveValue() throws Exception {
        String body = """
                {"name":"Food","value":0.00,"categories":["GROCERIES"]}
                """;
        mockMvc.perform(post("/api/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns400OnInvalidCategoryEnum() throws Exception {
        String body = """
                {"name":"Food","value":400.00,"categories":["NOT_A_CATEGORY"]}
                """;
        mockMvc.perform(post("/api/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages").isArray());
    }

    @Test
    void progressReturnsListForRange() throws Exception {
        BudgetProgressResponse p = new BudgetProgressResponse(
                1L, "Food", new BigDecimal("400.00"), new BigDecimal("394.22"), EnumSet.of(Category.GROCERIES),
                new BigDecimal("125.50"), new BigDecimal("268.72"),
                LocalDate.of(2026, 6, 25), LocalDate.of(2026, 7, 24));
        when(service.progress(any(), any(), any())).thenReturn(List.of(p));

        mockMvc.perform(get("/api/budgets/progress").param("range", "MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Food"))
                .andExpect(jsonPath("$[0].periodValue").value(394.22))
                .andExpect(jsonPath("$[0].spent").value(125.50))
                .andExpect(jsonPath("$[0].remaining").value(268.72));
    }

    @Test
    void listReturnsBudgets() throws Exception {
        when(service.findAll()).thenReturn(List.of(sample()));
        mockMvc.perform(get("/api/budgets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/budgets/1"))
                .andExpect(status().isNoContent());
        verify(service).delete(1L);
    }
}
