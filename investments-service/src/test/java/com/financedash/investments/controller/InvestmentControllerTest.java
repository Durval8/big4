package com.financedash.investments.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financedash.investments.domain.HoldingStatus;
import com.financedash.investments.domain.PriceStatus;
import com.financedash.investments.dto.HoldingResponse;
import com.financedash.investments.exception.InvalidInvestmentException;
import com.financedash.investments.exception.ProviderUnavailableException;
import com.financedash.investments.exception.ResourceNotFoundException;
import com.financedash.investments.service.HoldingService;
import java.math.BigDecimal;
import java.time.Instant;
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
    @MockBean
    private HoldingService service;

    // 5 shares @ avgCost 10.00, latest price 15.00: currentValue 75.00, positionChangePct 50.00.
    private static HoldingResponse sample() {
        return new HoldingResponse("abc", "AAPL",
                new BigDecimal("5.000000"), new BigDecimal("50.00"), new BigDecimal("10.0000"),
                new BigDecimal("15.0000"), new BigDecimal("75.00"), new BigDecimal("50.00"),
                new BigDecimal("0.00"), new BigDecimal("50.00"), PriceStatus.OK,
                Instant.parse("2026-07-24T00:00:00Z"), HoldingStatus.OPEN,
                Instant.parse("2026-07-24T00:00:00Z"), Instant.parse("2026-07-24T00:00:00Z"));
    }

    @Test
    void buyReturns201() throws Exception {
        when(service.buy(any())).thenReturn(sample());
        mockMvc.perform(post("/api/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockSymbol\":\"AAPL\",\"amount\":50,\"sourceAccount\":\"CHECKING\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stockSymbol").value("AAPL"))
                .andExpect(jsonPath("$.positionChangePct").value(50.00));
    }

    @Test
    void missingAmountFailsValidation() throws Exception {
        mockMvc.perform(post("/api/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockSymbol\":\"AAPL\",\"sourceAccount\":\"CHECKING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void invalidEnumBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockSymbol\":\"AAPL\",\"amount\":50,\"sourceAccount\":\"INVESTING\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void domainRuleViolationReturns400() throws Exception {
        when(service.buy(any())).thenThrow(new InvalidInvestmentException("bad"));
        mockMvc.perform(post("/api/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockSymbol\":\"AAPL\",\"amount\":50,\"sourceAccount\":\"CHECKING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value("bad"));
    }

    @Test
    void providerDownReturns503() throws Exception {
        when(service.buy(any())).thenThrow(new ProviderUnavailableException("down"));
        mockMvc.perform(post("/api/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockSymbol\":\"AAPL\",\"amount\":50,\"sourceAccount\":\"CHECKING\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void unknownHoldingReturns404() throws Exception {
        when(service.get(eq("nope"))).thenThrow(new ResourceNotFoundException("Holding nope not found"));
        mockMvc.perform(get("/api/investments/nope"))
                .andExpect(status().isNotFound());
    }
}
