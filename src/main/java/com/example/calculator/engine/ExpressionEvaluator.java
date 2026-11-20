package com.example.calculator.engine;

import com.example.calculator.engine.Lexer.Kind;
import com.example.calculator.engine.Lexer.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * Evaluates calculator expressions with a recursive-descent parser.
 *
 * <p>Precedence, lowest to highest:
 * <ol>
 *   <li>{@code + −}</li>
 *   <li>{@code × ÷ mod}, and implicit multiplication ({@code 2π}, {@code 3(4)})</li>
 *   <li>unary {@code −}, prefix {@code √ ∛}</li>
 *   <li>{@code nPr nCr}</li>
 *   <li>{@code ^ yroot} (right-associative)</li>
 *   <li>postfix {@code ! % ² ³ ⁻¹}</li>
 *   <li>numbers, constants, functions, parentheses</li>
 * </ol>
 * A missing closing parenthesis at the end of the input is tolerated, so {@code "sin(30"} works.
 */
public final class ExpressionEvaluator {

    /** Values the expression may refer to. */
    public record Context(AngleUnit angleUnit, double ans, DoubleSupplier random) {
        public static Context defaults() {
            return new Context(AngleUnit.DEGREES, 0, Math::random);
        }
    }

    /** Largest integer a double can represent exactly; integer-only functions refuse anything bigger. */
    private static final double MAX_EXACT_INTEGER = 9007199254740992.0;

    private final List<Token> tokens;
    private final Context context;
    private int position;

    private ExpressionEvaluator(List<Token> tokens, Context context) {
        this.tokens = tokens;
        this.context = context;
    }

    public static double evaluate(String expression, Context context) throws CalculationException {
        List<Token> tokens = Lexer.tokenize(expression);
        ExpressionEvaluator parser = new ExpressionEvaluator(tokens, context);
        if (parser.peek() == Kind.END) {
            throw CalculationException.syntax();
        }
        double value = parser.parseExpression();
        if (parser.peek() != Kind.END) {
            throw CalculationException.syntax();
        }
        return checked(value);
    }

    // ---- grammar ----------------------------------------------------------------------------

    private double parseExpression() throws CalculationException {
        double left = parseTerm();
        while (true) {
            if (accept(Kind.PLUS)) {
                left = left + parseTerm();
            } else if (accept(Kind.MINUS)) {
                left = left - parseTerm();
            } else {
                return left;
            }
        }
    }

    private double parseTerm() throws CalculationException {
        double left = parseUnary();
        while (true) {
            if (accept(Kind.TIMES)) {
                left = left * parseUnary();
            } else if (accept(Kind.DIVIDE)) {
                double right = parseUnary();
                if (right == 0) {
                    throw CalculationException.divideByZero();
                }
                left = left / right;
            } else if (accept(Kind.MOD)) {
                left = floorMod(left, parseUnary());
            } else if (startsOperand(peek())) {
                left = left * parseUnary(); // implicit multiplication: 2π, 3(4), 2sin(30)
            } else {
                return left;
            }
        }
    }

    private double parseUnary() throws CalculationException {
        if (accept(Kind.MINUS)) {
            return -parseUnary();
        }
        if (accept(Kind.PLUS)) {
            return parseUnary();
        }
        if (accept(Kind.PREFIX_SQRT)) {
            return sqrt(parseUnary());
        }
        if (accept(Kind.PREFIX_CBRT)) {
            return Math.cbrt(parseUnary());
        }
        return parseCombinatorics();
    }

    private double parseCombinatorics() throws CalculationException {
        double left = parsePower();
        while (true) {
            if (accept(Kind.PERMUTATIONS)) {
                left = permutations(left, parsePower());
            } else if (accept(Kind.COMBINATIONS)) {
                left = combinations(left, parsePower());
            } else {
                return left;
            }
        }
    }

    private double parsePower() throws CalculationException {
        double base = parsePostfix();
        if (accept(Kind.POWER)) {
            return power(base, parseUnary()); // parseUnary recurses back here: right-associative
        }
        if (accept(Kind.ROOT)) {
            return root(base, parseUnary());
        }
        return base;
    }

    private double parsePostfix() throws CalculationException {
        double value = parsePrimary();
        while (peek() == Kind.POSTFIX) {
            String op = tokens.get(position++).text();
            value = switch (op) {
                case "!" -> factorial(value);
                case "%" -> value / 100;
                case "²" -> value * value;
                case "³" -> value * value * value;
                case "⁻¹" -> {
                    if (value == 0) {
                        throw CalculationException.divideByZero();
                    }
                    yield 1 / value;
                }
                default -> throw CalculationException.syntax();
            };
        }
        return value;
    }

    private double parsePrimary() throws CalculationException {
        Token token = tokens.get(position);
        switch (token.kind()) {
            case NUMBER -> {
                position++;
                return token.value();
            }
            case CONSTANT -> {
                position++;
                return switch (token.text()) {
                    case "π" -> Math.PI;
                    case "e" -> Math.E;
                    case "Ans" -> context.ans();
                    case "rand" -> context.random().getAsDouble();
                    default -> throw CalculationException.syntax();
                };
            }
            case FUNCTION -> {
                position++;
                expect(Kind.LEFT_PAREN);
                List<Double> args = new ArrayList<>();
                args.add(parseExpression());
                while (accept(Kind.COMMA)) {
                    args.add(parseExpression());
                }
                closeParen();
                return applyFunction(token.text(), args);
            }
            case LEFT_PAREN -> {
                position++;
                double value = parseExpression();
                closeParen();
                return value;
            }
            default -> throw CalculationException.syntax();
        }
    }

    private static boolean startsOperand(Kind kind) {
        return switch (kind) {
            case NUMBER, CONSTANT, FUNCTION, LEFT_PAREN, PREFIX_SQRT, PREFIX_CBRT -> true;
            default -> false;
        };
    }

    // ---- token helpers ----------------------------------------------------------------------

    private Kind peek() {
        return tokens.get(position).kind();
    }

    private boolean accept(Kind kind) {
        if (peek() == kind) {
            position++;
            return true;
        }
        return false;
    }

    private void expect(Kind kind) throws CalculationException {
        if (!accept(kind)) {
            throw CalculationException.syntax();
        }
    }

    /** Accepts ")" — or the end of input, so unclosed parentheses are closed automatically. */
    private void closeParen() throws CalculationException {
        if (!accept(Kind.RIGHT_PAREN) && peek() != Kind.END) {
            throw CalculationException.syntax();
        }
    }

    // ---- functions --------------------------------------------------------------------------

    private double applyFunction(String name, List<Double> args) throws CalculationException {
        if (name.equals("log") && args.size() == 2) {
            return logBase(args.get(0), args.get(1));
        }
        if (name.equals("gcd") || name.equals("lcm")) {
            if (args.size() < 2) {
                throw CalculationException.syntax();
            }
            double result = args.get(0);
            for (int i = 1; i < args.size(); i++) {
                result = name.equals("gcd") ? gcd(result, args.get(i)) : lcm(result, args.get(i));
            }
            return result;
        }
        if (args.size() != 1) {
            throw CalculationException.syntax();
        }
        double x = args.get(0);
        AngleUnit angle = context.angleUnit();
        return checked(switch (name) {
            case "sin" -> sin(x);
            case "cos" -> cos(x);
            case "tan" -> tan(x);
            case "sin⁻¹" -> angle.fromRadians(Math.asin(requireRange(x, -1, 1)));
            case "cos⁻¹" -> angle.fromRadians(Math.acos(requireRange(x, -1, 1)));
            case "tan⁻¹" -> angle.fromRadians(Math.atan(x));
            case "sinh" -> Math.sinh(x);
            case "cosh" -> Math.cosh(x);
            case "tanh" -> Math.tanh(x);
            case "sinh⁻¹" -> Math.log(x + Math.sqrt(x * x + 1));
            case "cosh⁻¹" -> Math.log(requireAtLeast(x, 1) + Math.sqrt(x * x - 1));
            case "tanh⁻¹" -> {
                if (x <= -1 || x >= 1) {
                    throw CalculationException.invalidInput();
                }
                yield 0.5 * Math.log((1 + x) / (1 - x));
            }
            case "ln" -> Math.log(requirePositive(x));
            case "log" -> Math.log10(requirePositive(x));
            case "log₂" -> Math.log(requirePositive(x)) / Math.log(2);
            case "abs" -> Math.abs(x);
            case "floor" -> Math.floor(x);
            case "ceil" -> Math.ceil(x);
            case "round" -> x < 0 ? -Math.round(-x) : Math.round(x); // halves round away from zero
            case "sqrt" -> sqrt(x);
            case "cbrt" -> Math.cbrt(x);
            default -> throw CalculationException.syntax();
        });
    }

    private double sin(double x) {
        AngleUnit angle = context.angleUnit();
        if (angle != AngleUnit.RADIANS && x % angle.halfTurn() == 0) {
            return 0; // exact: sin(180°) is 0, not 1.2E-16
        }
        return snapToZero(Math.sin(angle.toRadians(x)), x);
    }

    private double cos(double x) {
        AngleUnit angle = context.angleUnit();
        if (angle != AngleUnit.RADIANS && (x - angle.halfTurn() / 2) % angle.halfTurn() == 0) {
            return 0; // exact: cos(90°) is 0
        }
        return snapToZero(Math.cos(angle.toRadians(x)), x);
    }

    private double tan(double x) throws CalculationException {
        double cos = cos(x);
        if (cos == 0) {
            throw CalculationException.invalidInput(); // tan(90°) is undefined
        }
        return sin(x) / cos;
    }

    /** Removes floating-point noise such as sin(π) = 1.2E-16. */
    private static double snapToZero(double result, double input) {
        return Math.abs(result) < 1e-15 && Math.abs(input) > 1e-6 ? 0 : result;
    }

    private static double sqrt(double x) throws CalculationException {
        if (x < 0) {
            throw CalculationException.invalidInput();
        }
        return Math.sqrt(x);
    }

    private static double power(double base, double exponent) throws CalculationException {
        if (base == 0 && exponent < 0) {
            throw CalculationException.divideByZero();
        }
        double result = Math.pow(base, exponent);
        if (Double.isNaN(result)) {
            throw CalculationException.invalidInput(); // e.g. (−8)^0.5
        }
        return result;
    }

    /** {@code x yroot n}: the n-th root of x. Odd roots of negative numbers are allowed. */
    private static double root(double x, double n) throws CalculationException {
        if (n == 0) {
            throw CalculationException.invalidInput();
        }
        if (x < 0) {
            if (isInteger(n) && Math.abs(n % 2) == 1) {
                return -Math.pow(-x, 1 / n);
            }
            throw CalculationException.invalidInput();
        }
        return Math.pow(x, 1 / n);
    }

    private static double logBase(double x, double base) throws CalculationException {
        if (x <= 0 || base <= 0 || base == 1) {
            throw CalculationException.invalidInput();
        }
        return Math.log(x) / Math.log(base);
    }

    private static double floorMod(double x, double y) throws CalculationException {
        if (y == 0) {
            throw CalculationException.divideByZero();
        }
        return x - y * Math.floor(x / y);
    }

    private static double factorial(double n) throws CalculationException {
        requireNonNegativeInteger(n);
        if (n > 170) {
            throw CalculationException.overflow();
        }
        double result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    private static double permutations(double n, double r) throws CalculationException {
        requireNonNegativeInteger(n);
        requireNonNegativeInteger(r);
        if (r > n) {
            throw CalculationException.invalidInput();
        }
        double result = 1;
        for (double i = n - r + 1; i <= n; i++) {
            result *= i;
        }
        return checked(result);
    }

    private static double combinations(double n, double r) throws CalculationException {
        requireNonNegativeInteger(n);
        requireNonNegativeInteger(r);
        if (r > n) {
            throw CalculationException.invalidInput();
        }
        double k = Math.min(r, n - r);
        double result = 1;
        for (int i = 1; i <= k; i++) {
            result = result * (n - k + i) / i;
        }
        return checked(Math.round(result));
    }

    private static double gcd(double a, double b) throws CalculationException {
        requireInteger(a);
        requireInteger(b);
        long x = Math.abs((long) a);
        long y = Math.abs((long) b);
        while (y != 0) {
            long t = x % y;
            x = y;
            y = t;
        }
        return x;
    }

    private static double lcm(double a, double b) throws CalculationException {
        double g = gcd(a, b);
        return g == 0 ? 0 : checked(Math.abs(a / g * b));
    }

    // ---- validation -------------------------------------------------------------------------

    private static boolean isInteger(double x) {
        return x == Math.rint(x) && Math.abs(x) <= MAX_EXACT_INTEGER;
    }

    private static void requireInteger(double x) throws CalculationException {
        if (!isInteger(x)) {
            throw CalculationException.invalidInput();
        }
    }

    private static void requireNonNegativeInteger(double x) throws CalculationException {
        if (x < 0 || !isInteger(x)) {
            throw CalculationException.invalidInput();
        }
    }

    private static double requirePositive(double x) throws CalculationException {
        if (x <= 0) {
            throw CalculationException.invalidInput();
        }
        return x;
    }

    private static double requireAtLeast(double x, double min) throws CalculationException {
        if (x < min) {
            throw CalculationException.invalidInput();
        }
        return x;
    }

    private static double requireRange(double x, double min, double max) throws CalculationException {
        if (x < min || x > max) {
            throw CalculationException.invalidInput();
        }
        return x;
    }

    private static double checked(double value) throws CalculationException {
        if (Double.isNaN(value)) {
            throw CalculationException.invalidInput();
        }
        if (Double.isInfinite(value)) {
            throw CalculationException.overflow();
        }
        return value;
    }
}
