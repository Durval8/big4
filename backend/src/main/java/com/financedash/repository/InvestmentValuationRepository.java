package com.financedash.repository;

import com.financedash.domain.InvestmentValuation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentValuationRepository extends JpaRepository<InvestmentValuation, String> {
}
