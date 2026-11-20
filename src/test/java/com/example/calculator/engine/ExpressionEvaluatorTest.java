package com.example.calculator.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExpressionEvaluatorTest {

    private static String eval(String expression) throws CalculationException {
        return eval(expression, AngleUnit.DEGREES);
    }

    private static String eval(String expression, AngleUnit angle) throws CalculationException {
        double value = ExpressionEvaluator.evaluate(expression, new ExpressionEvaluator.Context(angle, 42, () -> 0.5));
        return NumberFormatter.format(value);
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(delimiter = '=', value = {
            // arithmetic and precedence
            "1+2×3          = 7",
            "(1+2)×3        = 9",
            "10−4−3         = 3",
            "8÷4÷2          = 1",
            "0.1+0.2        = 0.3",
            "2^3^2          = 512",
            "−2^2           = -4",
            "2^−1           = 0.5",
            "5−−3           = 8",
            // implicit multiplication
            "2π             = 6.28318530718",
            "3(4)           = 12",
            "(2)(3)         = 6",
            "2sin(30)       = 1",
            "2√9            = 6",
            // postfix
            "5!             = 120",
            "0!             = 1",
            "50%            = 0.5",
            "3²             = 9",
            "2³             = 8",
            "4⁻¹            = 0.25",
            "(1+2)²         = 9",
            // roots and logs
            "√16            = 4",
            "√9+1           = 4",
            "∛27            = 3",
            "8 yroot 3      = 2",
            "−8 yroot 3     = -2",
            "ln(e)          = 1",
            "log(1000)      = 3",
            "log₂(8)        = 3",
            "log(8,2)       = 3",
            // trigonometry in degrees, exact at special angles
            "sin(30)        = 0.5",
            "sin(180)       = 0",
            "cos(90)        = 0",
            "cos(60)        = 0.5",
            "tan(45)        = 1",
            "sin⁻¹(1)       = 90",
            "tan⁻¹(1)       = 45",
            // combinatorics and integers
            "5 P 2          = 20",
            "5 C 2          = 10",
            "52 C 5         = 2598960",
            "7 mod 3        = 1",
            "−7 mod 3       = 2",
            "gcd(12,18)     = 6",
            "lcm(4,6)       = 12",
            "abs(−3)        = 3",
            "floor(2.7)     = 2",
            "ceil(2.1)      = 3",
            "round(2.5)     = 3",
            "round(−2.5)    = -3",
            // hyperbolic
            "sinh(0)        = 0",
            "cosh(0)        = 1",
            "tanh⁻¹(0)      = 0",
            // constants, scientific notation, auto-closing
            "Ans+1          = 43",
            "rand           = 0.5",
            "3E8            = 300000000",
            "1.5E−3         = 0.0015",
            "sin(30         = 0.5",
            "((2+3          = 5",
            // ASCII spellings, as when pasting
            "2*3-1          = 5",
            "10/4           = 2.5",
            "sqrt(16)+pi-pi = 4",
            "asin(1)        = 90",
    })
    void evaluates(String expression, String expected) throws CalculationException {
        assertEquals(expected, eval(expression.strip()));
    }

    @Test
    void radiansAndGradians() throws CalculationException {
        assertEquals("0", eval("sin(π)", AngleUnit.RADIANS));
        assertEquals("-1", eval("cos(π)", AngleUnit.RADIANS));
        assertEquals("1", eval("sin(100)", AngleUnit.GRADIANS));
        assertEquals("1.57079632679", eval("sin⁻¹(1)", AngleUnit.RADIANS));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '=', value = {
            "1÷0       = Can't divide by zero",
            "5 mod 0   = Can't divide by zero",
            "0⁻¹       = Can't divide by zero",
            "√(−1)     = Invalid input",
            "ln(0)     = Invalid input",
            "tan(90)   = Invalid input",
            "sin⁻¹(2)  = Invalid input",
            "2.5!      = Invalid input",
            "(−1)!     = Invalid input",
            "171!      = Overflow",
            "10^400    = Overflow",
            "2 C 5     = Invalid input",
            "(−8)^0.5  = Invalid input",
            "5+        = Syntax error",
            "×5        = Syntax error",
            "2)        = Syntax error",
            "''        = Syntax error",
            "1..2      = Syntax error",
            "sin       = Syntax error",
            "hello     = Syntax error",
    })
    void reportsErrors(String expression, String message) {
        CalculationException e = assertThrows(CalculationException.class, () -> eval(expression.strip()));
        assertEquals(message, e.getMessage());
    }
}
