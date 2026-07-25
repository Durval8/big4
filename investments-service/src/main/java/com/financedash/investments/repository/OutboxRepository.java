package com.financedash.investments.repository;

import com.financedash.investments.domain.OutboxMessage;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OutboxRepository extends MongoRepository<OutboxMessage, String> {

    List<OutboxMessage> findByPublishedFalseOrderByCreatedAtAsc();
}
