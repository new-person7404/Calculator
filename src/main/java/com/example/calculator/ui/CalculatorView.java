package com.example.calculator.ui;

import com.example.calculator.engine.Calculator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Display plus keypad for the Basic, Scientific (Basic) and Scientific (Advanced) modes. */
public final class CalculatorView extends BorderPane {

    public enum Layout { BASIC, SCIENTIFIC, ADVANCED }

    private static final String DIGIT = "digit";
    private static final String OPERATOR = "operator";
    private static final String FUNCTION = "function";
    private static final String MEMORY = "memory";
    private static final String CONTROL = "control";
    private static final String EQUALS = "equals";

    private final Calculator calc;
    private final Label expression = new Label();
    private final Label result = new Label();
    private final Label angleIndicator = indicator();
    private final Label secondIndicator = indicator();
    private final Label hypIndicator = indicator();
    private final Label memoryIndicator = indicator();
    private final Map<Layout, Keypad> keypads = new EnumMap<>(Layout.class);

    private Layout layout;
    private Keypad keypad;
    private boolean second; // the 2nd key: shows inverse functions
    private boolean hyp;    // the hyp key: shows hyperbolic functions

    public CalculatorView(Calculator calc) {
        this.calc = calc;
        getStyleClass().add("calculator");
        setTop(buildDisplay());
        setLayout(Layout.BASIC);
    }

    public void setLayout(Layout layout) {
        this.layout = layout;
        second = false;
        hyp = false;
        keypad = keypads.computeIfAbsent(layout, this::buildKeypad);
        setCenter(keypad.grid);
        refresh();
    }

    /**
     * Presses the button bound to a keyboard key in the current layout. {@code key} is a typed
     * character ("7", "*", "s") or a key name ("ENTER", "BACK_SPACE", "ESCAPE", "DELETE", "F9").
     */
    public boolean handleKey(String key) {
        CalcButton button = keypad.shortcuts.get(key);
        if (button == null && key.equals(",")) {
            button = keypad.shortcuts.get("."); // decimal comma on some keyboards
        }
        if (button == null || button.isDisabled()) {
            return false;
        }
        button.fire();
        flash(button);
        return true;
    }

    public void refresh() {
        String exprText = calc.expressionText();
        expression.setText(exprText.isEmpty() ? " " : exprText);

        String resultText = calc.resultText();
        result.setText(resultText.isEmpty() ? " " : resultText);
        result.getStyleClass().removeAll("preview", "final", "error", "long", "very-long");
        result.getStyleClass().add(switch (calc.resultState()) {
            case PREVIEW -> "preview";
            case FINAL -> "final";
            case ERROR -> "error";
        });
        if (resultText.length() > 22) {
            result.getStyleClass().add("very-long");
        } else if (resultText.length() > 14) {
            result.getStyleClass().add("long");
        }

        boolean scientific = layout != Layout.BASIC;
        angleIndicator.setText(scientific ? calc.angleUnit().label() : "");
        secondIndicator.setText(second ? "2nd" : "");
        hypIndicator.setText(hyp ? "HYP" : "");
        memoryIndicator.setText(calc.hasMemory() ? "M" : "");

        for (CalcButton button : keypad.buttons) {
            button.update();
        }
    }

    // ---- display ----------------------------------------------------------------------------

    private VBox buildDisplay() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox indicators = new HBox(8, angleIndicator, secondIndicator, hypIndicator, spacer, memoryIndicator);
        indicators.getStyleClass().add("indicators");

        expression.getStyleClass().add("expression");
        result.getStyleClass().add("result");
        for (Label label : List.of(expression, result)) {
            label.setMaxWidth(Double.MAX_VALUE);
            label.setAlignment(Pos.CENTER_RIGHT);
            label.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS); // keep the end of long expressions visible
        }

        VBox display = new VBox(2, indicators, expression, result);
        display.getStyleClass().add("display");
        return display;
    }

    private static Label indicator() {
        Label label = new Label();
        label.getStyleClass().add("indicator");
        return label;
    }

    // ---- keypads ----------------------------------------------------------------------------

    private Keypad buildKeypad(Layout layout) {
        return switch (layout) {
            case BASIC -> basicKeypad();
            case SCIENTIFIC -> scientificKeypad(false);
            case ADVANCED -> scientificKeypad(true);
        };
    }

    private Keypad basicKeypad() {
        Keypad k = new Keypad(4);
        k.add("MC", MEMORY, calc::memoryClear, calc::hasMemory);
        k.add("MR", MEMORY, calc::memoryRecall, calc::hasMemory);
        k.add("M+", MEMORY, calc::memoryAdd);
        k.add("M−", MEMORY, calc::memorySubtract);

        k.add("%", FUNCTION, () -> calc.postfix("%"), "%");
        k.add("CE", CONTROL, calc::clearEntry, "DELETE");
        k.add("C", CONTROL, calc::clearAll, "ESCAPE");
        k.add("⌫", CONTROL, calc::backspace, "BACK_SPACE");

        k.add("1/x", FUNCTION, () -> calc.postfix("⁻¹"), "i");
        k.add("x²", FUNCTION, () -> calc.postfix("²"), "q");
        k.add("√x", FUNCTION, () -> calc.root("√"), "r");
        k.add("÷", OPERATOR, () -> calc.operator("÷"), "/");

        digitRow(k, 7, 8, 9);
        k.add("×", OPERATOR, () -> calc.operator("×"), "*");
        digitRow(k, 4, 5, 6);
        k.add("−", OPERATOR, () -> calc.operator("−"), "-");
        digitRow(k, 1, 2, 3);
        k.add("+", OPERATOR, () -> calc.operator("+"), "+");

        k.add("±", DIGIT, calc::negate, "F9");
        digitRow(k, 0);
        k.add(".", DIGIT, calc::decimalPoint, ".");
        k.add("=", EQUALS, calc::evaluate, "=", "ENTER");
        return k.build();
    }

    /**
     * Scientific (Basic) is 5 columns. Scientific (Advanced) adds 2 columns of extra functions on the left,
     * so both layouts are built row by row from the same list.
     */
    private Keypad scientificKeypad(boolean advanced) {
        Keypad k = new Keypad(advanced ? 7 : 5);
        List<Consumer<Keypad>> extras = advanced ? advancedExtras() : List.of();
        List<Consumer<Keypad>> rows = scientificRows();
        for (int i = 0; i < rows.size(); i++) {
            if (advanced) {
                extras.get(i).accept(k);
            }
            rows.get(i).accept(k);
        }
        return k.build();
    }

    private List<Consumer<Keypad>> scientificRows() {
        return List.of(
                k -> {
                    k.add("MC", MEMORY, calc::memoryClear, calc::hasMemory);
                    k.add("MR", MEMORY, calc::memoryRecall, calc::hasMemory);
                    k.add("M+", MEMORY, calc::memoryAdd);
                    k.add("M−", MEMORY, calc::memorySubtract);
                    k.add("MS", MEMORY, calc::memoryStore);
                },
                k -> {
                    k.toggle("2nd", () -> second = !second, () -> second);
                    k.add(() -> calc.angleUnit().label(), CONTROL,
                            () -> calc.setAngleUnit(calc.angleUnit().next()), null, "d");
                    k.add("CE", CONTROL, calc::clearEntry, "DELETE");
                    k.add("C", CONTROL, calc::clearAll, "ESCAPE");
                    k.add("⌫", CONTROL, calc::backspace, "BACK_SPACE");
                },
                k -> {
                    trig(k, "sin", "s");
                    trig(k, "cos", "c");
                    trig(k, "tan", "t");
                    k.add("(", FUNCTION, calc::openParenthesis, "(");
                    k.add(")", FUNCTION, calc::closeParenthesis, ")");
                },
                k -> {
                    k.add(() -> second ? "x³" : "x²", FUNCTION,
                            () -> calc.postfix(second ? "³" : "²"), null, "q");
                    k.add(() -> second ? "ʸ√x" : "xʸ", FUNCTION,
                            () -> calc.operator(second ? " yroot " : "^"), null, "^");
                    k.add(() -> second ? "∛x" : "√x", FUNCTION,
                            () -> calc.root(second ? "∛" : "√"), null, "r");
                    k.add(() -> second ? "eˣ" : "ln", FUNCTION,
                            () -> { if (second) calc.powerOf("e"); else calc.function("ln"); }, null, "l");
                    k.add(() -> second ? "10ˣ" : "log", FUNCTION,
                            () -> { if (second) calc.powerOf("10"); else calc.function("log"); }, null, "g");
                },
                k -> {
                    k.add("1/x", FUNCTION, () -> calc.postfix("⁻¹"), "i");
                    k.add("n!", FUNCTION, () -> calc.postfix("!"), "!");
                    k.add("π", FUNCTION, () -> calc.constant("π"), "p");
                    k.add("e", FUNCTION, () -> calc.constant("e"), "e");
                    k.add("÷", OPERATOR, () -> calc.operator("÷"), "/");
                },
                k -> {
                    k.add("%", FUNCTION, () -> calc.postfix("%"), "%");
                    digitRow(k, 7, 8, 9);
                    k.add("×", OPERATOR, () -> calc.operator("×"), "*");
                },
                k -> {
                    k.add("EE", FUNCTION, calc::exponent, "E");
                    digitRow(k, 4, 5, 6);
                    k.add("−", OPERATOR, () -> calc.operator("−"), "-");
                },
                k -> {
                    k.add("Ans", FUNCTION, () -> calc.constant("Ans"), "a");
                    digitRow(k, 1, 2, 3);
                    k.add("+", OPERATOR, () -> calc.operator("+"), "+");
                },
                k -> {
                    k.add("|x|", FUNCTION, () -> calc.function("abs"), "|");
                    k.add("±", DIGIT, calc::negate, "F9");
                    digitRow(k, 0);
                    k.add(".", DIGIT, calc::decimalPoint, ".");
                    k.add("=", EQUALS, calc::evaluate, "=", "ENTER");
                });
    }

    /** Two extra columns for Scientific (Advanced), one pair of buttons per row. */
    private List<Consumer<Keypad>> advancedExtras() {
        return List.of(
                k -> {
                    k.toggle("hyp", () -> hyp = !hyp, () -> hyp, "h");
                    k.add("rand", FUNCTION, () -> calc.constant("rand"));
                },
                k -> {
                    k.add("nPr", FUNCTION, () -> calc.operator(" P "));
                    k.add("nCr", FUNCTION, () -> calc.operator(" C "));
                },
                k -> {
                    k.add("mod", FUNCTION, () -> calc.operator(" mod "), "m");
                    k.add(",", FUNCTION, calc::comma, ",");
                },
                k -> {
                    k.add("x³", FUNCTION, () -> calc.postfix("³"));
                    k.add("∛x", FUNCTION, () -> calc.root("∛"));
                },
                k -> {
                    k.add("eˣ", FUNCTION, () -> calc.powerOf("e"));
                    k.add("10ˣ", FUNCTION, () -> calc.powerOf("10"));
                },
                k -> {
                    k.add("log₂", FUNCTION, () -> calc.function("log₂"));
                    k.add("2ˣ", FUNCTION, () -> calc.powerOf("2"));
                },
                k -> {
                    k.add("logᵧx", FUNCTION, () -> calc.multiArgumentFunction("log"))
                            .setTooltip(new Tooltip("Logarithm to any base: log(x, base)"));
                    k.add("gcd", FUNCTION, () -> calc.multiArgumentFunction("gcd"))
                            .setTooltip(new Tooltip("Greatest common divisor: gcd(a, b)"));
                },
                k -> {
                    k.add("lcm", FUNCTION, () -> calc.multiArgumentFunction("lcm"))
                            .setTooltip(new Tooltip("Least common multiple: lcm(a, b)"));
                    k.add("floor", FUNCTION, () -> calc.function("floor"));
                },
                k -> {
                    k.add("ceil", FUNCTION, () -> calc.function("ceil"));
                    k.add("round", FUNCTION, () -> calc.function("round"));
                });
    }

    /** A trig key whose label and function follow the 2nd and hyp toggles: sin, sin⁻¹, sinh, sinh⁻¹. */
    private void trig(Keypad k, String base, String shortcut) {
        Supplier<String> name = () -> base + (hyp ? "h" : "") + (second ? "⁻¹" : "");
        k.add(name, FUNCTION, () -> calc.function(name.get()), null, shortcut);
    }

    private void digitRow(Keypad k, int... digits) {
        for (int d : digits) {
            k.add(String.valueOf(d), DIGIT, () -> calc.digit(d), String.valueOf(d));
        }
    }

    private static void flash(Button button) {
        button.getStyleClass().add("flash");
        PauseTransition pause = new PauseTransition(Duration.millis(120));
        pause.setOnFinished(e -> button.getStyleClass().remove("flash"));
        pause.play();
    }

    private static String describeKey(String key) {
        return switch (key) {
            case "ENTER" -> "Enter";
            case "BACK_SPACE" -> "Backspace";
            case "ESCAPE" -> "Esc";
            case "DELETE" -> "Delete";
            default -> key;
        };
    }

    // ---- building blocks --------------------------------------------------------------------

    /** A grid of buttons filled left-to-right, top-to-bottom, with their keyboard shortcuts. */
    private final class Keypad {
        final GridPane grid = new GridPane();
        final Map<String, CalcButton> shortcuts = new HashMap<>();
        final List<CalcButton> buttons = new ArrayList<>();
        private final int columns;

        Keypad(int columns) {
            this.columns = columns;
            grid.getStyleClass().add("keypad");
            for (int c = 0; c < columns; c++) {
                ColumnConstraints column = new ColumnConstraints();
                column.setPercentWidth(100.0 / columns);
                column.setHgrow(Priority.ALWAYS);
                grid.getColumnConstraints().add(column);
            }
        }

        CalcButton add(String label, String style, Runnable action, String... keys) {
            return add(() -> label, style, action, null, keys);
        }

        CalcButton add(String label, String style, Runnable action, BooleanSupplier enabled) {
            return add(() -> label, style, action, enabled);
        }

        CalcButton add(Supplier<String> label, String style, Runnable action, BooleanSupplier enabled,
                       String... keys) {
            CalcButton button = new CalcButton(label, action, enabled);
            button.getStyleClass().add(style);
            int index = buttons.size();
            grid.add(button, index % columns, index / columns);
            buttons.add(button);
            for (String key : keys) {
                shortcuts.put(key, button);
            }
            if (keys.length > 0) {
                button.setTooltip(new Tooltip("Keyboard: " + Arrays.stream(keys)
                        .map(CalculatorView::describeKey)
                        .collect(Collectors.joining(" or "))));
            }
            return button;
        }

        /** A key that switches a mode on and off and is highlighted while on. */
        void toggle(String label, Runnable flip, BooleanSupplier on, String... keys) {
            add(label, CONTROL, flip, keys).active = on;
        }

        Keypad build() {
            int rows = (buttons.size() + columns - 1) / columns;
            for (int r = 0; r < rows; r++) {
                RowConstraints row = new RowConstraints();
                row.setPercentHeight(100.0 / rows);
                row.setVgrow(Priority.ALWAYS);
                grid.getRowConstraints().add(row);
            }
            return this;
        }
    }

    /** A keypad button whose label (and enabled/highlighted state) can change, e.g. with 2nd. */
    private final class CalcButton extends Button {
        private final Supplier<String> label;
        private final BooleanSupplier enabled;
        BooleanSupplier active;

        CalcButton(Supplier<String> label, Runnable action, BooleanSupplier enabled) {
            this.label = label;
            this.enabled = enabled;
            getStyleClass().add("calc-button");
            setFocusTraversable(false); // keeps Enter/Space from re-pressing the last clicked button
            setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            setMinSize(40, 32);
            setOnAction(e -> {
                action.run();
                refresh();
            });
            update();
        }

        void update() {
            setText(label.get());
            setDisable(enabled != null && !enabled.getAsBoolean());
            boolean on = active != null && active.getAsBoolean();
            if (on != getStyleClass().contains("on")) {
                if (on) {
                    getStyleClass().add("on");
                } else {
                    getStyleClass().remove("on");
                }
            }
        }
    }
}
