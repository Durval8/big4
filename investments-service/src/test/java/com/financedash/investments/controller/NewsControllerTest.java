package com.financedash.investments.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financedash.investments.dto.NewsResponse;
import com.financedash.investments.service.NewsService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NewsController.class)
class NewsControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private NewsService newsService;

    @Test
    void returnsFeedJson() throws Exception {
        when(newsService.getFeed()).thenReturn(new NewsResponse(
                Instant.parse("2026-07-24T12:00:00Z"),
                List.of(new NewsResponse.Item("AAPL", "Apple soars", "Shares up.", "https://x/1",
                        "Reuters", Instant.parse("2026-07-24T09:00:00Z")))));

        mockMvc.perform(get("/api/investments/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.items[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.items[0].headline").value("Apple soars"));
    }

    @Test
    void emptyFeedReturnsEmptyItems() throws Exception {
        when(newsService.getFeed()).thenReturn(new NewsResponse(null, List.of()));
        mockMvc.perform(get("/api/investments/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }
}
