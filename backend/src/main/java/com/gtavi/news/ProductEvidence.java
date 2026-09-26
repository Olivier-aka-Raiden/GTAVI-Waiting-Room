package com.gtavi.news;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.regex.Pattern;

/** Match price evidence without borrowing another product's number or guessing a currency. */
final class ProductEvidence {
    private ProductEvidence() {}
    private static final Pattern NUMBERS = Pattern.compile(
        "(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+|['’\\u00a0\\u202f ]\\d{3})*(?![\\p{L}\\p{N}])");

    static boolean price(String evidence, Double price, String currency) {
        if (evidence == null || price == null || !Double.isFinite(price) || price <= 0
                || currency == null || !currency.matches("[A-Z]{3}")) return false;
        try { Currency.getInstance(currency); } catch (IllegalArgumentException invalid) { return false; }
        if (!Pattern.compile("(?<![A-Za-z])" + currency + "(?![A-Za-z])").matcher(evidence).find()) return false;
        var matches = NUMBERS.matcher(evidence);
        while (matches.find()) {
            String number = matches.group().replaceAll("['’\\u00a0\\u202f ]", "");
            int separator = Math.max(number.lastIndexOf('.'), number.lastIndexOf(','));
            int decimals = separator < 0 ? 0 : number.length() - separator - 1;
            if (decimals == 1 || decimals == 2) {
                number = number.substring(0, separator).replace(".", "").replace(",", "")
                    + "." + number.substring(separator + 1);
            } else number = number.replace(".", "").replace(",", "");
            try {
                if (new BigDecimal(number).compareTo(BigDecimal.valueOf(price)) == 0) return true;
            } catch (NumberFormatException ignored) { }
        }
        return false;
    }
}
