package com.wallet.adapter.paymentgateway.config;

import java.util.List;
import java.util.Set;

/**
 * Main providers configuration (providers.json)
 */
public record ProvidersConfig(
    List<ProviderEntry> providers,
    DefaultsConfig defaults
) {
    
    public record ProviderEntry(
        String id,
        boolean enabled,
        Set<String> labelCodes,
        int priority
    ) {}
    
    public record DefaultsConfig(
        int timeoutMs,
        int maxRetries,
        int rateLimitPerSecond
    ) {
        public static DefaultsConfig defaultConfig() {
            return new DefaultsConfig(30000, 3, 100);
        }
    }
}
