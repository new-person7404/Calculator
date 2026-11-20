package com.example.calculator.ui;

import com.example.calculator.convert.Unit;
import com.example.calculator.convert.UnitCategory;
import com.example.calculator.convert.Units;
import com.example.calculator.engine.AngleUnit;
import com.example.calculator.engine.CalculationException;
import com.example.calculator.engine.ExpressionEvaluator;
import com.example.calculator.engine.NumberFormatter;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;

/**
 * Unit converter: pick a category and two units, type a value. The value may also be a simple
 * expression such as {@code 5*12} or {@code 1/3}. All units of the category are listed on the right.
 */
public final class ConverterView extends HBox {

    /** Characters allowed in the input box: numbers and simple arithmetic. */
    private static final Pattern INPUT_CHARS = Pattern.compile("[0-9.,+\\-−*×/÷^()%Eπ ]*");
    private static final ExpressionEvaluator.Context CONTEXT =
            new ExpressionEvaluator.Context(AngleUnit.RADIANS, 0, Math::random);

    private final ComboBox<UnitCategory> category = new ComboBox<>(FXCollections.observableList(Units.CATEGORIES));
    private final TextField input = new TextField("1");
    private final ComboBox<Unit> fromUnit = new ComboBox<>();
    private final ComboBox<Unit> toUnit = new ComboBox<>();
    private final TextField output = new TextField();
    private final Label formula = new Label();
    private final ListView<Unit> allUnits = new ListView<>();
    private Double value; // the parsed input, or null if it isn't a number

    public ConverterView() {
        getStyleClass().add("converter");
        setSpacing(16);

        VBox form = new VBox(6,
                caption("Category"), category,
                caption("From"), input, fromUnit,
                swapButton(),
                caption("To"), output, toUnit,
                formula,
                keypad());
        form.getStyleClass().add("converter-form");
        form.setPrefWidth(300);
        form.setMinWidth(260);
        for (Region r : new Region[] {category, fromUnit, toUnit, input, output}) {
            r.setMaxWidth(Double.MAX_VALUE);
        }

        input.getStyleClass().add("converter-input");
        input.setTextFormatter(new TextFormatter<>((UnaryOperator<TextFormatter.Change>) change ->
                INPUT_CHARS.matcher(change.getControlNewText()).matches() ? change : null));
        input.setOnAction(e -> normalizeInput());
        output.getStyleClass().add("converter-output");
        output.setEditable(false);
        formula.getStyleClass().add("formula");
        formula.setWrapText(true);

        allUnits.setCellFactory(list -> new AllUnitsCell());
        allUnits.setOnMouseClicked(e -> {
            Unit picked = allUnits.getSelectionModel().getSelectedItem();
            if (picked != null) {
                toUnit.setValue(picked);
            }
        });
        allUnits.setTooltip(new Tooltip("Click a unit to convert to it"));
        VBox.setVgrow(allUnits, Priority.ALWAYS);
        VBox list = new VBox(6, caption("All units"), allUnits);
        list.setPrefWidth(320);
        HBox.setHgrow(list, Priority.ALWAYS);
        getChildren().addAll(form, list);

        category.valueProperty().addListener((obs, old, c) -> selectCategory(c));
        input.textProperty().addListener((obs, old, text) -> recalculate());
        fromUnit.valueProperty().addListener((obs, old, u) -> recalculate());
        toUnit.valueProperty().addListener((obs, old, u) -> recalculate());
        category.setValue(Units.CATEGORIES.getFirst());
    }

    /** Puts the cursor in the input box (called when the converter is shown). */
    public void focusInput() {
        input.requestFocus();
        input.end();
    }

    /** Sends typed characters to the input box even when another control (e.g. a list) has focus. */
    public void handleKeyTyped(KeyEvent e) {
        String ch = e.getCharacter();
        if (getScene() == null || getScene().getFocusOwner() == input || ch.isEmpty()) {
            return;
        }
        if ("0123456789.-+*/^()%".contains(ch) && !(getScene().getFocusOwner() instanceof TextField)) {
            input.requestFocus();
            input.end();
            input.appendText(ch);
            e.consume();
        }
    }

    public void clearInput() {
        input.clear();
        input.requestFocus();
    }

    /** The converted value in plain form, for the clipboard. */
    public String resultText() {
        Double converted = converted(toUnit.getValue());
        return converted == null ? "" : NumberFormatter.format(converted);
    }

    public void paste(String text) {
        String cleaned = text.strip().replace(",", "");
        if (INPUT_CHARS.matcher(cleaned).matches()) {
            input.setText(cleaned);
            input.end();
        }
    }

    // ---- behaviour --------------------------------------------------------------------------

    private void selectCategory(UnitCategory c) {
        fromUnit.setItems(FXCollections.observableList(c.units()));
        toUnit.setItems(FXCollections.observableList(c.units()));
        allUnits.setItems(FXCollections.observableList(c.units()));
        fromUnit.setValue(c.defaultFrom());
        toUnit.setValue(c.defaultTo());
        recalculate();
    }

    private void swapUnits() {
        Unit from = fromUnit.getValue();
        fromUnit.setValue(toUnit.getValue());
        toUnit.setValue(from);
    }

    /** Enter in the input box: replaces an expression such as "5*12" with its value. */
    private void normalizeInput() {
        if (value != null) {
            input.setText(NumberFormatter.format(value));
            input.end();
        }
    }

    private void recalculate() {
        value = parse(input.getText());
        Unit from = fromUnit.getValue();
        Unit to = toUnit.getValue();
        if (from == null || to == null) {
            return;
        }
        Double converted = converted(to);
        output.setText(converted == null ? "" : NumberFormatter.toDisplay(converted));
        if (value == null) {
            formula.setText(input.getText().isBlank() ? "Enter a value to convert" : "Not a valid number");
        } else {
            formula.setText("1 " + from.symbol() + " = " + display(Units.convert(1, from, to)) + " " + to.symbol());
        }
        allUnits.refresh();
    }

    private Double converted(Unit to) {
        if (value == null || fromUnit.getValue() == null || to == null) {
            return null;
        }
        double result = Units.convert(value, fromUnit.getValue(), to);
        return Double.isFinite(result) ? result : null;
    }

    private static Double parse(String text) {
        String cleaned = text.replace(",", "").strip();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return ExpressionEvaluator.evaluate(cleaned, CONTEXT);
        } catch (CalculationException e) {
            return null;
        }
    }

    private static String display(double v) {
        return Double.isFinite(v) ? NumberFormatter.toDisplay(v) : "—";
    }

    // ---- building blocks --------------------------------------------------------------------

    private static Label caption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("caption");
        return label;
    }

    private Button swapButton() {
        Button swap = keypadButton("⇅ Swap units", "control", this::swapUnits);
        swap.setMaxWidth(Double.MAX_VALUE);
        swap.setMinHeight(32);
        swap.setPrefHeight(34);
        swap.setMaxHeight(34);
        return swap;
    }

    private GridPane keypad() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("keypad");
        for (int c = 0; c < 4; c++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(25);
            grid.getColumnConstraints().add(column);
        }
        for (int r = 0; r < 4; r++) {
            RowConstraints row = new RowConstraints();
            row.setPercentHeight(25);
            row.setVgrow(Priority.ALWAYS);
            grid.getRowConstraints().add(row);
        }
        String[][] digits = {{"7", "8", "9"}, {"4", "5", "6"}, {"1", "2", "3"}};
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                String d = digits[r][c];
                grid.add(keypadButton(d, "digit", () -> type(d)), c, r);
            }
        }
        grid.add(keypadButton("⌫", "control", this::backspace), 3, 0);
        grid.add(keypadButton("C", "control", this::clearInput), 3, 1);
        grid.add(keypadButton("±", "digit", this::negate), 3, 2);
        Button zero = keypadButton("0", "digit", () -> type("0"));
        grid.add(zero, 0, 3, 2, 1);
        grid.add(keypadButton(".", "digit", () -> type(".")), 2, 3);
        grid.add(keypadButton("⇅", "control", this::swapUnits), 3, 3);
        grid.setPrefHeight(200);
        VBox.setVgrow(grid, Priority.ALWAYS);
        return grid;
    }

    private Button keypadButton(String text, String style, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll("calc-button", style);
        button.setFocusTraversable(false);
        button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        button.setMinSize(40, 32);
        button.setOnAction(e -> action.run());
        return button;
    }

    private void type(String text) {
        input.replaceSelection(text);
    }

    private void backspace() {
        if (input.getSelection().getLength() > 0) {
            input.replaceSelection("");
        } else {
            input.deletePreviousChar();
        }
    }

    private void negate() {
        String text = input.getText();
        if (text.startsWith("-") || text.startsWith("−")) {
            input.setText(text.substring(1));
        } else {
            input.setText("-" + text);
        }
        input.end();
    }

    /** One row of the "All units" list: the converted value, then the unit's name. */
    private final class AllUnitsCell extends ListCell<Unit> {
        private final Label amount = new Label();
        private final Label name = new Label();
        private final HBox row = new HBox(8, amount, name);

        AllUnitsCell() {
            amount.getStyleClass().add("amount");
            name.getStyleClass().add("unit-name");
        }

        @Override
        protected void updateItem(Unit unit, boolean empty) {
            super.updateItem(unit, empty);
            if (empty || unit == null) {
                setGraphic(null);
                return;
            }
            Double converted = converted(unit);
            amount.setText((converted == null ? "—" : NumberFormatter.toDisplay(converted)) + " " + unit.symbol());
            name.setText(unit.name());
            setGraphic(row);
        }
    }
}
