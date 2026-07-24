package com.financedash.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.financedash.domain.InvestmentStatus;
import com.financedash.dto.InvestmentRequest;
import com.financedash.dto.InvestmentResponse;
import com.financedash.dto.InvestmentUpdateRequest;
import com.financedash.exception.InvalidInvestmentException;
import com.financedash.exception.ResourceNotFoundException;
import com.financedash.service.InvestmentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InvestmentController.class)
class InvestmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InvestmentService service;

    private static InvestmentResponse sample() {
        return new InvestmentResponse(1L, "AAPL", new BigDecimal("150.00"), new BigDecimal("100.00"),
                new BigDecimal("50.00"), InvestmentStatus.OPEN, Instant.now(), Instant.now());
    }

    @Test
    void createReturns201() throws Exception {
        when(service.create(any())).thenReturn(sample());
        InvestmentRequest req = new InvestmentRequest("AAPL", new BigDecimal("100.00"), AccountType.CHECKING);

        mockMvc.perform(post("/api/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.stockSymbol").value("AAPL"))
                .andExpect(jsonPath("$.positionChangePct").value(50.00));
    }

    @Test
    void createReturns400OnBlankSymbol() throws Exception {
        String body = "{\"stockSymbol\":\"  \",\"amount\":100.00,\"sourceAccount\":\"CHECKING\"}";
        mockMvc.perform(post("/api/investments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns400OnNonPositiveAmount() throws Exception {
        String body = "{\"stockSymbol\":\"AAPL\",\"amount\":0.00,\"sourceAccount\":\"CHECKING\"}";
        mockMvc.perform(post("/api/investments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns400WhenServiceRejectsSource() throws Exception {
        when(service.create(any()))
                .thenThrow(new InvalidInvestmentException("sourceAccount must be CHECKING or SAVINGS"));
        InvestmentRequest req = new InvestmentRequest("AAPL", new BigDecimal("100.00"), AccountType.INVESTING);

        mockMvc.perform(post("/api/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value("sourceAccount must be CHECKING or SAVINGS"));
    }

    @Test
    void listAndSummary() throws Exception {
        when(service.findAll()).thenReturn(List.of(sample()));
        mockMvc.perform(get("/api/investments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        when(service.findById(99L)).thenThrow(new ResourceNotFoundException("Investment 99 not found"));
        mockMvc.perform(get("/api/investments/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateReturns200() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(sample());
        mockMvc.perform(put("/api/investments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InvestmentUpdateRequest("AAPL", new BigDecimal("150.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentValue").value(150.00));
    }

    @Test
    void cashOutReturns200() throws Exception {
        when(service.cashOut(eq(1L), any())).thenReturn(sample());
        mockMvc.perform(post("/api/investments/1/cash-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50.00}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/investments/1")).andExpect(status().isNoContent());
        verify(service).delete(1L);
    }
}
