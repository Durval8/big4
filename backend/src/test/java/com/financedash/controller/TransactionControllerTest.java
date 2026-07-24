package com.financedash.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financedash.domain.AccountType;
import com.financedash.domain.Category;
import com.financedash.domain.Transaction;
import com.financedash.domain.TransactionType;
import com.financedash.dto.TransactionRequest;
import com.financedash.exception.InvalidTransactionException;
import com.financedash.exception.ResourceNotFoundException;
import com.financedash.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService service;

    private static Transaction sampleIncome() {
        Transaction t = new Transaction(
                "Paycheck", new BigDecimal("2000.00"), LocalDate.of(2026, 7, 1),
                AccountType.CHECKING, null, Category.SALARY, TransactionType.INCOME);
        ReflectionTestUtils.setField(t, "id", 1L);
        return t;
    }

    private static TransactionRequest validRequest() {
        return new TransactionRequest(
                "Paycheck", new BigDecimal("2000.00"), LocalDate.of(2026, 7, 1),
                AccountType.CHECKING, null, Category.SALARY, TransactionType.INCOME);
    }

    @Test
    void createReturns201WithBody() throws Exception {
        when(service.create(any())).thenReturn(sampleIncome());

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.description").value("Paycheck"))
                .andExpect(jsonPath("$.transactionType").value("INCOME"))
                .andExpect(jsonPath("$.category").value("SALARY"));
    }

    @Test
    void createReturns400OnBlankDescription() throws Exception {
        TransactionRequest bad = new TransactionRequest(
                "  ", new BigDecimal("10.00"), LocalDate.of(2026, 7, 1),
                AccountType.CHECKING, null, Category.SALARY, TransactionType.INCOME);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createReturns400OnNonPositiveAmount() throws Exception {
        TransactionRequest bad = new TransactionRequest(
                "x", new BigDecimal("0.00"), LocalDate.of(2026, 7, 1),
                AccountType.CHECKING, null, Category.SALARY, TransactionType.INCOME);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns400OnMissingRequiredEnum() throws Exception {
        // accountType omitted entirely.
        String body = """
                {"description":"x","amount":10.00,"transactionDate":"2026-07-01",
                 "category":"SALARY","transactionType":"INCOME"}
                """;

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns400WhenServiceRejectsCrossFieldRule() throws Exception {
        when(service.create(any()))
                .thenThrow(new InvalidTransactionException("linkedAccountType is required for TRANSFER transactions"));

        TransactionRequest req = new TransactionRequest(
                "bad transfer", new BigDecimal("50.00"), LocalDate.of(2026, 7, 1),
                AccountType.CHECKING, null, null, TransactionType.TRANSFER);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value("linkedAccountType is required for TRANSFER transactions"));
    }

    @Test
    void createReturns400WithErrorShapeOnInvalidEnum() throws Exception {
        // Unknown enum value → Jackson fails deserialization (HttpMessageNotReadableException),
        // before bean validation. Must still produce the ErrorResponse contract.
        String body = """
                {"description":"x","amount":10.00,"transactionDate":"2026-07-01",
                 "accountType":"CHECKING","category":"SALARY","transactionType":"NOPE"}
                """;

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.messages").isArray())
                .andExpect(jsonPath("$.messages[0]").isNotEmpty());
    }

    @Test
    void createReturns400WithErrorShapeOnMalformedJson() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages").isArray());
    }

    @Test
    void updateReturns404WhenMissing() throws Exception {
        when(service.update(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Transaction 99 not found"));

        mockMvc.perform(put("/api/transactions/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getByIdReturns200() throws Exception {
        when(service.findById(1L)).thenReturn(sampleIncome());

        mockMvc.perform(get("/api/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        when(service.findById(99L)).thenThrow(new ResourceNotFoundException("Transaction 99 not found"));

        mockMvc.perform(get("/api/transactions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listReturnsArray() throws Exception {
        when(service.findAll(any(), any(), any(), any())).thenReturn(List.of(sampleIncome()));

        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void listPassesFilterParamsThrough() throws Exception {
        when(service.findAll(any(), any(), eq(AccountType.SAVINGS), eq(Category.GROCERIES)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/transactions")
                        .param("from", "2026-01-01")
                        .param("to", "2026-12-31")
                        .param("accountType", "SAVINGS")
                        .param("category", "GROCERIES"))
                .andExpect(status().isOk());

        verify(service).findAll(
                eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 12, 31)),
                eq(AccountType.SAVINGS), eq(Category.GROCERIES));
    }

    @Test
    void updateReturns200() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(sampleIncome());

        mockMvc.perform(put("/api/transactions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/transactions/1"))
                .andExpect(status().isNoContent());
        verify(service).delete(1L);
    }

    @Test
    void deleteReturns404WhenMissing() throws Exception {
        doThrow(new ResourceNotFoundException("Transaction 99 not found"))
                .when(service).delete(99L);

        mockMvc.perform(delete("/api/transactions/99"))
                .andExpect(status().isNotFound());
    }
}
