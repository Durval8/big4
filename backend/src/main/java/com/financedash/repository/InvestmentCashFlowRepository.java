package com.financedash.repository;

import com.financedash.domain.InvestmentCashFlow;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentCashFlowRepository extends JpaRepository<InvestmentCashFlow, String> {

    List<InvestmentCashFlow> findByFlowDateLessThanEqual(LocalDate to);

    List<InvestmentCashFlow> findByFlowDateBetween(LocalDate from, LocalDate to);
}
