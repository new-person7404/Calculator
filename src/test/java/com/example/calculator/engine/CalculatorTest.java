package com.example.calculator.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.calculator.engine.Calculator.ResultState;
import org.junit.jupiter.api.Test;

/** Simulates button presses. */
class CalculatorTest {

    private final Calculator calc = new Calculator(() -> 0.5);

    private void type(String keys) {
        for (char c : keys.toCharArray()) {
            switch (c) {
                case '+' -> calc.operator("+");
                case '-' -> calc.operator("−");
                case '*' -> calc.operator("×");
                case '/' -> calc.operator("÷");
                case '^' -> calc.operator("^");
                case '.' -> calc.decimalPoint();
                case '(' -> calc.openParenthesis();
                case ')' -> calc.closeParenthesis();
                case '=' -> calc.evaluate();
                case '~' -> calc.negate();
                case '<' -> calc.backspace();
                default -> calc.digit(c - '0');
            }
        }
    }

    @Test
    void startsAtZero() {
        assertEquals("", calc.expressionText());
        assertEquals("0", calc.resultText());
    }

    @Test
    void basicArithmeticWithPrecedence() {
        type("12+3*4=");
        assertEquals("12+3×4 =", calc.expressionText());
        assertEquals("24", calc.resultText());
        assertEquals(ResultState.FINAL, calc.resultState());
    }

    @Test
    void showsLivePreviewWhileTyping() {
        type("7*6");
        assertEquals("42", calc.resultText());
        assertEquals(ResultState.PREVIEW, calc.resultState());
        type("*");
        assertEquals("", calc.resultText()); // incomplete
    }

    @Test
    void operatorAfterResultContinuesFromAnswer() {
        type("2+3=");
        type("*2");
        assertEquals("Ans×2", calc.expressionText());
        type("=");
        assertEquals("10", calc.resultText());
    }

    @Test
    void digitAfterResultStartsOver() {
        type("2+3=");
        type("7");
        assertEquals("7", calc.expressionText());
    }

    @Test
    void pressingAnotherOperatorReplacesThePreviousOne() {
        type("5+*");
        assertEquals("5×", calc.expressionText());
        type("-");
        assertEquals("5×−", calc.expressionText()); // minus after × means a negative number
        type("3=");
        assertEquals("−15", calc.resultText());
    }

    @Test
    void noLeadingZerosOrDoubleDecimalPoints() {
        type("0");
        type("0");
        type("5");
        assertEquals("5", calc.expressionText());
        type("..2.");
        assertEquals("5.2", calc.expressionText());
        calc.clearAll();
        type(".5");
        assertEquals("0.5", calc.expressionText());
    }

    @Test
    void negateFlipsLastNumber() {
        type("5");
        type("~");
        assertEquals("−5", calc.expressionText());
        type("~");
        assertEquals("5", calc.expressionText());
        calc.clearAll();
        type("8-3~");
        assertEquals("8+3", calc.expressionText());
        calc.clearAll();
        type("2*3~");
        assertEquals("2×−3", calc.expressionText());
    }

    @Test
    void negateKeepsImplicitMultiplication() {
        type("2(3)~");
        assertEquals("2(−(3))", calc.expressionText());
        type("=");
        assertEquals("−6", calc.resultText());
    }

    @Test
    void functionsWrapTheNumberJustTyped() {
        type("30");
        calc.function("sin");
        assertEquals("sin(30)", calc.expressionText());
        assertEquals("0.5", calc.resultText());

        calc.clearAll();
        type("9");
        calc.root("√");
        assertEquals("√(9)", calc.expressionText());

        calc.clearAll();
        type("2+(1+2)");
        calc.function("ln");
        assertEquals("2+ln(1+2)", calc.expressionText());
    }

    @Test
    void functionsStartAGroupWhenNothingToWrap() {
        calc.function("cos");
        type("60");
        assertEquals("cos(60", calc.expressionText());
        assertEquals("0.5", calc.resultText()); // missing ")" closed automatically
        type("=");
        assertEquals("cos(60) =", calc.expressionText());
    }

    @Test
    void functionAfterResultAppliesToAnswer() {
        type("4=");
        calc.root("√");
        assertEquals("√(Ans)", calc.expressionText());
        type("=");
        assertEquals("2", calc.resultText());
    }

    @Test
    void powersOfTen() {
        type("3");
        calc.powerOf("10");
        assertEquals("10^(3)", calc.expressionText());
        assertEquals("1,000", calc.resultText());
    }

    @Test
    void postfixOperators() {
        type("5");
        calc.postfix("!");
        assertEquals("120", calc.resultText());
        calc.clearAll();
        calc.postfix("²"); // nothing to square yet
        assertEquals("", calc.expressionText());
    }

    @Test
    void scientificNotationEntry() {
        type("3");
        calc.exponent();
        type("-");
        type("2");
        assertEquals("3E−2", calc.expressionText());
        assertEquals("0.03", calc.resultText());
    }

    @Test
    void backspaceRemovesWholeTokens() {
        calc.function("sin");
        type("3<<");
        assertEquals("", calc.expressionText());
    }

    @Test
    void clearEntryRemovesLastNumber() {
        type("12+345");
        calc.clearEntry();
        assertEquals("12+", calc.expressionText());
    }

    @Test
    void errorsAreShownAndClearedByNextKey() {
        type("1/0=");
        assertEquals("Can't divide by zero", calc.resultText());
        assertEquals(ResultState.ERROR, calc.resultState());
        type("<");
        assertEquals(ResultState.PREVIEW, calc.resultState());
    }

    @Test
    void closeParenthesisOnlyWhenOneIsOpen() {
        type(")");
        assertEquals("", calc.expressionText());
        type("(2+3))");
        assertEquals("(2+3)", calc.expressionText());
    }

    @Test
    void memory() {
        assertFalse(calc.hasMemory());
        type("5");
        calc.memoryAdd();
        calc.memoryAdd();
        assertTrue(calc.hasMemory());
        calc.clearAll();
        type("1+");
        calc.memoryRecall();
        assertEquals("1+10", calc.expressionText());
        calc.memorySubtract(); // memory = 10 − 11
        calc.clearAll();
        calc.memoryRecall();
        assertEquals("(−1)", calc.expressionText());
        calc.memoryClear();
        assertFalse(calc.hasMemory());
    }

    @Test
    void angleUnitAffectsTrig() {
        calc.setAngleUnit(AngleUnit.RADIANS);
        calc.constant("π");
        calc.function("cos");
        assertEquals("−1", calc.resultText());
    }

    @Test
    void paste() {
        assertTrue(calc.paste("2*pi"));
        assertEquals("2×π", calc.expressionText());
        calc.clearAll();
        assertTrue(calc.paste("sqrt(16) mod 3"));
        assertEquals("√(16) mod 3", calc.expressionText());
        assertEquals("1", calc.resultText());
        calc.clearAll();
        assertTrue(calc.paste("1,234.5"));
        assertEquals("1234.5", calc.expressionText());
        assertFalse(calc.paste("hello"));
    }

    @Test
    void copyTextIsPlainNumber() {
        type("1000*-2=");
        assertEquals("-2000", calc.copyText());
    }
}
