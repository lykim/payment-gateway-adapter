package com.wallet.adapter.paymentgateway.config;

import java.util.Map;

/**
 * Provider-specific configuration loaded from JSON.
 */
public record ProviderConfig(
    String providerId,
    String displayName,
    boolean sandbox,
    Map<String, String> credentials,
    Map<String, String> endpoints,
    Map<String, PaymentMethodConfig> paymentMethods,
    CallbackConfig callback,
    RateLimitConfig rateLimit,
    TimeoutConfig timeout
) {
    
    public String getCredential(String key) {
        return credentials != null ? credentials.get(key) : null;
    }
    
    public String getEndpoint(String key) {
        if (endpoints == null) return null;
        String endpoint = endpoints.get(key);
        if (endpoint != null && !endpoint.startsWith("http")) {
            return getBaseUrl() + endpoint;
        }
        return endpoint;
    }
    
    public String getBaseUrl() {
        return endpoints != null ? endpoints.get("baseUrl") : null;
    }
    
    public int getTimeoutMs() {
        return timeout != null ? timeout.readMs() : 30000;
    }
    
    public int getRateLimitPerSecond() {
        return rateLimit != null ? rateLimit.requestsPerSecond() : 100;
    }
    
    public int getRateLimitBurstSize() {
        return rateLimit != null ? rateLimit.burstSize() : 200;
    }
    
    public ProviderConfig withDefaults(ProvidersConfig.DefaultsConfig defaults) {
        if (defaults == null) return this;
        
        TimeoutConfig newTimeout = this.timeout;
        if (newTimeout == null) {
            newTimeout = new TimeoutConfig(5000, defaults.timeoutMs(), 10000);
        }
        
        RateLimitConfig newRateLimit = this.rateLimit;
        if (newRateLimit == null) {
            newRateLimit = new RateLimitConfig(defaults.rateLimitPerSecond(), defaults.rateLimitPerSecond() * 2);
        }
        
        return new ProviderConfig(
            providerId, displayName, sandbox, credentials, endpoints,
            paymentMethods, callback, newRateLimit, newTimeout
        );
    }
    
    public record PaymentMethodConfig(
        boolean enabled,
        String[] channels,
        int expiryMinutes,
        String acquirer,
        String type
    ) {}
    
    public record CallbackConfig(
        String signatureKey,
        String signatureAlgorithm,
        String verificationToken
    ) {}
    
    public record RateLimitConfig(
        int requestsPerSecond,
        int burstSize
    ) {}
    
    public record TimeoutConfig(
        int connectMs,
        int readMs,
        int writeMs
    ) {}
}
