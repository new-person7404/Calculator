package com.example.calculator.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NumberFormatterTest {

    @Test
    void plainFormat() {
        assertEquals("0", NumberFormatter.format(0));
        assertEquals("0", NumberFormatter.format(-0.0));
        assertEquals("42", NumberFormatter.format(42));
        assertEquals("-1.5", NumberFormatter.format(-1.5));
        assertEquals("0.3", NumberFormatter.format(0.1 + 0.2));
        assertEquals("0.333333333333", NumberFormatter.format(1.0 / 3));
        assertEquals("999999999999", NumberFormatter.format(999999999999.0));
    }

    @Test
    void tooManyIntegerDigitsSwitchesToScientific() {
        assertEquals("1.23456789012E14", NumberFormatter.format(123456789012345.0));
        assertEquals("1E12", NumberFormatter.format(1e12));
    }

    @Test
    void scientificFormatForVeryLargeAndSmall() {
        assertEquals("1E15", NumberFormatter.format(1e15));
        assertEquals("6.02214076E23", NumberFormatter.format(6.02214076e23));
        assertEquals("1.5E-10", NumberFormatter.format(1.5e-10));
        assertEquals("0.000000001", NumberFormatter.format(1e-9));
    }

    @Test
    void displayFormatGroupsThousandsAndUsesMinusSign() {
        assertEquals("1,234,567.89", NumberFormatter.toDisplay(1234567.89));
        assertEquals("−1,000", NumberFormatter.toDisplay(-1000));
        assertEquals("999", NumberFormatter.toDisplay(999));
        assertEquals("1.5E−10", NumberFormatter.toDisplay(1.5e-10));
        assertEquals("−2E20", NumberFormatter.toDisplay(-2e20));
    }
}
