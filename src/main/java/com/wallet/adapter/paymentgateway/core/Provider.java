package com.wallet.adapter.paymentgateway.core;

import java.lang.annotation.*;

/**
 * Marks a class as a Payment Gateway Provider.
 * Used for auto-discovery and registration.
 * 
 * To add a new provider:
 * 1. Implement PaymentGatewayProvider interface
 * 2. Annotate with @Provider("provider-id")
 * 3. Create config/[provider-id].json
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Provider {
    /**
     * Provider ID, must match config file name (e.g., "midtrans" -> config/midtrans.json)
     */
    String value();
    
    /**
     * Priority for provider selection (higher = preferred)
     */
    int priority() default 0;
}
