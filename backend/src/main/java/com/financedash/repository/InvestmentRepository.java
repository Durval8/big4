package com.financedash.repository;

import com.financedash.domain.Investment;
import com.financedash.domain.InvestmentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentRepository extends JpaRepository<Investment, Long> {

    List<Investment> findAllByOrderByStockSymbolAsc();

    List<Investment> findByStatus(InvestmentStatus status);

    Optional<Investment> findFirstByStockSymbolAndStatus(String stockSymbol, InvestmentStatus status);
}
