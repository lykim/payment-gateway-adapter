package com.wallet.adapter.paymentgateway.support;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Handles timeout for provider operations.
 */
public class TimeoutHandler {
    
    private final ExecutorService executor;
    private final Duration defaultTimeout;
    private final SecureLogger logger;
    
    public TimeoutHandler(Duration defaultTimeout) {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.defaultTimeout = defaultTimeout;
        this.logger = new SecureLogger(TimeoutHandler.class.getName());
    }
    
    public <T> T executeWithTimeout(Supplier<T> operation, Duration timeout) 
            throws ProviderTimeoutException {
        try {
            CompletableFuture<T> future = CompletableFuture.supplyAsync(operation, executor);
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            logger.warn("Operation timed out after %dms", timeout.toMillis());
            throw new ProviderTimeoutException(
                "Operation timed out after " + timeout.toMillis() + "ms", e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operation interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Operation failed", cause);
        }
    }
    
    public <T> T executeWithTimeout(Supplier<T> operation) throws ProviderTimeoutException {
        return executeWithTimeout(operation, defaultTimeout);
    }
    
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
