package com.wallet.adapter.paymentgateway.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.adapter.paymentgateway.support.SecureLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads provider configurations from JSON files.
 */
public class ProviderConfigLoader {
    
    private static final String CONFIG_DIR = "config";
    private static final String PROVIDERS_CONFIG_FILE = "providers.json";
    
    private final ObjectMapper objectMapper;
    private final Path configPath;
    private final SecureLogger logger;
    
    public ProviderConfigLoader() {
        this(Path.of(CONFIG_DIR));
    }
    
    public ProviderConfigLoader(Path configPath) {
        this.configPath = configPath;
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.logger = new SecureLogger(ProviderConfigLoader.class.getName());
    }
    
    /**
     * Load main providers.json configuration
     */
    public ProvidersConfig loadProvidersConfig() {
        try {
            Path filePath = configPath.resolve(PROVIDERS_CONFIG_FILE);
            
            // Try file system first
            if (Files.exists(filePath)) {
                logger.info("Loading providers config from: %s", filePath);
                return objectMapper.readValue(filePath.toFile(), ProvidersConfig.class);
            }
            
            // Try classpath
            try (InputStream is = getClass().getClassLoader()
                    .getResourceAsStream(CONFIG_DIR + "/" + PROVIDERS_CONFIG_FILE)) {
                if (is != null) {
                    logger.info("Loading providers config from classpath");
                    return objectMapper.readValue(is, ProvidersConfig.class);
                }
            }
            
            throw new RuntimeException("providers.json not found in " + configPath + " or classpath");
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to load providers.json", e);
        }
    }
    
    /**
     * Load provider-specific configuration (e.g., midtrans.json)
     */
    public ProviderConfig loadProviderConfig(String providerId) {
        String fileName = providerId + ".json";
        
        try {
            Path filePath = configPath.resolve(fileName);
            
            // Try file system first
            if (Files.exists(filePath)) {
                logger.info("Loading provider config from: %s", filePath);
                return objectMapper.readValue(filePath.toFile(), ProviderConfig.class);
            }
            
            // Try classpath
            try (InputStream is = getClass().getClassLoader()
                    .getResourceAsStream(CONFIG_DIR + "/" + fileName)) {
                if (is != null) {
                    logger.info("Loading provider config from classpath: %s", fileName);
                    return objectMapper.readValue(is, ProviderConfig.class);
                }
            }
            
            throw new RuntimeException(fileName + " not found in " + configPath + " or classpath");
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + fileName, e);
        }
    }
}
