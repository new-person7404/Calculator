package com.example.calculator.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits an expression such as {@code "2×sin(30)+√9"} into tokens.
 * Accepts both the display symbols (×, ÷, −, π …) and their ASCII equivalents (*, /, -, pi …),
 * so pasted text works too.
 */
final class Lexer {

    enum Kind {
        NUMBER, CONSTANT, FUNCTION,
        PLUS, MINUS, TIMES, DIVIDE, MOD, POWER, ROOT, PERMUTATIONS, COMBINATIONS,
        PREFIX_SQRT, PREFIX_CBRT, POSTFIX,
        LEFT_PAREN, RIGHT_PAREN, COMMA, END
    }

    /** {@code text} is the canonical spelling (e.g. "sin⁻¹" for "asin"); {@code value} is set for numbers. */
    record Token(Kind kind, String text, double value) {
        Token(Kind kind, String text) {
            this(kind, text, 0);
        }
    }

    /** Named tokens, keyed by every accepted spelling. Matched longest-first. */
    private static final Map<String, Token> WORDS = new LinkedHashMap<>();
    private static final List<String> WORDS_LONGEST_FIRST;

    static {
        for (String f : List.of("sin", "cos", "tan", "sinh", "cosh", "tanh")) {
            word(f, Kind.FUNCTION, f);
            word(f + "⁻¹", Kind.FUNCTION, f + "⁻¹");
            word("a" + f, Kind.FUNCTION, f + "⁻¹");
        }
        for (String f : List.of("ln", "log", "abs", "floor", "ceil", "round", "gcd", "lcm", "sqrt", "cbrt")) {
            word(f, Kind.FUNCTION, f);
        }
        word("log₂", Kind.FUNCTION, "log₂");
        word("log2", Kind.FUNCTION, "log₂");

        word("π", Kind.CONSTANT, "π");
        word("pi", Kind.CONSTANT, "π");
        word("e", Kind.CONSTANT, "e");
        word("Ans", Kind.CONSTANT, "Ans");
        word("ans", Kind.CONSTANT, "Ans");
        word("rand", Kind.CONSTANT, "rand");

        word("mod", Kind.MOD, "mod");
        word("yroot", Kind.ROOT, "yroot");
        word("P", Kind.PERMUTATIONS, "P");
        word("C", Kind.COMBINATIONS, "C");

        WORDS_LONGEST_FIRST = WORDS.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    private static void word(String spelling, Kind kind, String canonical) {
        WORDS.put(spelling, new Token(kind, canonical));
    }

    private Lexer() {
    }

    static List<Token> tokenize(String s) throws CalculationException {
        List<Token> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (isDigit(c) || c == '.') {
                i = readNumber(s, i, out);
                continue;
            }
            Token simple = switch (c) {
                case '+' -> new Token(Kind.PLUS, "+");
                case '-', '−' -> new Token(Kind.MINUS, "−");
                case '*', '×', '·' -> new Token(Kind.TIMES, "×");
                case '/', '÷' -> new Token(Kind.DIVIDE, "÷");
                case '^' -> new Token(Kind.POWER, "^");
                case '(' -> new Token(Kind.LEFT_PAREN, "(");
                case ')' -> new Token(Kind.RIGHT_PAREN, ")");
                case ',' -> new Token(Kind.COMMA, ",");
                case '!', '%', '²', '³' -> new Token(Kind.POSTFIX, String.valueOf(c));
                case '√' -> new Token(Kind.PREFIX_SQRT, "√");
                case '∛' -> new Token(Kind.PREFIX_CBRT, "∛");
                default -> null;
            };
            if (simple != null) {
                out.add(simple);
                i++;
                continue;
            }
            if (s.startsWith("⁻¹", i)) {
                out.add(new Token(Kind.POSTFIX, "⁻¹"));
                i += 2;
                continue;
            }
            String match = matchWord(s, i);
            if (match == null) {
                throw CalculationException.syntax();
            }
            out.add(WORDS.get(match));
            i += match.length();
        }
        out.add(new Token(Kind.END, ""));
        return out;
    }

    private static String matchWord(String s, int i) {
        for (String w : WORDS_LONGEST_FIRST) {
            if (s.startsWith(w, i)) {
                return w;
            }
        }
        return null;
    }

    /** Reads {@code digits[.digits][E[±]digits]} starting at {@code i}; returns the index after it. */
    private static int readNumber(String s, int i, List<Token> out) throws CalculationException {
        int start = i;
        int n = s.length();
        boolean seenDot = false;
        while (i < n && (isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
            if (s.charAt(i) == '.') {
                if (seenDot) {
                    throw CalculationException.syntax();
                }
                seenDot = true;
            }
            i++;
        }
        String mantissa = s.substring(start, i);
        if (mantissa.equals(".")) {
            throw CalculationException.syntax();
        }
        String exponent = "";
        if (i < n && s.charAt(i) == 'E') {
            int j = i + 1;
            String sign = "";
            if (j < n && (s.charAt(j) == '+' || s.charAt(j) == '-' || s.charAt(j) == '−')) {
                sign = s.charAt(j) == '+' ? "" : "-";
                j++;
            }
            int digitsStart = j;
            while (j < n && isDigit(s.charAt(j))) {
                j++;
            }
            if (j == digitsStart) {
                throw CalculationException.syntax();
            }
            exponent = "E" + sign + s.substring(digitsStart, j);
            i = j;
        }
        String text = mantissa + exponent;
        out.add(new Token(Kind.NUMBER, text, Double.parseDouble(text)));
        return i;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
