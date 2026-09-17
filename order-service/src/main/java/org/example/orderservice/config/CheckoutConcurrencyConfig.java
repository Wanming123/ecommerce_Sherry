package org.example.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Sizing for the checkout inventory-reservation fan-out, scoped to a grocery-marketplace
 * traffic profile (Weee!-scale): baskets run larger than typical single-category retail
 * (~15 distinct SKUs/cart is a reasonable average), and per-instance peak checkout throughput
 * during dinner/weekend rushes is assumed around 20 checkouts/sec behind a horizontally-scaled
 * order-service fleet. Each reservation call is an internal RPC (~50ms p50, ~150ms p99), so by
 * Little's law the steady-state in-flight task count at peak is roughly
 * 20 checkouts/sec * 15 items/checkout * 0.15s (p99) ~= 45, rounded up to a core pool of 50.
 * Max pool gives headroom for a 3-4x burst (flash sales/promo pushes) before backpressure kicks
 * in via the bounded queue + CallerRunsPolicy, which throttles the calling checkout thread
 * instead of failing it outright or growing memory unbounded.
 */
@Configuration
public class CheckoutConcurrencyConfig {

    public static final int CORE_POOL_SIZE = 50;
    public static final int MAX_POOL_SIZE = 200;
    private static final int QUEUE_CAPACITY = 500;
    private static final int KEEP_ALIVE_SECONDS = 60;

    @Bean(name = "inventoryReservationExecutor")
    public Executor inventoryReservationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix("inventory-reserve-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
