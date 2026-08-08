package com.financedash.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financedash.domain.AccountType;
import com.financedash.domain.Category;
import com.financedash.domain.TransactionType;
import com.financedash.dto.AnalyticsResponse;
import com.financedash.dto.BalanceSummaryResponse;
import com.financedash.dto.CategoryTotal;
import com.financedash.dto.TransactionRequest;
import com.financedash.support.AbstractPostgresContainerTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// No broker in this test; keep the investment message listeners from trying to connect.
@org.springframework.test.context.TestPropertySource(
        properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class AnalyticsIT extends AbstractPostgresContainerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private void createTransaction(
            String amount, LocalDate date, AccountType accountType, AccountType linkedAccountType,
            Category category, TransactionType type) throws Exception {
        TransactionRequest req = new TransactionRequest(
                "t", new BigDecimal(amount), date, accountType, linkedAccountType, category, type);
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    private void expense(String amount, LocalDate date, Category category) throws Exception {
        createTransaction(amount, date, AccountType.CHECKING, null, category, TransactionType.EXPENSE);
    }

    private void income(String amount, LocalDate date, Category category) throws Exception {
        createTransaction(amount, date, AccountType.CHECKING, null, category, TransactionType.INCOME);
    }

    private AnalyticsResponse analytics(String from, String to) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/analytics").param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AnalyticsResponse.class);
    }

    private BalanceSummaryResponse balances(String from, String to) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/balances").param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), BalanceSummaryResponse.class);
    }

    @Test
    void reconcilesAgainstBalancesNetSpendingAndSumsCategories() throws Exception {
        expense("100.00", LocalDate.of(2026, 6, 10), Category.GROCERIES);
        expense("50.00", LocalDate.of(2026, 6, 15), Category.DINING_OUT);
        income("2000.00", LocalDate.of(2026, 6, 20), Category.SALARY);
        // Outside the window -- must not leak into either endpoint's totals.
        expense("999.00", LocalDate.of(2019, 1, 1), Category.TRAVEL);

        AnalyticsResponse analytics = analytics("2026-01-01", "2026-12-31");
        BalanceSummaryResponse balances = balances("2026-01-01", "2026-12-31");

        assertThat(analytics.totalExpense()).isEqualByComparingTo(balances.netSpending());
        assertThat(analytics.totalIncome()).isEqualByComparingTo("2000.00");

        BigDecimal categorySum = analytics.categories().stream()
                .map(CategoryTotal::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(categorySum).isEqualByComparingTo(analytics.totalExpense());
    }

    @Test
    void bucketsAreGapFilledAndTransfersExcluded() throws Exception {
        expense("40.00", LocalDate.of(2026, 6, 1), Category.GROCERIES);
        createTransaction("500.00", LocalDate.of(2026, 6, 2), AccountType.CHECKING, AccountType.SAVINGS,
                null, TransactionType.TRANSFER);
        expense("60.00", LocalDate.of(2026, 6, 5), Category.GROCERIES);

        AnalyticsResponse analytics = analytics("2026-06-01", "2026-06-05");

        assertThat(analytics.buckets()).hasSize(5);
        assertThat(analytics.totalExpense()).isEqualByComparingTo("100.00");
        // The gap day (June 3rd/4th) is present with zero expense, not omitted.
        assertThat(analytics.buckets()).extracting(b -> b.start())
                .containsExactly(
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 3),
                        LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 5));
    }

    @Test
    void zeroTransactionsReturnsEmptyAggregatesNotAnError() throws Exception {
        AnalyticsResponse analytics = analytics("2026-01-01", "2026-01-31");

        assertThat(analytics.totalIncome()).isEqualByComparingTo("0");
        assertThat(analytics.totalExpense()).isEqualByComparingTo("0");
        assertThat(analytics.categories()).isEmpty();
    }
}
