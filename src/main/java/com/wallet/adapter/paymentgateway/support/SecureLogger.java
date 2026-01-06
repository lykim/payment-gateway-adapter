package com.wallet.adapter.paymentgateway.support;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logger that automatically masks sensitive data.
 */
public class SecureLogger {
    
    private final Logger logger;
    private final SensitiveDataMasker masker;
    
    public SecureLogger(String name) {
        this.logger = Logger.getLogger(name);
        this.masker = new SensitiveDataMasker();
    }
    
    public void info(String message, Object... args) {
        log(Level.INFO, message, args);
    }
    
    public void debug(String message, Object... args) {
        log(Level.FINE, message, args);
    }
    
    public void warn(String message, Object... args) {
        log(Level.WARNING, message, args);
    }
    
    public void error(String message, Throwable t, Object... args) {
        String formatted = formatMessage(message, args);
        String masked = masker.mask(formatted);
        logger.log(Level.SEVERE, "[" + Instant.now() + "] " + masked, t);
    }
    
    public void request(String providerId, String operation, Object request) {
        info("[%s] %s REQUEST: %s", providerId, operation, masker.maskObject(request));
    }
    
    public void response(String providerId, String operation, Object response, long durationMs) {
        info("[%s] %s RESPONSE (%dms): %s", providerId, operation, durationMs, masker.maskObject(response));
    }
    
    private void log(Level level, String message, Object... args) {
        if (logger.isLoggable(level)) {
            String formatted = formatMessage(message, args);
            String masked = masker.mask(formatted);
            logger.log(level, "[" + Instant.now() + "] " + masked);
        }
    }
    
    private String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        try {
            return String.format(message, args);
        } catch (Exception e) {
            return message + " [format error: " + e.getMessage() + "]";
        }
    }
}
