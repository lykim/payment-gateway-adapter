package com.wallet.adapter.paymentgateway.support;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Masks sensitive data in logs and responses.
 */
public class SensitiveDataMasker {
    
    private static final List<String> FULL_MASK_FIELDS = List.of(
        "serverKey", "clientKey", "apiKey", "secretKey", 
        "signatureKey", "password", "token", "secret",
        "callbackToken", "verificationToken", "authorization",
        "x-api-key", "x-signature", "signature_key"
    );
    
    private static final List<String> PARTIAL_MASK_FIELDS = List.of(
        "accountNumber", "virtualAccountNumber", "bankAccount",
        "cardNumber", "phoneNumber", "va_number", "account_number"
    );
    
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("\\b\\d{13,19}\\b");
    private static final Pattern API_KEY_PATTERN = Pattern.compile("\\b[A-Za-z0-9_-]{20,}\\b");
    
    public String mask(String input) {
        if (input == null) return null;
        
        String result = input;
        
        // Mask full fields (JSON style)
        for (String field : FULL_MASK_FIELDS) {
            result = result.replaceAll(
                "(?i)(\"" + field + "\"\\s*:\\s*\")[^\"]+(\"|,|})",
                "$1****$2"
            );
            // Also handle non-JSON formats
            result = result.replaceAll(
                "(?i)(" + field + "\\s*[=:]\\s*)[^\\s,}\"]+",
                "$1****"
            );
        }
        
        // Mask partial fields (keep last 4)
        for (String field : PARTIAL_MASK_FIELDS) {
            result = result.replaceAll(
                "(?i)(\"" + field + "\"\\s*:\\s*\")([^\"]*?)(.{0,4})(\")",
                "$1****$3$4"
            );
        }
        
        // Mask potential card numbers
        result = CARD_NUMBER_PATTERN.matcher(result).replaceAll(match -> {
            String num = match.group();
            if (num.length() >= 4) {
                return "****" + num.substring(num.length() - 4);
            }
            return "****";
        });
        
        return result;
    }
    
    public String maskObject(Object obj) {
        if (obj == null) return "null";
        return mask(obj.toString());
    }
    
    public String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
    
    public String maskApiKey(String apiKey) {
        return "****";
    }
}
