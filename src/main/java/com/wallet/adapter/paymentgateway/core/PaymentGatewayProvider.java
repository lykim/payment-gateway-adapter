package com.wallet.adapter.paymentgateway.core;

import com.wallet.adapter.paymentgateway.config.ProviderConfig;
import com.wallet.core.application.port.outbound.service.PaymentGatewayService.*;
import com.wallet.core.domain.PaymentMethod;
import com.wallet.core.domain.PaymentStatus;

import java.util.Set;

/**
 * Service Provider Interface for Payment Gateway providers.
 * 
 * Implementing Open/Closed Principle:
 * - Open for extension: New providers can be added by implementing this interface
 * - Closed for modification: No changes to existing code needed when adding providers
 * 
 * To add a new provider:
 * 1. Implement this interface
 * 2. Annotate with @Provider("provider-id")
 * 3. Create config/[provider-id].json
 */
public interface PaymentGatewayProvider {
    
    /**
     * Unique provider identifier (e.g., "midtrans", "xendit")
     * Must match config file name: config/[providerId].json
     */
    String getProviderId();
    
    /**
     * Display name for this provider
     */
    String getDisplayName();
    
    /**
     * Payment methods supported by this provider
     */
    Set<PaymentMethod> getSupportedMethods();
    
    /**
     * Check if this provider handles the given label code
     */
    boolean supports(String labelCode);
    
    /**
     * Register label codes this provider will handle
     */
    void registerLabelCodes(Set<String> labelCodes);
    
    /**
     * Initialize provider with configuration
     */
    void initialize(ProviderConfig config);
    
    /**
     * Create Virtual Account for deposit
     */
    PaymentGatewayResponse createVirtualAccount(PaymentGatewayRequest request);
    
    /**
     * Create QRIS for deposit
     */
    PaymentGatewayResponse createQris(PaymentGatewayRequest request);
    
    /**
     * Process withdraw/disbursement
     */
    WithdrawResponse processWithdraw(WithdrawRequest request);
    
    /**
     * Verify callback signature
     */
    boolean verifyCallback(PaymentGatewayCallback callback);
    
    /**
     * Check transaction status
     */
    PaymentStatus checkTransactionStatus(String externalReference);
    
    /**
     * Inquiry bank account
     */
    InquiryResponse inquiryAccount(InquiryRequest request);
    
    /**
     * Health check
     */
    boolean isHealthy();
    
    /**
     * Shutdown/cleanup
     */
    void shutdown();
}
