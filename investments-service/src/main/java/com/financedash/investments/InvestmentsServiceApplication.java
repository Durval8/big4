package com.financedash.investments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Investments microservice. Owns everything investment-related — holdings, share-based
 * valuation, buy/sell events, the stock-price integration, and the periodic price-refresh
 * job — in its own MongoDB. Communicates with the finance-dash backend only over RabbitMQ.
 */
@SpringBootApplication
@EnableScheduling
public class InvestmentsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvestmentsServiceApplication.class, args);
    }
}
