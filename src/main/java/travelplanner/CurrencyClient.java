package travelplanner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Converts INR amounts into the destination's local currency for DISPLAY only
 * (e.g. "≈ €12"). All planning maths stays in INR. Failure = equivalents are hidden.
 */
public class CurrencyClient {

    private static final String RATES_URL = "https://open.er-api.com/v6/latest/INR";

    /** How many units of the target currency 1 INR buys, or 0 if unavailable. */
    public double getRateFromInr(String currencyCode) {
        if (currencyCode == null || currencyCode.equals("INR")) return 0;
        String cacheKey = "fx_inr_" + LocalDate.now();   // refresh once per day
        try {
            String json = CacheManager.get(cacheKey);
            if (json == null) {
                json = HttpUtil.get(RATES_URL, null, 8);
                CacheManager.put(cacheKey, json);
            }
            JsonObject rates = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("rates");
            if (rates == null || !rates.has(currencyCode)) return 0;
            return rates.get(currencyCode).getAsDouble();
        } catch (IOException | RuntimeException e) {
            return 0;   // silent: the UI just won't show local-currency equivalents
        }
    }

    /** Country code -> currency code (only common travel countries; others show INR only). */
    public static String currencyForCountry(String countryCode) {
        switch (countryCode) {
            case "FR": case "DE": case "IT": case "ES": case "NL": case "BE": case "AT":
            case "PT": case "GR": case "IE": case "FI": case "LU":
                return "EUR";
            case "GB": return "GBP";
            case "US": return "USD";
            case "CA": return "CAD";
            case "AU": return "AUD";
            case "JP": return "JPY";
            case "CH": return "CHF";
            case "AE": return "AED";
            case "SG": return "SGD";
            case "TH": return "THB";
            case "MY": return "MYR";
            case "ID": return "IDR";
            case "LK": return "LKR";
            case "NP": return "NPR";
            case "VN": return "VND";
            default: return "INR";
        }
    }

    public static String symbolFor(String currencyCode) {
        switch (currencyCode) {
            case "EUR": return "€";
            case "GBP": return "£";
            case "USD": return "$";
            case "JPY": return "¥";
            case "INR": return "₹";
            default: return currencyCode + " ";
        }
    }
}
