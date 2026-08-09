package com.financedash.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financedash.domain.Category;
import com.financedash.dto.AnalyticsResponse;
import com.financedash.dto.BucketUnit;
import com.financedash.dto.CategoryTotal;
import com.financedash.dto.TimeBucket;
import com.financedash.service.AnalyticsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService service;

    private static AnalyticsResponse sample() {
        return new AnalyticsResponse(
                LocalDate.of(2026, 7, 4), LocalDate.of(2026, 8, 2),
                LocalDate.of(2026, 6, 4), LocalDate.of(2026, 7, 3),
                BucketUnit.DAY,
                new BigDecimal("3000.00"), new BigDecimal("1284.55"),
                List.of(new CategoryTotal(Category.GROCERIES, new BigDecimal("412.30"), new BigDecimal("380.00"))),
                List.of(new CategoryTotal(Category.SALARY, new BigDecimal("3000.00"), new BigDecimal("3000.00"))),
                List.of(new TimeBucket(LocalDate.of(2026, 7, 4), BigDecimal.ZERO, new BigDecimal("42.10"))));
    }

    @Test
    void getReturnsAnalyticsJson() throws Exception {
        when(service.getAnalytics(any(), any(), any())).thenReturn(sample());

        mockMvc.perform(get("/api/analytics").param("range", "MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-07-04"))
                .andExpect(jsonPath("$.to").value("2026-08-02"))
                .andExpect(jsonPath("$.previousFrom").value("2026-06-04"))
                .andExpect(jsonPath("$.bucketUnit").value("DAY"))
                .andExpect(jsonPath("$.totalIncome").value(3000.00))
                .andExpect(jsonPath("$.totalExpense").value(1284.55))
                .andExpect(jsonPath("$.categories[0].category").value("GROCERIES"))
                .andExpect(jsonPath("$.categories[0].amount").value(412.30))
                .andExpect(jsonPath("$.categories[0].previousAmount").value(380.00))
                .andExpect(jsonPath("$.incomeCategories[0].category").value("SALARY"))
                .andExpect(jsonPath("$.incomeCategories[0].amount").value(3000.00))
                .andExpect(jsonPath("$.buckets[0].start").value("2026-07-04"))
                .andExpect(jsonPath("$.buckets[0].expense").value(42.10));
    }

    @Test
    void explicitFromIsForwardedSeparatelyFromTheResolvedWindow() throws Exception {
        // The controller must pass the raw `from` param through untouched (so the service can
        // detect "explicit dates" and skip the earliest-transaction floor), alongside the window
        // Period.resolve already computed from range/from/to.
        when(service.getAnalytics(any(), any(), any())).thenReturn(sample());

        mockMvc.perform(get("/api/analytics")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31"))
                .andExpect(status().isOk());

        verify(service).getAnalytics(eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 1, 31)));
    }

    @Test
    void namedRangePassesNullAsTheExplicitFrom() throws Exception {
        when(service.getAnalytics(any(), any(), any())).thenReturn(sample());

        mockMvc.perform(get("/api/analytics").param("range", "WEEK"))
                .andExpect(status().isOk());

        verify(service).getAnalytics(isNull(), any(), any());
    }

    @Test
    void noParamsDefaultsButStillPassesNullExplicitFrom() throws Exception {
        when(service.getAnalytics(any(), any(), any())).thenReturn(sample());

        mockMvc.perform(get("/api/analytics")).andExpect(status().isOk());

        verify(service).getAnalytics(isNull(), any(), any());
    }
}
