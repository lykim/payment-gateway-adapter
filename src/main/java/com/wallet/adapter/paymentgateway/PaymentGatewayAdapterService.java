package com.wallet.adapter.paymentgateway;

import com.wallet.adapter.paymentgateway.config.ProviderConfigLoader;
import com.wallet.adapter.paymentgateway.core.PaymentGatewayProvider;
import com.wallet.adapter.paymentgateway.core.PaymentGatewayProviderRegistry;
import com.wallet.adapter.paymentgateway.provider.midtrans.MidtransProvider;
import com.wallet.adapter.paymentgateway.provider.xendit.XenditProvider;
import com.wallet.adapter.paymentgateway.support.SecureLogger;
import com.wallet.core.application.port.outbound.service.PaymentGatewayService;
import com.wallet.core.domain.PaymentMethod;
import com.wallet.core.domain.PaymentStatus;

import java.nio.file.Path;

/**
 * Main adapter service that implements PaymentGatewayService from core.
 * Routes requests to appropriate providers based on labelCode.
 */
public class PaymentGatewayAdapterService implements PaymentGatewayService {
    
    private final PaymentGatewayProviderRegistry registry;
    private final SecureLogger logger;
    
    public PaymentGatewayAdapterService() {
        this(Path.of("config"));
    }
    
    public PaymentGatewayAdapterService(Path configPath) {
        this.logger = new SecureLogger(PaymentGatewayAdapterService.class.getName());
        
        // Initialize config loader
        ProviderConfigLoader configLoader = new ProviderConfigLoader(configPath);
        
        // Initialize registry
        this.registry = new PaymentGatewayProviderRegistry(configLoader);
        
        // Register all available providers (OCP: add new providers here)
        registerProviders();
        
        // Initialize all providers with their configurations
        this.registry.initialize();
        
        logger.info("PaymentGatewayAdapterService initialized");
    }
    
    /**
     * Register all available providers.
     * To add a new provider following OCP:
     * 1. Create new provider class implementing PaymentGatewayProvider
     * 2. Add provider instance here
     * 3. Create config/[provider-id].json
     * 4. Update config/providers.json with label codes
     */
    private void registerProviders() {
        registry.register(new MidtransProvider());
        registry.register(new XenditProvider());
        // Add new providers here...
    }
    
    @Override
    public PaymentGatewayResponse initiatePayment(PaymentGatewayRequest request) {
        PaymentGatewayProvider provider = getProviderForLabel(request.labelCode());
        
        // Determine payment method from request or label code
        PaymentMethod method = determinePaymentMethod(request.labelCode());
        
        return switch (method) {
            case VIRTUAL_ACCOUNT -> provider.createVirtualAccount(request);
            case QRIS -> provider.createQris(request);
            default -> throw new UnsupportedOperationException(
                "Payment method not supported: " + method
            );
        };
    }
    
    @Override
    public WithdrawResponse initiateWithdraw(WithdrawRequest request) {
        PaymentGatewayProvider provider = getProviderForLabel(request.labelCode());
        return provider.processWithdraw(request);
    }
    
    @Override
    public boolean verifyPayment(PaymentGatewayCallback callback) {
        PaymentGatewayProvider provider = getProviderForLabel(callback.labelCode());
        return provider.verifyCallback(callback);
    }
    
    @Override
    public PaymentStatus checkStatus(String labelCode, String externalReference) {
        PaymentGatewayProvider provider = getProviderForLabel(labelCode);
        return provider.checkTransactionStatus(externalReference);
    }
    
    @Override
    public InquiryResponse inquiry(InquiryRequest request) {
        PaymentGatewayProvider provider = getProviderForLabel(request.labelCode());
        return provider.inquiryAccount(request);
    }
    
    /**
     * Shutdown all providers gracefully
     */
    public void shutdown() {
        logger.info("Shutting down PaymentGatewayAdapterService...");
        registry.shutdown();
    }
    
    /**
     * Get provider for a given label code
     */
    private PaymentGatewayProvider getProviderForLabel(String labelCode) {
        return registry.getProviderByLabelCode(labelCode)
            .orElseThrow(() -> new IllegalArgumentException(
                "No provider found for label code: " + labelCode
            ));
    }
    
    /**
     * Determine payment method from label code
     */
    private PaymentMethod determinePaymentMethod(String labelCode) {
        String upper = labelCode.toUpperCase();
        if (upper.contains("QRIS")) {
            return PaymentMethod.QRIS;
        }
        if (upper.contains("VA") || upper.contains("BCA") || upper.contains("BNI") 
            || upper.contains("BRI") || upper.contains("MANDIRI") || upper.contains("BSI")) {
            return PaymentMethod.VIRTUAL_ACCOUNT;
        }
        // Default to VA if not determined
        return PaymentMethod.VIRTUAL_ACCOUNT;
    }
    
    /**
     * Check if a label code is supported
     */
    public boolean supportsLabelCode(String labelCode) {
        return registry.supportsLabelCode(labelCode);
    }
}
