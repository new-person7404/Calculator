package com.example.calculator.engine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Turns doubles into text for the display, hiding floating-point noise such as 0.1 + 0.2 = 0.30000000000000004. */
public final class NumberFormatter {

    /** Digits shown on the display. Rounding to 12 also absorbs binary rounding errors. */
    public static final int DISPLAY_DIGITS = 12;

    private NumberFormatter() {
    }

    /** Plain ASCII form ("-1234.5", "1.5E-20"): safe to copy, paste and parse. */
    public static String format(double value) {
        return format(value, DISPLAY_DIGITS);
    }

    public static String format(double value, int significantDigits) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Not a finite number: " + value);
        }
        if (value == 0) {
            return "0"; // also turns −0 into 0
        }
        BigDecimal rounded = new BigDecimal(value)
                .round(new MathContext(significantDigits, RoundingMode.HALF_EVEN))
                .stripTrailingZeros();
        int exponent = rounded.precision() - rounded.scale() - 1;
        // Use scientific notation when the integer part has more digits than we show, so nothing is
        // silently rounded to zeros (123456789012345 → 1.23456789012E14), and for tiny numbers.
        if (exponent >= significantDigits || exponent <= -10) {
            String mantissa = rounded.movePointLeft(exponent).stripTrailingZeros().toPlainString();
            return mantissa + "E" + exponent;
        }
        return rounded.toPlainString();
    }

    /** Display form: thousands separators and a real minus sign ("−1,234.5"). */
    public static String toDisplay(double value) {
        String plain = format(value);
        boolean negative = plain.startsWith("-");
        if (negative) {
            plain = plain.substring(1);
        }
        String body;
        if (plain.contains("E")) {
            body = plain.replace('-', '−');
        } else {
            int dot = plain.indexOf('.');
            String integerPart = dot < 0 ? plain : plain.substring(0, dot);
            String fraction = dot < 0 ? "" : plain.substring(dot);
            body = group(integerPart) + fraction;
        }
        return negative ? "−" + body : body;
    }

    private static String group(String digits) {
        StringBuilder out = new StringBuilder();
        int firstGroup = digits.length() % 3 == 0 ? 3 : digits.length() % 3;
        out.append(digits, 0, firstGroup);
        for (int i = firstGroup; i < digits.length(); i += 3) {
            out.append(',').append(digits, i, i + 3);
        }
        return out.toString();
    }
}
