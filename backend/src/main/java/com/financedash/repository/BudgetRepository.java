package com.financedash.repository;

import com.financedash.domain.Budget;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findAllByOrderByNameAsc();
}
