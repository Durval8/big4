package com.financedash.investments.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Fires a feed rebuild on the configured cadence (default every 4h). */
@Component
public class NewsRefreshScheduler {

    private final NewsRefreshPublisher publisher;

    public NewsRefreshScheduler(NewsRefreshPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(cron = "${news.refresh-cron:0 0 */4 * * *}")
    public void scheduledRebuild() {
        publisher.requestRebuild();
    }
}
