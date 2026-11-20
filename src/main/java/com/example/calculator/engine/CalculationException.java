package com.example.calculator.engine;

/** Thrown when an expression can't be evaluated. The message is shown to the user as-is. */
public class CalculationException extends Exception {

    public CalculationException(String message) {
        super(message);
    }

    static CalculationException syntax() {
        return new CalculationException("Syntax error");
    }

    static CalculationException invalidInput() {
        return new CalculationException("Invalid input");
    }

    static CalculationException divideByZero() {
        return new CalculationException("Can't divide by zero");
    }

    static CalculationException overflow() {
        return new CalculationException("Overflow");
    }
}
