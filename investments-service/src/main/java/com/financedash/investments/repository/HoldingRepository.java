package com.financedash.investments.repository;

import com.financedash.investments.domain.Holding;
import com.financedash.investments.domain.HoldingStatus;
import com.financedash.investments.domain.PriceStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HoldingRepository extends MongoRepository<Holding, String> {

    List<Holding> findAllByOrderByStockSymbolAsc();

    List<Holding> findByStatus(HoldingStatus status);

    Optional<Holding> findFirstByStockSymbolAndStatus(String stockSymbol, HoldingStatus status);

    List<Holding> findByStockSymbolAndStatus(String stockSymbol, HoldingStatus status);

    /** Symbols the price job may fetch: OPEN and actually recognized by the provider. */
    List<Holding> findByStatusAndPriceStatusNot(HoldingStatus status, PriceStatus priceStatus);
}
