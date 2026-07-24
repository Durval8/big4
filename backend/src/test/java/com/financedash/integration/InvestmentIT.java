package com.financedash.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financedash.domain.AccountType;
import com.financedash.domain.Category;
import com.financedash.domain.InvestmentStatus;
import com.financedash.domain.TransactionType;
import com.financedash.dto.BalanceSummaryResponse;
import com.financedash.dto.CashOutRequest;
import com.financedash.dto.InvestmentRequest;
import com.financedash.dto.InvestmentResponse;
import com.financedash.dto.InvestmentUpdateRequest;
import com.financedash.dto.TransactionRequest;
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

/** End-to-end: investment operations against real Postgres, including the balance fold-in. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InvestmentIT extends AbstractPostgresContainerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private void seedChecking(String amount) throws Exception {
        TransactionRequest income = new TransactionRequest(
                "seed", new BigDecimal(amount), LocalDate.of(2021, 1, 1),
                AccountType.CHECKING, null, Category.SALARY, TransactionType.INCOME);
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(income)))
                .andExpect(status().isCreated());
    }

    private InvestmentResponse addInvestment(String symbol, String amount, AccountType source) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InvestmentRequest(symbol, new BigDecimal(amount), source))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(r.getResponse().getContentAsString(), InvestmentResponse.class);
    }

    private BalanceSummaryResponse balances() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/balances").param("range", "ALL"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(r.getResponse().getContentAsString(), BalanceSummaryResponse.class);
    }

    private BalanceSummaryResponse balancesBetween(String from, String to) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/balances").param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(r.getResponse().getContentAsString(), BalanceSummaryResponse.class);
    }

    @Test
    void lifecycleFoldsIntoBalancesAndTracksPositionChange() throws Exception {
        seedChecking("1000.00");

        // Buy: debits checking, INVESTING reflects the holding, net worth flat.
        InvestmentResponse bought = addInvestment("AAPL", "400.00", AccountType.CHECKING);
        long id = bought.id();
        assertThat(bought.positionChangePct()).isEqualByComparingTo("0.00");

        BalanceSummaryResponse b1 = balances();
        assertThat(b1.accountBalances().checking()).isEqualByComparingTo("600.00");
        assertThat(b1.accountBalances().investing()).isEqualByComparingTo("400.00");
        assertThat(b1.netWorth()).isEqualByComparingTo("1000.00");
        assertThat(b1.netInvestment()).isEqualByComparingTo("400.00");

        // Mark-to-market €400 → €600: +50%, net worth rises by the unrealized gain.
        MvcResult upd = mockMvc.perform(put("/api/investments/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InvestmentUpdateRequest("AAPL", new BigDecimal("600.00")))))
                .andExpect(status().isOk())
                .andReturn();
        InvestmentResponse revalued = objectMapper.readValue(upd.getResponse().getContentAsString(), InvestmentResponse.class);
        assertThat(revalued.positionChangePct()).isEqualByComparingTo("50.00");

        BalanceSummaryResponse b2 = balances();
        assertThat(b2.accountBalances().investing()).isEqualByComparingTo("600.00");
        assertThat(b2.netWorth()).isEqualByComparingTo("1200.00");

        // Partial cash-out €250 → savings.
        mockMvc.perform(post("/api/investments/" + id + "/cash-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CashOutRequest(new BigDecimal("250.00")))))
                .andExpect(status().isOk());

        BalanceSummaryResponse b3 = balances();
        assertThat(b3.accountBalances().savings()).isEqualByComparingTo("250.00");
        assertThat(b3.accountBalances().investing()).isEqualByComparingTo("350.00");
        assertThat(b3.netWorth()).isEqualByComparingTo("1200.00");
        assertThat(b3.netInvestment()).isEqualByComparingTo("150.00"); // 400 in − 250 out

        // Full cash-out of the remaining €350 → CASHED_OUT, kept as history.
        MvcResult out = mockMvc.perform(post("/api/investments/" + id + "/cash-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CashOutRequest(new BigDecimal("350.00")))))
                .andExpect(status().isOk())
                .andReturn();
        InvestmentResponse closed = objectMapper.readValue(out.getResponse().getContentAsString(), InvestmentResponse.class);
        assertThat(closed.status()).isEqualTo(InvestmentStatus.CASHED_OUT);
        assertThat(closed.currentValue()).isEqualByComparingTo("0.00");
        assertThat(closed.positionChangePct()).isNull(); // net cash invested now ≤ 0

        BalanceSummaryResponse b4 = balances();
        assertThat(b4.accountBalances().savings()).isEqualByComparingTo("600.00");
        assertThat(b4.accountBalances().investing()).isEqualByComparingTo("0");
        assertThat(b4.netWorth()).isEqualByComparingTo("1200.00");
    }

    @Test
    void addingSameSymbolMergesHolding() throws Exception {
        seedChecking("1000.00");
        addInvestment("MSFT", "100.00", AccountType.CHECKING);
        InvestmentResponse merged = addInvestment("msft", "50.00", AccountType.SAVINGS);

        assertThat(merged.stockSymbol()).isEqualTo("MSFT");
        assertThat(merged.currentValue()).isEqualByComparingTo("150.00");
        assertThat(merged.netCashInvested()).isEqualByComparingTo("150.00");
    }

    @Test
    void netInvestmentExcludesEventsOutsideThePeriodButInvestingStaysCurrent() throws Exception {
        // The buy's FUND event is dated today; query a window entirely in the past.
        addInvestment("NVDA", "500.00", AccountType.CHECKING);

        BalanceSummaryResponse past = balancesBetween("2000-01-01", "2000-12-31");

        // Flow is period-scoped: today's fund is outside the 2000 window → excluded.
        assertThat(past.netInvestment()).isEqualByComparingTo("0");
        // Holdings value is always current regardless of the period (documented simplification).
        assertThat(past.accountBalances().investing()).isEqualByComparingTo("500.00");
    }

    @Test
    void cashOutExceedingPositionIsRejected() throws Exception {
        seedChecking("1000.00");
        long id = addInvestment("GOOG", "100.00", AccountType.CHECKING).id();
        mockMvc.perform(post("/api/investments/" + id + "/cash-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CashOutRequest(new BigDecimal("200.00")))))
                .andExpect(status().isBadRequest());
    }
}
