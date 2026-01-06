package com.wallet.adapter.paymentgateway.support;

/**
 * Exception thrown when provider operation times out.
 */
public class ProviderTimeoutException extends RuntimeException {
    
    public ProviderTimeoutException(String message) {
        super(message);
    }
    
    public ProviderTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
