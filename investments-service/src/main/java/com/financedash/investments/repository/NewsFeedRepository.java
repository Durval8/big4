package com.financedash.investments.repository;

import com.financedash.investments.domain.NewsFeed;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NewsFeedRepository extends MongoRepository<NewsFeed, String> {
}
