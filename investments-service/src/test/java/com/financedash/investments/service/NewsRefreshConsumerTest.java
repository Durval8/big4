package com.financedash.investments.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The consumer delegates a trigger to a full feed rebuild. */
@ExtendWith(MockitoExtension.class)
class NewsRefreshConsumerTest {

    @Mock
    private NewsService newsService;
    @InjectMocks
    private NewsRefreshConsumer consumer;

    @Test
    void triggerRebuildsTheFeed() {
        consumer.handle("rebuild");
        verify(newsService).rebuildFeed();
    }
}
