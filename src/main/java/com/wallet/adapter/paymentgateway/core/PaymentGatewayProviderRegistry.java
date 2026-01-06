package com.wallet.adapter.paymentgateway.core;

import com.wallet.adapter.paymentgateway.config.ProviderConfig;
import com.wallet.adapter.paymentgateway.config.ProviderConfigLoader;
import com.wallet.adapter.paymentgateway.config.ProvidersConfig;
import com.wallet.adapter.paymentgateway.support.SecureLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for Payment Gateway providers.
 * Handles provider discovery, initialization, and lifecycle management.
 * 
 * Implements plugin-based architecture for Open/Closed Principle.
 */
public class PaymentGatewayProviderRegistry {
    
    private final Map<String, PaymentGatewayProvider> providerById = new ConcurrentHashMap<>();
    private final Map<String, PaymentGatewayProvider> providerByLabelCode = new ConcurrentHashMap<>();
    private final ProviderConfigLoader configLoader;
    private final SecureLogger logger;
    private boolean initialized = false;
    
    public PaymentGatewayProviderRegistry(ProviderConfigLoader configLoader) {
        this.configLoader = configLoader;
        this.logger = new SecureLogger(PaymentGatewayProviderRegistry.class.getName());
    }
    
    /**
     * Register a provider instance
     */
    public void register(PaymentGatewayProvider provider) {
        String providerId = provider.getProviderId();
        
        if (providerById.containsKey(providerId)) {
            logger.warn("Provider already registered: %s, replacing...", providerId);
        }
        
        providerById.put(providerId, provider);
        logger.info("Registered provider: %s (%s)", providerId, provider.getDisplayName());
    }
    
    /**
     * Initialize all registered providers with their configurations
     */
    public void initialize() {
        if (initialized) {
            logger.warn("Registry already initialized");
            return;
        }
        
        ProvidersConfig mainConfig = configLoader.loadProvidersConfig();
        
        for (ProvidersConfig.ProviderEntry entry : mainConfig.providers()) {
            if (!entry.enabled()) {
                logger.info("Provider %s is disabled, skipping", entry.id());
                continue;
            }
            
            PaymentGatewayProvider provider = providerById.get(entry.id());
            if (provider == null) {
                logger.warn("Provider %s configured but not registered", entry.id());
                continue;
            }
            
            try {
                // Load provider-specific config
                ProviderConfig providerConfig = configLoader.loadProviderConfig(entry.id());
                
                // Apply main config defaults
                providerConfig = providerConfig.withDefaults(mainConfig.defaults());
                
                // Register label codes
                provider.registerLabelCodes(entry.labelCodes());
                
                // Map label codes to provider
                for (String labelCode : entry.labelCodes()) {
                    providerByLabelCode.put(labelCode, provider);
                }
                
                // Initialize provider
                provider.initialize(providerConfig);
                
                logger.info("Initialized provider %s with %d label codes", 
                    entry.id(), entry.labelCodes().size());
                
            } catch (Exception e) {
                logger.error("Failed to initialize provider: %s", e, entry.id());
            }
        }
        
        initialized = true;
        logger.info("Registry initialized with %d providers, %d label codes",
            providerById.size(), providerByLabelCode.size());
    }
    
    /**
     * Get provider by label code
     */
    public Optional<PaymentGatewayProvider> getProviderByLabelCode(String labelCode) {
        return Optional.ofNullable(providerByLabelCode.get(labelCode));
    }
    
    /**
     * Get provider by ID
     */
    public Optional<PaymentGatewayProvider> getProviderById(String providerId) {
        return Optional.ofNullable(providerById.get(providerId));
    }
    
    /**
     * Get all registered providers
     */
    public Collection<PaymentGatewayProvider> getAllProviders() {
        return Collections.unmodifiableCollection(providerById.values());
    }
    
    /**
     * Get all registered label codes
     */
    public Set<String> getAllLabelCodes() {
        return Collections.unmodifiableSet(providerByLabelCode.keySet());
    }
    
    /**
     * Check if a label code is supported
     */
    public boolean supportsLabelCode(String labelCode) {
        return providerByLabelCode.containsKey(labelCode);
    }
    
    /**
     * Shutdown all providers
     */
    public void shutdown() {
        logger.info("Shutting down registry...");
        
        for (PaymentGatewayProvider provider : providerById.values()) {
            try {
                provider.shutdown();
            } catch (Exception e) {
                logger.error("Error shutting down provider: %s", e, provider.getProviderId());
            }
        }
        
        providerById.clear();
        providerByLabelCode.clear();
        initialized = false;
        
        logger.info("Registry shutdown complete");
    }
}
