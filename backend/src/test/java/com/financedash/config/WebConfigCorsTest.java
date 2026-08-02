package com.financedash.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financedash.controller.BudgetController;
import com.financedash.domain.Budget;
import com.financedash.domain.Category;
import com.financedash.service.BudgetService;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regression test for the production outage where every write returned {@code 403 Invalid CORS
 * request} while the app loaded and read fine.
 *
 * <p>Cause: {@code WebConfig} allowed only {@code http://localhost:*}, but browsers attach an
 * {@code Origin} header to every non-GET request — including same-origin ones — and Spring
 * CORS-checks any request carrying that header. On a real domain every POST/PUT/DELETE was
 * rejected before reaching a controller; GETs (no Origin header) were unaffected, which is what
 * made it look like a single broken feature rather than a total write outage.
 *
 * <p>Nothing in the old suite could catch it, because Docker, the test stack and Vite are all
 * localhost and therefore always matched. This test pins a <em>non</em>-localhost deployed origin.
 */
@WebMvcTest(BudgetController.class)
@Import(WebConfig.class)
@TestPropertySource(properties =
        "app.cors.allowed-origin-patterns=http://localhost:*,https://big4finance.online")
class WebConfigCorsTest {

    private static final String DEPLOYED = "https://big4finance.online";
    private static final String BODY = """
            {"name":"Food","value":400.00,"categories":["GROCERIES"]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BudgetService service;

    private static Budget sample() {
        return new Budget("Food", new BigDecimal("400.00"), EnumSet.of(Category.GROCERIES));
    }

    @Test
    void postFromTheDeployedOriginIsAllowed() throws Exception {
        when(service.create(any())).thenReturn(sample());

        mockMvc.perform(post("/api/budgets")
                        .header("Origin", DEPLOYED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void preflightFromTheDeployedOriginIsAllowed() throws Exception {
        mockMvc.perform(options("/api/budgets")
                        .header("Origin", DEPLOYED)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk());
    }

    @Test
    void postFromAnUnlistedOriginIsStillRejected() throws Exception {
        // The allowlist is the only thing protecting this unauthenticated API from cross-site
        // writes — widening it to "*" to "fix" CORS would be a real vulnerability.
        mockMvc.perform(post("/api/budgets")
                        .header("Origin", "https://evil.example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void getWithoutAnOriginHeaderIsUnaffected() throws Exception {
        // Why the outage looked partial: reads never carry an Origin, so they never CORS-check.
        when(service.findAll()).thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/budgets"))
                .andExpect(status().isOk());
    }
}
