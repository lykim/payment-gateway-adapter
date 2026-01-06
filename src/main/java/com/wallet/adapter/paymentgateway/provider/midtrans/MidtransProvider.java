package com.wallet.adapter.paymentgateway.provider.midtrans;

import com.wallet.adapter.paymentgateway.config.ProviderConfig;
import com.wallet.adapter.paymentgateway.core.PaymentGatewayProvider;
import com.wallet.adapter.paymentgateway.core.Provider;
import com.wallet.adapter.paymentgateway.support.*;
import com.wallet.core.application.port.outbound.service.PaymentGatewayService.*;
import com.wallet.core.domain.PaymentMethod;
import com.wallet.core.domain.PaymentStatus;

import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

/**
 * Midtrans payment gateway provider.
 * Supports: Virtual Account (BCA, BNI, BRI, Mandiri, Permata) and QRIS.
 */
@Provider("midtrans")
public class MidtransProvider implements PaymentGatewayProvider {
    
    private static final String PROVIDER_ID = "midtrans";
    private static final String DISPLAY_NAME = "Midtrans Payment Gateway";
    
    private ProviderConfig config;
    private final Set<String> labelCodes = new HashSet<>();
    private FakeHttpClient httpClient;
    private RateLimiter rateLimiter;
    private TimeoutHandler timeoutHandler;
    private SecureLogger logger;
    
    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }
    
    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }
    
    @Override
    public Set<PaymentMethod> getSupportedMethods() {
        return Set.of(PaymentMethod.VIRTUAL_ACCOUNT, PaymentMethod.QRIS);
    }
    
    @Override
    public boolean supports(String labelCode) {
        return labelCodes.contains(labelCode);
    }
    
    @Override
    public void registerLabelCodes(Set<String> codes) {
        this.labelCodes.addAll(codes);
    }
    
    @Override
    public void initialize(ProviderConfig config) {
        this.config = config;
        this.httpClient = new FakeHttpClient(50); // 50ms delay
        this.rateLimiter = new RateLimiter();
        this.timeoutHandler = new TimeoutHandler(Duration.ofMillis(config.getTimeoutMs()));
        this.logger = new SecureLogger(MidtransProvider.class.getName());
        
        // Configure rate limiter
        rateLimiter.configure(
            PROVIDER_ID, 
            config.getRateLimitPerSecond(), 
            config.getRateLimitBurstSize()
        );
        
        logger.info("Midtrans provider initialized with %d label codes", labelCodes.size());
    }
    
    @Override
    public PaymentGatewayResponse createVirtualAccount(PaymentGatewayRequest request) {
        return executeWithProtection("createVA", () -> {
            logger.request(PROVIDER_ID, "CREATE_VA", request);
            long startTime = System.currentTimeMillis();
            
            Map<String, Object> payload = buildVaPayload(request);
            var response = httpClient.post(config.getEndpoint("createVa"), payload);
            
            var result = mapToGatewayResponse(request, response.body());
            logger.response(PROVIDER_ID, "CREATE_VA", result, System.currentTimeMillis() - startTime);
            
            return result;
        });
    }
    
    @Override
    public PaymentGatewayResponse createQris(PaymentGatewayRequest request) {
        return executeWithProtection("createQris", () -> {
            logger.request(PROVIDER_ID, "CREATE_QRIS", request);
            long startTime = System.currentTimeMillis();
            
            Map<String, Object> payload = buildQrisPayload(request);
            var response = httpClient.post(config.getEndpoint("createQris"), payload);
            
            var result = mapToQrisResponse(request, response.body());
            logger.response(PROVIDER_ID, "CREATE_QRIS", result, System.currentTimeMillis() - startTime);
            
            return result;
        });
    }
    
    @Override
    public WithdrawResponse processWithdraw(WithdrawRequest request) {
        // Midtrans doesn't support disbursement directly
        throw new UnsupportedOperationException(
            "Midtrans does not support withdraw/disbursement. Use Xendit or other provider."
        );
    }
    
    @Override
    public boolean verifyCallback(PaymentGatewayCallback callback) {
        try {
            String receivedSignature = (String) callback.data().get("signature_key");
            String orderId = (String) callback.data().get("order_id");
            String statusCode = String.valueOf(callback.data().get("status_code"));
            String grossAmount = String.valueOf(callback.data().get("gross_amount"));
            
            // Midtrans signature: SHA512(order_id + status_code + gross_amount + server_key)
            String serverKey = config.getCredential("serverKey");
            String computed = computeSignature(orderId, statusCode, grossAmount, serverKey);
            
            boolean valid = computed.equals(receivedSignature);
            logger.info("Callback verification for %s: %s", orderId, valid ? "VALID" : "INVALID");
            
            return valid;
        } catch (Exception e) {
            logger.error("Callback verification failed", e);
            return false;
        }
    }
    
    @Override
    public PaymentStatus checkTransactionStatus(String externalReference) {
        return executeWithProtection("checkStatus", () -> {
            String url = config.getEndpoint("checkStatus")
                    .replace("{order_id}", externalReference);
            
            var response = httpClient.get(url);
            String status = (String) response.body().get("transaction_status");
            
            return mapStatus(status);
        });
    }
    
    @Override
    public InquiryResponse inquiryAccount(InquiryRequest request) {
        return executeWithProtection("inquiry", () -> {
            logger.request(PROVIDER_ID, "INQUIRY", request);
            
            var response = httpClient.post(config.getEndpoint("inquiry"), Map.of(
                "account_number", request.accountNumber(),
                "bank_code", request.bankCode()
            ));
            
            return new InquiryResponse(
                true,
                (String) response.body().get("account_name"),
                (String) response.body().get("bank_name"),
                response.body()
            );
        });
    }
    
    @Override
    public boolean isHealthy() {
        try {
            httpClient.get(config.getBaseUrl() + "/health");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public void shutdown() {
        logger.info("Midtrans provider shutting down");
        if (timeoutHandler != null) {
            timeoutHandler.shutdown();
        }
    }
    
    // === Private Helper Methods ===
    
    private <T> T executeWithProtection(String operation, Supplier<T> action) {
        // Rate limiting
        rateLimiter.acquire(PROVIDER_ID);
        
        // Timeout handling
        return timeoutHandler.executeWithTimeout(action);
    }
    
    private Map<String, Object> buildVaPayload(PaymentGatewayRequest request) {
        return Map.of(
            "payment_type", "bank_transfer",
            "transaction_details", Map.of(
                "order_id", request.referenceId().toString(),
                "gross_amount", request.amount().longValue()
            ),
            "bank_transfer", Map.of(
                "bank", extractBankFromLabel(request.labelCode())
            )
        );
    }
    
    private Map<String, Object> buildQrisPayload(PaymentGatewayRequest request) {
        return Map.of(
            "payment_type", "qris",
            "transaction_details", Map.of(
                "order_id", request.referenceId().toString(),
                "gross_amount", request.amount().longValue()
            ),
            "qris", Map.of(
                "acquirer", "gopay"
            )
        );
    }
    
    private String extractBankFromLabel(String labelCode) {
        if (labelCode.contains("BCA")) return "bca";
        if (labelCode.contains("BNI")) return "bni";
        if (labelCode.contains("BRI")) return "bri";
        if (labelCode.contains("MANDIRI")) return "mandiri";
        return "permata";
    }
    
    private PaymentGatewayResponse mapToGatewayResponse(PaymentGatewayRequest request, Map<String, Object> response) {
        String vaNumber = (String) response.get("va_number");
        String transactionId = (String) response.get("transaction_id");
        String orderId = (String) response.get("order_id");
        
        // For VA, payment URL is typically a payment instruction page
        String paymentUrl = config.getBaseUrl() + "/v2/payment/" + transactionId;
        
        return new PaymentGatewayResponse(
            transactionId,
            orderId,
            paymentUrl,
            PaymentStatus.PROCESSING,
            Map.of(
                "va_number", vaNumber != null ? vaNumber : "",
                "bank", extractBankFromLabel(request.labelCode()),
                "expiry_time", String.valueOf(response.get("expiry_time"))
            )
        );
    }
    
    private PaymentGatewayResponse mapToQrisResponse(PaymentGatewayRequest request, Map<String, Object> response) {
        String qrString = (String) response.get("qr_string");
        String transactionId = (String) response.get("transaction_id");
        String orderId = (String) response.get("order_id");
        
        // For QRIS, the payment URL could be the QR code image URL
        String paymentUrl = "qris://" + qrString;
        
        return new PaymentGatewayResponse(
            transactionId,
            orderId,
            paymentUrl,
            PaymentStatus.PROCESSING,
            Map.of(
                "qr_string", qrString != null ? qrString : "",
                "expiry_time", String.valueOf(response.get("expiry_time"))
            )
        );
    }
    
    private PaymentStatus mapStatus(String midtransStatus) {
        if (midtransStatus == null) return PaymentStatus.PROCESSING;
        
        return switch (midtransStatus.toLowerCase()) {
            case "capture", "settlement" -> PaymentStatus.COMPLETED;
            case "pending" -> PaymentStatus.PROCESSING;
            case "deny", "cancel", "expire" -> PaymentStatus.FAILED;
            case "refund", "partial_refund" -> PaymentStatus.FAILED;
            default -> PaymentStatus.PROCESSING;
        };
    }
    
    private String computeSignature(String orderId, String statusCode, String grossAmount, String serverKey) {
        // In real implementation: SHA512(order_id + status_code + gross_amount + server_key)
        // For fake implementation, just return a deterministic fake signature
        return "FAKE_SIG_" + orderId + "_" + statusCode;
    }
}
