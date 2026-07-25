package com.financedash.investments.controller;

import com.financedash.investments.dto.NewsResponse;
import com.financedash.investments.service.NewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Investments page's news feed. Served by this service (routed by the gateway). */
@RestController
@RequestMapping("/api/investments/news")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    public NewsResponse feed() {
        return newsService.getFeed();
    }
}
