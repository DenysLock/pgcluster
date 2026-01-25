package com.pgcluster.api.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocationDto {
    private String id;          // "fsn1"
    private String name;        // "Falkenstein"
    private String city;        // "Falkenstein"
    private String country;     // "DE"
    private String countryName; // "Germany"
    private String flag;        // "🇩🇪"
    private boolean available;  // true if server type is available at this location

    // Country code to flag emoji mapping
    public static String getFlag(String countryCode) {
        if (countryCode == null || countryCode.length() != 2) {
            return "🌍";
        }
        // Convert country code to regional indicator symbols
        int firstChar = Character.codePointAt(countryCode.toUpperCase(), 0) - 'A' + 0x1F1E6;
        int secondChar = Character.codePointAt(countryCode.toUpperCase(), 1) - 'A' + 0x1F1E6;
        return new String(Character.toChars(firstChar)) + new String(Character.toChars(secondChar));
    }

    // Country code to name mapping
    public static String getCountryName(String countryCode) {
        return switch (countryCode) {
            case "DE" -> "Germany";
            case "FI" -> "Finland";
            case "US" -> "United States";
            case "SG" -> "Singapore";
            case "NL" -> "Netherlands";
            case "GB" -> "United Kingdom";
            default -> countryCode;
        };
    }

    // Hetzner location code to country code mapping
    public static String getCountryCodeForLocation(String location) {
        if (location == null) return null;
        return switch (location.toLowerCase()) {
            case "fsn1", "nbg1" -> "DE";  // Germany
            case "hel1" -> "FI";           // Finland
            case "ash", "hil" -> "US";     // USA
            case "sin" -> "SG";            // Singapore
            default -> null;
        };
    }

    // Get flag emoji directly from location code
    public static String getFlagForLocation(String location) {
        String countryCode = getCountryCodeForLocation(location);
        return getFlag(countryCode);
    }
}
