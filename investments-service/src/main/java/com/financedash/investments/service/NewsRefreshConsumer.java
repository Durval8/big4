package com.financedash.investments.service;

import com.financedash.investments.messaging.InvestmentsMessaging;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Drains news-refresh triggers and rebuilds the feed. The rebuild is idempotent, so overlapping or
 * redundant triggers just recompute the same feed from current holdings.
 */
@Component
public class NewsRefreshConsumer {

    private final NewsService newsService;

    public NewsRefreshConsumer(NewsService newsService) {
        this.newsService = newsService;
    }

    @RabbitListener(queues = InvestmentsMessaging.NEWS_REFRESH_QUEUE)
    public void handle(String message) {
        newsService.rebuildFeed();
    }
}
