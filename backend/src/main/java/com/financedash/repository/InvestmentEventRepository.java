package com.financedash.repository;

import com.financedash.domain.InvestmentEvent;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentEventRepository extends JpaRepository<InvestmentEvent, Long> {

    List<InvestmentEvent> findByEventDateLessThanEqual(LocalDate to);

    List<InvestmentEvent> findByEventDateBetween(LocalDate from, LocalDate to);

    List<InvestmentEvent> findByInvestmentId(Long investmentId);
}
