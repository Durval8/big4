package com.financedash.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financedash.domain.AccountType;
import com.financedash.domain.Category;
import com.financedash.domain.TransactionType;
import com.financedash.dto.BalanceSummaryResponse;
import com.financedash.dto.TransactionRequest;
import com.financedash.dto.TransactionResponse;
import com.financedash.support.AbstractPostgresContainerTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end through the real Spring context and a real Postgres (Testcontainers).
 * {@code @Transactional} rolls each test back, so methods stay isolated.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FinanceDashApplicationIT extends AbstractPostgresContainerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static TransactionRequest tx(
            String desc, String amount, LocalDate date,
            AccountType account, AccountType linked, Category category, TransactionType type) {
        return new TransactionRequest(desc, new BigDecimal(amount), date, account, linked, category, type);
    }

    private long create(TransactionRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TransactionResponse.class).id();
    }

    @Test
    void fullCrudLifecycle() throws Exception {
        long id = create(tx("Groceries", "150.00", LocalDate.of(2021, 3, 5),
                AccountType.CHECKING, null, Category.GROCERIES, TransactionType.EXPENSE));

        mockMvc.perform(get("/api/transactions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Groceries"));

        TransactionRequest updated = tx("Groceries (Whole Foods)", "175.50", LocalDate.of(2021, 3, 5),
                AccountType.CHECKING, null, Category.GROCERIES, TransactionType.EXPENSE);
        mockMvc.perform(put("/api/transactions/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Groceries (Whole Foods)"))
                .andExpect(jsonPath("$.amount").value(175.50));

        mockMvc.perform(delete("/api/transactions/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/transactions/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void transferWithoutDestinationIsRejected() throws Exception {
        TransactionRequest bad = tx("bad", "50.00", LocalDate.of(2021, 1, 1),
                AccountType.CHECKING, null, null, TransactionType.TRANSFER);
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value("linkedAccountType is required for TRANSFER transactions"));
    }

    @Test
    void expenseWithoutCategoryIsRejected() throws Exception {
        TransactionRequest bad = tx("bad", "50.00", LocalDate.of(2021, 1, 1),
                AccountType.CHECKING, null, null, TransactionType.EXPENSE);
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidEnumBodyReturnsErrorShape() throws Exception {
        String body = """
                {"description":"x","amount":10.00,"transactionDate":"2021-01-01",
                 "accountType":"NOT_A_REAL_ACCOUNT","transactionType":"EXPENSE","category":"GROCERIES"}
                """;
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages").isArray());
    }

    @Test
    void updateMissingReturns404() throws Exception {
        TransactionRequest req = tx("x", "10.00", LocalDate.of(2021, 1, 1),
                AccountType.CHECKING, null, Category.GROCERIES, TransactionType.EXPENSE);
        mockMvc.perform(put("/api/transactions/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void balancesReflectWholeLedger() throws Exception {
        // Investing is no longer part of the transaction ledger (covered by InvestmentIT).
        LocalDate d = LocalDate.of(2021, 6, 1);
        create(tx("Opening", "1000.00", d, AccountType.CHECKING, null, null, TransactionType.ADJUSTMENT));
        create(tx("Salary", "3000.00", d, AccountType.CHECKING, null, Category.SALARY, TransactionType.INCOME));
        create(tx("Groceries", "200.00", d, AccountType.CHECKING, null, Category.GROCERIES, TransactionType.EXPENSE));
        create(tx("To savings", "500.00", d, AccountType.CHECKING, AccountType.SAVINGS, null, TransactionType.TRANSFER));

        MvcResult result = mockMvc.perform(get("/api/balances").param("range", "ALL"))
                .andExpect(status().isOk())
                .andReturn();

        BalanceSummaryResponse s = objectMapper.readValue(
                result.getResponse().getContentAsString(), BalanceSummaryResponse.class);

        assertThat(s.accountBalances().checking()).isEqualByComparingTo("3300.00"); // 1000+3000−200−500
        assertThat(s.accountBalances().savings()).isEqualByComparingTo("500.00");
        assertThat(s.accountBalances().investing()).isEqualByComparingTo("0");       // no holdings
        assertThat(s.netWorth()).isEqualByComparingTo("3800.00");
        assertThat(s.netSpending()).isEqualByComparingTo("200.00");
        assertThat(s.spending()).isEqualByComparingTo("700.00");
        assertThat(s.netInvestment()).isEqualByComparingTo("0");
    }

    @Test
    void transferToInvestingIsRejected() throws Exception {
        TransactionRequest bad = tx("bad", "50.00", LocalDate.of(2021, 1, 1),
                AccountType.CHECKING, AccountType.INVESTING, null, TransactionType.TRANSFER);
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listReturnsNewestFirst() throws Exception {
        create(tx("older", "10.00", LocalDate.of(2021, 1, 1),
                AccountType.CHECKING, null, Category.GROCERIES, TransactionType.EXPENSE));
        create(tx("newer", "20.00", LocalDate.of(2021, 2, 1),
                AccountType.CHECKING, null, Category.GROCERIES, TransactionType.EXPENSE));

        mockMvc.perform(get("/api/transactions")
                        .param("from", "2020-01-01")
                        .param("to", "2022-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("newer"))
                .andExpect(jsonPath("$[1].description").value("older"));
    }
}
