package com.example.calculator.convert;

import java.util.function.DoubleUnaryOperator;

/**
 * A unit of measure. Every unit converts to and from its category's base unit
 * (metre, kilogram, kelvin …); converting A → B goes A → base → B.
 */
public record Unit(String name, String symbol, DoubleUnaryOperator toBase, DoubleUnaryOperator fromBase) {

    /** A unit that is a fixed multiple of the base unit: {@code 1 unit = factor × base}. */
    public static Unit linear(String name, String symbol, double factor) {
        return new Unit(name, symbol, v -> v * factor, v -> v / factor);
    }

    @Override
    public String toString() {
        return name + " (" + symbol + ")";
    }
}
