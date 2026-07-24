package com.financedash.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financedash.dto.AccountBalances;
import com.financedash.dto.BalanceSummaryResponse;
import com.financedash.service.BalanceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BalanceController.class)
class BalanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BalanceService service;

    private static BalanceSummaryResponse sample(LocalDate from, LocalDate to) {
        return new BalanceSummaryResponse(
                from, to,
                new BigDecimal("2824.50"), new BigDecimal("175.50"),
                new BigDecimal("175.50"), new BigDecimal("500.00"),
                new AccountBalances(new BigDecimal("2324.50"), BigDecimal.ZERO, new BigDecimal("500.00")));
    }

    @Test
    void returnsSummaryJson() throws Exception {
        when(service.summarize(any(), any())).thenReturn(sample(LocalDate.of(2026, 6, 25), LocalDate.of(2026, 7, 24)));

        mockMvc.perform(get("/api/balances").param("range", "MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netWorth").value(2824.50))
                .andExpect(jsonPath("$.spending").value(175.50))
                .andExpect(jsonPath("$.netSpending").value(175.50))
                .andExpect(jsonPath("$.netInvestment").value(500.00))
                .andExpect(jsonPath("$.accountBalances.checking").value(2324.50))
                .andExpect(jsonPath("$.accountBalances.investing").value(500.00));
    }

    @Test
    void rangeAllResolvesFromEpoch() throws Exception {
        when(service.summarize(any(), any())).thenReturn(sample(LocalDate.of(1970, 1, 1), LocalDate.now()));

        mockMvc.perform(get("/api/balances").param("range", "ALL"))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(service).summarize(fromCaptor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(fromCaptor.getValue()).isEqualTo(LocalDate.of(1970, 1, 1));
    }

    @Test
    void explicitFromToArePassedThrough() throws Exception {
        when(service.summarize(any(), any())).thenReturn(sample(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)));

        mockMvc.perform(get("/api/balances")
                        .param("from", "2026-01-01")
                        .param("to", "2026-03-31"))
                .andExpect(status().isOk());

        verify(service).summarize(eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 3, 31)));
    }

    @Test
    void noParamsDefaultsToLastMonth() throws Exception {
        when(service.summarize(any(), any())).thenReturn(sample(LocalDate.now().minusMonths(1), LocalDate.now()));

        mockMvc.perform(get("/api/balances"))
                .andExpect(status().isOk());

        // Default window ends today and starts one month + one day earlier (MONTH.resolveFrom).
        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(service).summarize(fromCaptor.capture(), toCaptor.capture());
        LocalDate today = toCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(fromCaptor.getValue())
                .isEqualTo(today.minusMonths(1).plusDays(1));
    }
}
