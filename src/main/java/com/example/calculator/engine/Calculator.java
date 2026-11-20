package com.example.calculator.engine;

import com.example.calculator.engine.Lexer.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.regex.Pattern;

/**
 * The state behind the calculator screens, with no UI code.
 *
 * <p>The expression is kept as a list of "pieces" exactly as they appear on screen ("7", "×", "sin(", ")" …),
 * so backspace removes whole tokens and the display is just the pieces joined together.
 *
 * <p>Button conventions (matching common desktop calculators):
 * <ul>
 *   <li>A function or root pressed right after a number wraps it: {@code 30 sin} → {@code sin(30)}.</li>
 *   <li>After {@code =}, typing a digit starts over; an operator continues from the answer ({@code Ans+}).</li>
 * </ul>
 */
public final class Calculator {

    /** What the big result line is currently showing. */
    public enum ResultState { PREVIEW, FINAL, ERROR }

    private static final Set<String> BINARY =
            Set.of("+", "−", "×", "÷", "^", " mod ", " yroot ", " P ", " C ", ",");
    private static final Set<String> POSTFIX = Set.of("!", "%", "²", "³", "⁻¹");
    private static final Set<String> CONSTANTS = Set.of("π", "e", "Ans", "rand");
    private static final Set<String> PREFIX = Set.of("√", "∛");
    private static final Pattern NUMBER_PIECE = Pattern.compile("[0-9.E]+|[0-9.]+E[−+][0-9]+");
    private static final Pattern GROUPED_NUMBER = Pattern.compile("−?-?\\d{1,3}(,\\d{3})+(\\.\\d+)?");

    private final List<String> pieces = new ArrayList<>();
    private final DoubleSupplier random;
    private AngleUnit angleUnit = AngleUnit.DEGREES;
    private boolean justEvaluated;
    private String evaluatedExpression = "";
    private double ans;
    private double memory;
    private boolean memoryStored;
    private String error;

    public Calculator() {
        this(Math::random);
    }

    public Calculator(DoubleSupplier random) {
        this.random = random;
    }

    // ---- display ----------------------------------------------------------------------------

    /** The small line above the result: the expression being typed, or "… =" after evaluating. */
    public String expressionText() {
        return justEvaluated ? evaluatedExpression + " =" : String.join("", pieces);
    }

    /** The big line: an error, the final answer, or a live preview of the expression's value. */
    public String resultText() {
        if (error != null) {
            return error;
        }
        if (justEvaluated) {
            return NumberFormatter.toDisplay(ans);
        }
        if (pieces.isEmpty()) {
            return "0";
        }
        try {
            return NumberFormatter.toDisplay(evaluate(closedExpression()));
        } catch (CalculationException e) {
            return ""; // incomplete expression such as "5×": show nothing yet
        }
    }

    public ResultState resultState() {
        if (error != null) {
            return ResultState.ERROR;
        }
        return justEvaluated ? ResultState.FINAL : ResultState.PREVIEW;
    }

    /** Text for the clipboard: the current value in plain ASCII, or the expression if it has no value yet. */
    public String copyText() {
        Double value = currentValue();
        return value != null ? NumberFormatter.format(value) : expressionText();
    }

    public AngleUnit angleUnit() {
        return angleUnit;
    }

    public void setAngleUnit(AngleUnit angleUnit) {
        this.angleUnit = angleUnit;
    }

    public boolean hasMemory() {
        return memoryStored;
    }

    public double answer() {
        return ans;
    }

    // ---- input ------------------------------------------------------------------------------

    public void digit(int digit) {
        clearError();
        startFreshIfEvaluated();
        String d = String.valueOf(digit);
        int start = numberRunStart();
        if (start >= 0 && start == pieces.size() - 1 && pieces.get(start).equals("0")) {
            pieces.set(start, d); // no leading zeros: "0" then "5" is "5"
        } else {
            pieces.add(d);
        }
    }

    public void decimalPoint() {
        clearError();
        startFreshIfEvaluated();
        if (awaitingExponentDigits()) {
            return;
        }
        int start = numberRunStart();
        if (start < 0) {
            pieces.add("0");
            pieces.add(".");
        } else if (!joined(start).contains(".") && !joined(start).contains("E")) {
            pieces.add(".");
        }
    }

    /** The EE key: scientific notation, e.g. 3 EE 8 → 3E8. */
    public void exponent() {
        clearError();
        if (justEvaluated) {
            return;
        }
        int start = numberRunStart();
        if (start >= 0 && !joined(start).contains("E")) {
            pieces.add("E");
        }
    }

    /** A binary operator: "+", "−", "×", "÷", "^", " mod ", " yroot ", " P " or " C ". */
    public void operator(String op) {
        clearError();
        if (justEvaluated) {
            continueFromAnswer();
        }
        boolean minus = op.equals("−");
        if (pieces.isEmpty()) {
            if (minus) {
                pieces.add("−");
            } else {
                pieces.add("0");
                pieces.add(op);
            }
            return;
        }
        String last = last();
        if (last.equals("E") || isExponentSign(pieces.size() - 1)) {
            if (op.equals("−") || op.equals("+")) { // sign of the exponent: 1E−5
                if (last.equals("E")) {
                    pieces.add(op);
                } else {
                    pieces.set(pieces.size() - 1, op);
                }
            }
            return;
        }
        if (opensGroup(last)) {
            if (minus) {
                pieces.add("−"); // unary minus: "(−"
            }
            return;
        }
        if (BINARY.contains(last)) {
            if (minus && !last.equals("+") && !last.equals("−")) {
                pieces.add("−"); // negative operand: "5×−"
                return;
            }
            while (!pieces.isEmpty() && BINARY.contains(last())) {
                pieces.removeLast(); // replace the previous operator
            }
            if (pieces.isEmpty() || opensGroup(last())) {
                if (minus) {
                    pieces.add("−");
                }
                return;
            }
        }
        pieces.add(op);
    }

    /** A postfix operator: "!", "%", "²", "³" or "⁻¹". */
    public void postfix(String op) {
        clearError();
        if (justEvaluated) {
            continueFromAnswer();
        }
        if (endsWithOperand()) {
            pieces.add(op);
        }
    }

    /** A one-argument function such as "sin" or "ln". */
    public void function(String name) {
        insertFunction(name + "(", false);
    }

    /** A function taking several comma-separated arguments: "log" (x, base), "gcd", "lcm". */
    public void multiArgumentFunction(String name) {
        insertFunction(name + "(", true);
    }

    /** "√" or "∛": wraps a number just typed, otherwise starts a root. */
    public void root(String symbol) {
        if (justEvaluated || endsWithOperand()) {
            insertFunction(symbol + "(", false);
        } else {
            clearError();
            if (!awaitingExponentDigits()) {
                pieces.add(symbol);
            }
        }
    }

    /** Powers of a fixed base: "10" for 10ˣ, "2" for 2ˣ, "e" for eˣ. */
    public void powerOf(String base) {
        clearError();
        if (awaitingExponentDigits()) {
            return;
        }
        List<String> head = new ArrayList<>(base.equals("e") ? List.of("e") : List.of(base.split("")));
        head.add("^");
        head.add("(");
        if (justEvaluated) {
            pieces.clear();
            pieces.addAll(head);
            pieces.add("Ans");
            pieces.add(")");
            justEvaluated = false;
        } else if (endsWithOperand()) {
            int start = lastOperandStart();
            if (start < 0) {
                return;
            }
            if (isBareGroup(start)) {
                pieces.addAll(start, head.subList(0, head.size() - 1)); // (3) → 10^(3)
            } else {
                pieces.addAll(start, head);
                pieces.add(")");
            }
        } else {
            pieces.addAll(head);
        }
    }

    /** "π", "e", "Ans" or "rand". */
    public void constant(String name) {
        clearError();
        startFreshIfEvaluated();
        if (!awaitingExponentDigits()) {
            pieces.add(name);
        }
    }

    public void openParenthesis() {
        clearError();
        startFreshIfEvaluated();
        if (!awaitingExponentDigits()) {
            pieces.add("(");
        }
    }

    public void closeParenthesis() {
        clearError();
        if (!justEvaluated && openParentheses() > 0 && endsWithOperand()) {
            pieces.add(")");
        }
    }

    /** Separates arguments of multi-argument functions: log(8,2), gcd(12,18). */
    public void comma() {
        clearError();
        if (!justEvaluated && openParentheses() > 0 && endsWithOperand()) {
            pieces.add(",");
        }
    }

    /** The ± key: flips the sign of the last operand (or of the answer). */
    public void negate() {
        clearError();
        if (justEvaluated) {
            evaluatedExpression = "−(" + NumberFormatter.toDisplay(ans) + ")";
            ans = ans == 0 ? 0 : -ans;
            return;
        }
        if (!endsWithOperand()) {
            operator("−");
            return;
        }
        int start = lastOperandStart();
        if (start < 0) {
            return;
        }
        if (start > 0) {
            String before = pieces.get(start - 1);
            if (before.equals("−")) {
                if (isOperatorContext(start - 2)) {
                    pieces.remove(start - 1); // "−5" → "5"
                } else {
                    pieces.set(start - 1, "+"); // "3−5" → "3+5"
                }
                return;
            }
            if (before.equals("+")) {
                pieces.set(start - 1, "−");
                return;
            }
            if (!isOperatorContext(start - 1)) {
                // Implicit multiplication such as 2(3): negate inside parentheses so it stays a product.
                pieces.add(start, "(");
                pieces.add(start + 1, "−");
                pieces.add(")");
                return;
            }
        }
        pieces.add(start, "−");
    }

    public void backspace() {
        clearError();
        if (justEvaluated) {
            justEvaluated = false;
            pieces.clear();
        } else if (!pieces.isEmpty()) {
            pieces.removeLast();
        }
    }

    /** CE: removes the last operand (the number being typed). */
    public void clearEntry() {
        clearError();
        if (justEvaluated) {
            clearAll();
        } else if (endsWithOperand()) {
            int start = lastOperandStart();
            pieces.subList(Math.max(start, 0), pieces.size()).clear();
        } else {
            backspace();
        }
    }

    public void clearAll() {
        pieces.clear();
        justEvaluated = false;
        error = null;
    }

    /** The = key. On failure the expression is kept so it can be corrected. */
    public void evaluate() {
        clearError();
        if (justEvaluated || pieces.isEmpty()) {
            return;
        }
        String expression = closedExpression();
        try {
            ans = evaluate(expression);
            evaluatedExpression = expression;
            justEvaluated = true;
        } catch (CalculationException e) {
            error = e.getMessage();
        }
    }

    /** Inserts pasted text. Returns false (and changes nothing) if it isn't a valid expression. */
    public boolean paste(String text) {
        String cleaned = text.strip();
        if (GROUPED_NUMBER.matcher(cleaned).matches()) {
            cleaned = cleaned.replace(",", "");
        }
        List<String> pasted = new ArrayList<>();
        try {
            List<Token> tokens = Lexer.tokenize(cleaned);
            for (int i = 0; i < tokens.size(); i++) {
                Token token = tokens.get(i);
                switch (token.kind()) {
                    case END -> { }
                    case NUMBER -> {
                        String number = token.text().replace('-', '−');
                        if (number.contains("E")) {
                            pasted.add(number);
                        } else {
                            pasted.addAll(List.of(number.split("")));
                        }
                    }
                    case FUNCTION -> {
                        if (tokens.get(i + 1).kind() != Lexer.Kind.LEFT_PAREN) {
                            return false;
                        }
                        i++;
                        pasted.add(switch (token.text()) {
                            case "sqrt" -> "√(";
                            case "cbrt" -> "∛(";
                            default -> token.text() + "(";
                        });
                    }
                    case MOD, ROOT, PERMUTATIONS, COMBINATIONS -> pasted.add(" " + token.text() + " ");
                    default -> pasted.add(token.text());
                }
            }
        } catch (CalculationException e) {
            return false;
        }
        if (pasted.isEmpty()) {
            return false;
        }
        clearError();
        startFreshIfEvaluated();
        pieces.addAll(pasted);
        return true;
    }

    // ---- memory -----------------------------------------------------------------------------

    public void memoryClear() {
        memory = 0;
        memoryStored = false;
    }

    /** MR: inserts the stored value, replacing a number that is being typed. */
    public void memoryRecall() {
        if (!memoryStored) {
            return;
        }
        clearError();
        startFreshIfEvaluated();
        if (awaitingExponentDigits()) {
            return;
        }
        int start = numberRunStart();
        if (start >= 0) {
            pieces.subList(start, pieces.size()).clear();
        }
        String literal = NumberFormatter.format(memory, 15).replace('-', '−');
        if (memory < 0) {
            pieces.addAll(List.of("(", literal, ")"));
        } else {
            pieces.add(literal);
        }
    }

    public void memoryAdd() {
        Double value = currentValue();
        if (value != null) {
            memory += value;
            memoryStored = true;
        }
    }

    public void memorySubtract() {
        Double value = currentValue();
        if (value != null) {
            memory -= value;
            memoryStored = true;
        }
    }

    public void memoryStore() {
        Double value = currentValue();
        if (value != null) {
            memory = value;
            memoryStored = true;
        }
    }

    // ---- helpers ----------------------------------------------------------------------------

    private double evaluate(String expression) throws CalculationException {
        return ExpressionEvaluator.evaluate(expression, new ExpressionEvaluator.Context(angleUnit, ans, random));
    }

    /** The value shown right now, or null if the expression is incomplete or in error. */
    private Double currentValue() {
        if (error != null) {
            return null;
        }
        if (justEvaluated) {
            return ans;
        }
        if (pieces.isEmpty()) {
            return 0.0;
        }
        try {
            return evaluate(closedExpression());
        } catch (CalculationException e) {
            return null;
        }
    }

    private void insertFunction(String open, boolean multiArgument) {
        clearError();
        String close = multiArgument ? "," : ")";
        if (justEvaluated) {
            pieces.clear();
            pieces.addAll(List.of(open, "Ans", close));
            justEvaluated = false;
            return;
        }
        if (awaitingExponentDigits()) {
            return;
        }
        if (endsWithOperand()) {
            int start = lastOperandStart();
            if (start >= 0) {
                if (isBareGroup(start)) {
                    pieces.set(start, open); // (1+2) → ln(1+2), not ln((1+2))
                    pieces.set(pieces.size() - 1, close);
                } else {
                    pieces.add(start, open);
                    pieces.add(close);
                }
                return;
            }
        }
        pieces.add(open);
    }

    /** True if the operand starting at {@code start} is exactly "( … )" running to the end. */
    private boolean isBareGroup(int start) {
        return pieces.get(start).equals("(") && last().equals(")");
    }

    private String closedExpression() {
        return String.join("", pieces) + ")".repeat(Math.max(0, openParentheses()));
    }

    private void clearError() {
        error = null;
    }

    private void startFreshIfEvaluated() {
        if (justEvaluated) {
            pieces.clear();
            justEvaluated = false;
        }
    }

    private void continueFromAnswer() {
        pieces.clear();
        pieces.add("Ans");
        justEvaluated = false;
    }

    private String last() {
        return pieces.getLast();
    }

    private String joined(int from) {
        return String.join("", pieces.subList(from, pieces.size()));
    }

    private int openParentheses() {
        int open = 0;
        for (String piece : pieces) {
            if (piece.endsWith("(")) {
                open++;
            } else if (piece.equals(")")) {
                open--;
            }
        }
        return open;
    }

    private static boolean opensGroup(String piece) {
        return piece.endsWith("(") || PREFIX.contains(piece);
    }

    /** True if position {@code index} is the start of input or follows something that expects an operand. */
    private boolean isOperatorContext(int index) {
        if (index < 0) {
            return true;
        }
        String piece = pieces.get(index);
        return BINARY.contains(piece) || opensGroup(piece);
    }

    private boolean isExponentSign(int index) {
        return index > 0
                && (pieces.get(index).equals("−") || pieces.get(index).equals("+"))
                && pieces.get(index - 1).equals("E");
    }

    private boolean isNumberPiece(int index) {
        return NUMBER_PIECE.matcher(pieces.get(index)).matches() || isExponentSign(index);
    }

    private boolean awaitingExponentDigits() {
        return !pieces.isEmpty() && (last().equals("E") || isExponentSign(pieces.size() - 1));
    }

    /** Index where a trailing number starts, or −1 if the expression doesn't end in a number. */
    private int numberRunStart() {
        int i = pieces.size() - 1;
        if (i < 0 || !isNumberPiece(i)) {
            return -1;
        }
        while (i > 0 && isNumberPiece(i - 1)) {
            i--;
        }
        return i;
    }

    private boolean endsWithOperand() {
        if (pieces.isEmpty() || awaitingExponentDigits()) {
            return false;
        }
        String last = last();
        return isNumberPiece(pieces.size() - 1) || last.equals(")") || CONSTANTS.contains(last) || POSTFIX.contains(last);
    }

    /**
     * Index where the last complete operand starts: a number, constant or parenthesised group (including
     * its function name), with any postfix operators after it and √/∛ before it. Returns −1 if none.
     */
    private int lastOperandStart() {
        int i = pieces.size() - 1;
        while (i >= 0 && POSTFIX.contains(pieces.get(i))) {
            i--;
        }
        if (i < 0) {
            return -1;
        }
        String piece = pieces.get(i);
        if (piece.equals(")")) {
            int depth = 0;
            int j = i;
            for (; j >= 0; j--) {
                if (pieces.get(j).equals(")")) {
                    depth++;
                } else if (pieces.get(j).endsWith("(") && --depth == 0) {
                    break;
                }
            }
            if (j < 0) {
                return -1;
            }
            i = j;
        } else if (isNumberPiece(i)) {
            while (i > 0 && isNumberPiece(i - 1)) {
                i--;
            }
        } else if (!CONSTANTS.contains(piece)) {
            return -1;
        }
        while (i > 0 && PREFIX.contains(pieces.get(i - 1))) {
            i--;
        }
        return i;
    }
}
