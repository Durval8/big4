package com.financedash.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A spending budget: a named target ({@code value}) tracked against the sum of EXPENSE
 * transactions whose {@link Category} falls in {@code categories}, over a period chosen
 * by the caller. The "spent" figure itself is computed on demand (see BudgetService),
 * not stored.
 */
@Entity
@Table(name = "budgets")
@EntityListeners(AuditingEntityListener.class)
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    /**
     * EAGER on purpose: categories are needed both to map the response and to compute
     * progress, and services are not {@code @Transactional} with {@code open-in-view=false},
     * so lazy loading would fail once the session closes.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "budget_categories", joinColumns = @JoinColumn(name = "budget_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private Set<Category> categories = EnumSet.noneOf(Category.class);

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected Budget() {
        // JPA
    }

    public Budget(String name, BigDecimal value, Set<Category> categories) {
        this.name = name;
        this.value = value;
        setCategories(categories);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public Set<Category> getCategories() {
        return categories;
    }

    public void setCategories(Set<Category> categories) {
        this.categories = (categories == null || categories.isEmpty())
                ? EnumSet.noneOf(Category.class)
                : EnumSet.copyOf(categories);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
