package com.wallet.adapter.paymentgateway.support;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Fake HTTP client for development/testing.
 * Always returns success after configured delay.
 */
public class FakeHttpClient {
    
    private static final long DEFAULT_DELAY_MS = 50;
    private final long delayMs;
    private final SecureLogger logger;
    
    public FakeHttpClient() {
        this(DEFAULT_DELAY_MS);
    }
    
    public FakeHttpClient(long delayMs) {
        this.delayMs = delayMs;
        this.logger = new SecureLogger(FakeHttpClient.class.getName());
    }
    
    public FakeResponse post(String url, Map<String, Object> body) {
        return execute("POST", url, body);
    }
    
    public FakeResponse get(String url) {
        return execute("GET", url, null);
    }
    
    private FakeResponse execute(String method, String url, Map<String, Object> body) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Simulate network delay
            Thread.sleep(delayMs);
            
            // Generate fake successful response
            FakeResponse response = generateSuccessResponse(url);
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("[FAKE] %s %s -> 200 OK (%dms)", method, url, duration);
            
            return response;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }
    
    private FakeResponse generateSuccessResponse(String url) {
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String externalRef = "EXT-" + System.currentTimeMillis();
        String vaNumber = "88810" + String.format("%011d", System.nanoTime() % 100000000000L);
        
        if (url.contains("/charge") || url.contains("/virtual_account") || url.contains("callback_virtual_accounts")) {
            return FakeResponse.success(Map.of(
                "status", "pending",
                "transaction_id", transactionId,
                "order_id", externalRef,
                "va_number", vaNumber,
                "bank", "bca",
                "expiry_time", Instant.now().plusSeconds(86400).toString()
            ));
        } else if (url.contains("/qr") || url.contains("qris")) {
            String qrString = "00020101021226610014ID.CO.FAKE.WWW01189360091800000000000215FAKE" + transactionId;
            return FakeResponse.success(Map.of(
                "status", "pending",
                "transaction_id", transactionId,
                "qr_string", qrString,
                "qr_code", qrString,
                "expiry_time", Instant.now().plusSeconds(1800).toString()
            ));
        } else if (url.contains("/disbursement") || url.contains("/withdraw")) {
            return FakeResponse.success(Map.of(
                "status", "PENDING",
                "id", transactionId,
                "external_id", externalRef,
                "amount", 100000,
                "bank_code", "BCA"
            ));
        } else if (url.contains("/inquiry") || url.contains("bank_account")) {
            return FakeResponse.success(Map.of(
                "status", "SUCCESS",
                "account_name", "JOHN DOE",
                "bank_name", "BANK CENTRAL ASIA"
            ));
        } else if (url.contains("/status") || url.contains("/invoice")) {
            return FakeResponse.success(Map.of(
                "status", "settlement",
                "transaction_status", "settlement",
                "transaction_id", transactionId
            ));
        }
        
        return FakeResponse.success(Map.of("status", "success"));
    }
    
    public record FakeResponse(
        int statusCode,
        Map<String, Object> body,
        boolean success
    ) {
        public static FakeResponse success(Map<String, Object> body) {
            return new FakeResponse(200, body, true);
        }
        
        public static FakeResponse error(int statusCode, String message) {
            return new FakeResponse(statusCode, Map.of("error", message), false);
        }
    }
}
