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
import com.financedash.dto.BudgetProgressResponse;
import com.financedash.dto.BudgetRequest;
import com.financedash.dto.BudgetResponse;
import com.financedash.dto.TransactionRequest;
import com.financedash.support.AbstractPostgresContainerTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// No broker in this test; keep the investment message listeners from trying to connect.
@org.springframework.test.context.TestPropertySource(
        properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class BudgetIT extends AbstractPostgresContainerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private long createBudget(BudgetRequest req) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), BudgetResponse.class).id();
    }

    private void createExpense(String amount, LocalDate date, Category category) throws Exception {
        TransactionRequest req = new TransactionRequest(
                "e", new BigDecimal(amount), date, AccountType.CHECKING, null, category, TransactionType.EXPENSE);
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    private List<BudgetProgressResponse> progress(String from, String to) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/budgets/progress")
                        .param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andReturn();
        return List.of(objectMapper.readValue(
                result.getResponse().getContentAsString(), BudgetProgressResponse[].class));
    }

    @Test
    void crudLifecycle() throws Exception {
        long id = createBudget(new BudgetRequest(
                "Food", new BigDecimal("400.00"), EnumSet.of(Category.GROCERIES, Category.DINING_OUT)));

        mockMvc.perform(get("/api/budgets/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Food"))
                .andExpect(jsonPath("$.categories.length()").value(2));

        mockMvc.perform(put("/api/budgets/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BudgetRequest(
                                "Essentials", new BigDecimal("600.00"), EnumSet.of(Category.GROCERIES)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Essentials"))
                .andExpect(jsonPath("$.categories.length()").value(1));

        mockMvc.perform(delete("/api/budgets/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/budgets/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void emptyCategoriesRejected() throws Exception {
        String body = """
                {"name":"Food","value":400.00,"categories":[]}
                """;
        mockMvc.perform(post("/api/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void progressCountsOnlyMatchingExpensesInPeriod() throws Exception {
        createBudget(new BudgetRequest(
                "Food", new BigDecimal("400.00"), EnumSet.of(Category.GROCERIES, Category.DINING_OUT)));

        createExpense("100.00", LocalDate.of(2021, 6, 10), Category.GROCERIES);   // counts
        createExpense("50.00", LocalDate.of(2021, 6, 15), Category.DINING_OUT);   // counts
        createExpense("999.00", LocalDate.of(2021, 6, 10), Category.TRAVEL);      // wrong category
        createExpense("25.00", LocalDate.of(2019, 1, 1), Category.GROCERIES);     // outside the window

        List<BudgetProgressResponse> result = progress("2021-01-01", "2021-12-31");

        assertThat(result).hasSize(1);
        BudgetProgressResponse food = result.get(0);
        assertThat(food.spent()).isEqualByComparingTo("150.00");
        assertThat(food.remaining()).isEqualByComparingTo("250.00");
    }
}
