package com.wallet.adapter.paymentgateway.provider.xendit;

import com.wallet.adapter.paymentgateway.config.ProviderConfig;
import com.wallet.adapter.paymentgateway.core.PaymentGatewayProvider;
import com.wallet.adapter.paymentgateway.core.Provider;
import com.wallet.adapter.paymentgateway.support.*;
import com.wallet.core.application.port.outbound.service.PaymentGatewayService.*;
import com.wallet.core.domain.PaymentMethod;
import com.wallet.core.domain.PaymentStatus;
import com.wallet.core.domain.QrisDataType;

import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

/**
 * Xendit payment gateway provider.
 * Supports: Virtual Account (BCA, BNI, BRI, Mandiri, Permata, BSI), QRIS, and Disbursement.
 */
@Provider("xendit")
public class XenditProvider implements PaymentGatewayProvider {
    
    private static final String PROVIDER_ID = "xendit";
    private static final String DISPLAY_NAME = "Xendit Payment Gateway";
    
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
        this.logger = new SecureLogger(XenditProvider.class.getName());
        
        // Configure rate limiter
        rateLimiter.configure(
            PROVIDER_ID, 
            config.getRateLimitPerSecond(), 
            config.getRateLimitBurstSize()
        );
        
        logger.info("Xendit provider initialized with %d label codes", labelCodes.size());
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
        return executeWithProtection("withdraw", () -> {
            logger.request(PROVIDER_ID, "WITHDRAW", request);
            long startTime = System.currentTimeMillis();
            
            Map<String, Object> payload = buildWithdrawPayload(request);
            var response = httpClient.post(config.getEndpoint("disburse"), payload);
            
            var result = mapToWithdrawResponse(response.body());
            logger.response(PROVIDER_ID, "WITHDRAW", result, System.currentTimeMillis() - startTime);
            
            return result;
        });
    }
    
    @Override
    public boolean verifyCallback(PaymentGatewayCallback callback) {
        try {
            // Xendit uses x-callback-token header for verification
            String receivedToken = (String) callback.data().get("x-callback-token");
            String expectedToken = config.getCredential("callbackToken");
            
            if (expectedToken == null) {
                // Also check verificationToken
                expectedToken = config.getCredential("verificationToken");
            }
            
            boolean valid = expectedToken != null && expectedToken.equals(receivedToken);
            String invoiceId = (String) callback.data().get("id");
            
            logger.info("Callback verification for %s: %s", invoiceId, valid ? "VALID" : "INVALID");
            
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
                    .replace("{invoice_id}", externalReference);
            
            var response = httpClient.get(url);
            String status = (String) response.body().get("status");
            
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
        logger.info("Xendit provider shutting down");
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
            "external_id", request.referenceId().toString(),
            "bank_code", extractBankFromLabel(request.labelCode()),
            "name", "Payment " + request.referenceId(),
            "expected_amount", request.amount().longValue(),
            "is_closed", true,
            "is_single_use", true
        );
    }
    
    private Map<String, Object> buildQrisPayload(PaymentGatewayRequest request) {
        return Map.of(
            "external_id", request.referenceId().toString(),
            "type", "DYNAMIC",
            "callback_url", request.callbackUrl(),
            "amount", request.amount().longValue()
        );
    }
    
    private Map<String, Object> buildWithdrawPayload(WithdrawRequest request) {
        return Map.of(
            "external_id", request.referenceId().toString(),
            "bank_code", request.destinationBank(),
            "account_holder_name", request.destinationName(),
            "account_number", request.destinationAccount(),
            "amount", request.amount().longValue(),
            "description", "Withdrawal " + request.referenceId()
        );
    }
    
    private String extractBankFromLabel(String labelCode) {
        if (labelCode.contains("BCA")) return "BCA";
        if (labelCode.contains("BNI")) return "BNI";
        if (labelCode.contains("BRI")) return "BRI";
        if (labelCode.contains("MANDIRI")) return "MANDIRI";
        if (labelCode.contains("BSI")) return "BSI";
        return "PERMATA";
    }
    
    private PaymentGatewayResponse mapToGatewayResponse(PaymentGatewayRequest request, Map<String, Object> response) {
        String vaNumber = (String) response.get("va_number");
        String transactionId = (String) response.get("transaction_id");
        String orderId = (String) response.get("order_id");
        
        if (transactionId == null) {
            transactionId = (String) response.get("id");
        }
        if (orderId == null) {
            orderId = (String) response.get("external_id");
        }
        
        // For VA, we don't have QRIS data, so return null
        return new PaymentGatewayResponse(
            transactionId,
            orderId,
            null,
            null,
            PaymentStatus.PROCESSING,
            Map.of(
                "va_number", vaNumber != null ? vaNumber : "",
                "bank_code", extractBankFromLabel(request.labelCode()),
                "expiry_time", String.valueOf(response.get("expiry_time"))
            )
        );
    }
    
    private PaymentGatewayResponse mapToQrisResponse(PaymentGatewayRequest request, Map<String, Object> response) {
        String qrString = (String) response.get("qr_string");
        if (qrString == null) {
            qrString = (String) response.get("qr_code");
        }
        String transactionId = (String) response.get("transaction_id");
        String orderId = (String) response.get("order_id");
        
        if (transactionId == null) {
            transactionId = (String) response.get("id");
        }
        
        // Xendit returns QRIS as a string (EMV format)
        return new PaymentGatewayResponse(
            transactionId,
            orderId,
            qrString,
            QrisDataType.STRING,
            PaymentStatus.PROCESSING,
            Map.of(
                "qr_string", qrString != null ? qrString : "",
                "expiry_time", String.valueOf(response.get("expiry_time"))
            )
        );
    }
    
    private WithdrawResponse mapToWithdrawResponse(Map<String, Object> response) {
        String transactionId = (String) response.get("id");
        String externalId = (String) response.get("external_id");
        String status = (String) response.get("status");
        
        return new WithdrawResponse(
            transactionId,
            externalId,
            mapStatus(status),
            response
        );
    }
    
    private PaymentStatus mapStatus(String xenditStatus) {
        if (xenditStatus == null) return PaymentStatus.PROCESSING;
        
        return switch (xenditStatus.toUpperCase()) {
            case "COMPLETED", "SETTLED", "PAID" -> PaymentStatus.COMPLETED;
            case "PENDING", "ACTIVE" -> PaymentStatus.PROCESSING;
            case "FAILED", "EXPIRED" -> PaymentStatus.FAILED;
            default -> PaymentStatus.PROCESSING;
        };
    }
}
