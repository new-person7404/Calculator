package com.example.calculator;

import com.example.calculator.engine.Calculator;
import com.example.calculator.ui.CalculatorView;
import com.example.calculator.ui.ConverterView;
import java.util.EnumMap;
import java.util.Map;
import java.util.prefs.Preferences;
import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class App extends Application {

    /** The four screens, in menu order. */
    enum Mode {
        BASIC("Basic", "Shortcut+1"),
        SCIENTIFIC("Scientific (Basic)", "Shortcut+2"),
        ADVANCED("Scientific (Advanced)", "Shortcut+3"),
        CONVERTER("Unit converter", "Shortcut+4");

        final String title;
        final String accelerator;

        Mode(String title, String accelerator) {
            this.title = title;
            this.accelerator = accelerator;
        }
    }

    /** Colour themes. Each non-Light theme is a stylesheet loaded on top of styles.css. */
    enum Theme {
        LIGHT("Light", null),
        DARK("Dark", "dark.css"),
        COLOURFUL("Colourful", "colourful.css");

        final String title;
        final String stylesheet;

        Theme(String title, String stylesheet) {
            this.title = title;
            this.stylesheet = stylesheet;
        }

        Theme next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static final String MODE_PREFERENCE = "mode";
    private static final String THEME_PREFERENCE = "theme";

    private static final String SHORTCUTS = """
            Calculator modes
              0–9  .           digits and decimal point
              + - * /  ^       operators (^ is power)
              ( )  %  !        parentheses, percent, factorial
              Enter or =       calculate
              Backspace        delete last entry
              Delete           clear entry (CE)
              Esc              clear all (C)
              F9               change sign (±)
              q  r  i          x², √x, 1/x

            Scientific modes
              s  c  t          sin, cos, tan
              l  g             ln, log
              p  e             π, e
              E                EE (scientific notation, 3E8)
              a                Ans (last answer)
              d                cycle DEG / RAD / GRAD
              |                absolute value
            Scientific (Advanced) also
              h                hyp
              m  ,             mod, argument separator

            Everywhere
              Ctrl+1 … Ctrl+4  switch mode
              Ctrl+T           next theme (Light, Dark, Colourful)
              Ctrl+C / Ctrl+V  copy result / paste
              F1               this help

            Hover over any button to see its key.
            """;

    private final Calculator calculator = new Calculator();
    private final BorderPane root = new BorderPane();
    private final Label header = new Label();
    private final Map<Mode, RadioMenuItem> modeItems = new EnumMap<>(Mode.class);
    private final Map<Theme, RadioMenuItem> themeItems = new EnumMap<>(Theme.class);
    private final Preferences preferences = Preferences.userNodeForPackage(App.class);
    private CalculatorView calculatorView;
    private ConverterView converterView;
    private Stage stage;
    private Scene scene;
    private Mode mode;
    private Theme theme;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        calculatorView = new CalculatorView(calculator);
        converterView = new ConverterView();

        header.getStyleClass().add("mode-header");
        root.getStyleClass().add("app");
        root.setTop(new VBox(buildMenuBar(), header));

        scene = new Scene(root);
        setTheme(savedTheme());
        scene.addEventFilter(KeyEvent.KEY_TYPED, this::onKeyTyped);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);

        stage.setTitle("Calculator");
        for (int size : new int[] {16, 32, 48, 64, 128, 256}) {
            stage.getIcons().add(new Image(resource("icons/icon-" + size + ".png")));
        }
        stage.setScene(scene);
        setMode(savedMode());
        stage.show();
    }

    private void setMode(Mode newMode) {
        mode = newMode;
        modeItems.get(newMode).setSelected(true);
        header.setText(newMode.title);
        switch (newMode) {
            case BASIC -> showCalculator(CalculatorView.Layout.BASIC);
            case SCIENTIFIC -> showCalculator(CalculatorView.Layout.SCIENTIFIC);
            case ADVANCED -> showCalculator(CalculatorView.Layout.ADVANCED);
            case CONVERTER -> {
                root.setCenter(converterView);
                converterView.focusInput();
            }
        }
        // Resize the window to suit the new keypad.
        // Sizing again after the next pulse catches keypads whose CSS hadn't been applied yet.
        root.applyCss();
        root.layout();
        stage.sizeToScene();
        Platform.runLater(stage::sizeToScene);
        preferences.put(MODE_PREFERENCE, newMode.name());
    }

    /**
     * Swaps stylesheets rather than style classes, so popups (menus, combo-box lists) and dialogs,
     * which copy the scene's stylesheets, follow the theme as well.
     */
    private void setTheme(Theme newTheme) {
        theme = newTheme;
        themeItems.get(newTheme).setSelected(true);
        scene.getStylesheets().setAll(resource("styles.css"));
        if (newTheme.stylesheet != null) {
            scene.getStylesheets().add(resource(newTheme.stylesheet));
        }
        preferences.put(THEME_PREFERENCE, newTheme.name());
    }

    /** URL of a file in this package's resources (stylesheets, icons). */
    private static String resource(String name) {
        return App.class.getResource(name).toExternalForm();
    }

    /** The theme chosen last time; on first run, Dark if the OS is in dark mode, otherwise Light. */
    private Theme savedTheme() {
        String saved = preferences.get(THEME_PREFERENCE, null);
        if (saved != null) {
            try {
                return Theme.valueOf(saved);
            } catch (IllegalArgumentException e) {
                // fall through to the OS setting
            }
        }
        return Platform.getPreferences().getColorScheme() == ColorScheme.DARK ? Theme.DARK : Theme.LIGHT;
    }

    private void showCalculator(CalculatorView.Layout layout) {
        calculatorView.setLayout(layout);
        root.setCenter(calculatorView);
        root.requestFocus(); // take focus away from the converter's text box
    }

    private Mode savedMode() {
        try {
            return Mode.valueOf(preferences.get(MODE_PREFERENCE, Mode.BASIC.name()));
        } catch (IllegalArgumentException e) {
            return Mode.BASIC;
        }
    }

    // ---- keyboard ---------------------------------------------------------------------------

    private void onKeyTyped(KeyEvent e) {
        if (mode == Mode.CONVERTER) {
            converterView.handleKeyTyped(e);
            return;
        }
        // Leave Ctrl/Alt shortcuts to the menus. Ctrl+Alt together is AltGr, which types characters.
        if (e.isControlDown() != e.isAltDown() || e.isMetaDown()) {
            return;
        }
        if (calculatorView.handleKey(e.getCharacter())) {
            e.consume();
        }
    }

    private void onKeyPressed(KeyEvent e) {
        if (e.isControlDown() || e.isAltDown() || e.isMetaDown()) {
            return;
        }
        KeyCode code = e.getCode();
        if (mode == Mode.CONVERTER) {
            if (code == KeyCode.ESCAPE) {
                converterView.clearInput();
                e.consume();
            }
            return;
        }
        boolean special = switch (code) {
            case ENTER, BACK_SPACE, ESCAPE, DELETE, F9 -> true;
            default -> false;
        };
        if (special && calculatorView.handleKey(code.name())) {
            e.consume();
        }
    }

    // ---- menus ------------------------------------------------------------------------------

    private MenuBar buildMenuBar() {
        Menu modeMenu = new Menu("_Mode");
        ToggleGroup group = new ToggleGroup();
        for (Mode m : Mode.values()) {
            RadioMenuItem item = new RadioMenuItem(m.title);
            item.setToggleGroup(group);
            item.setAccelerator(KeyCombination.keyCombination(m.accelerator));
            item.setOnAction(e -> setMode(m));
            modeItems.put(m, item);
            modeMenu.getItems().add(item);
            if (m == Mode.ADVANCED) {
                modeMenu.getItems().add(new SeparatorMenuItem());
            }
        }

        MenuItem copy = new MenuItem("_Copy result");
        copy.setAccelerator(KeyCombination.keyCombination("Shortcut+C"));
        copy.setOnAction(e -> copy());
        MenuItem paste = new MenuItem("_Paste");
        paste.setAccelerator(KeyCombination.keyCombination("Shortcut+V"));
        paste.setOnAction(e -> paste());
        Menu editMenu = new Menu("_Edit", null, copy, paste);

        Menu viewMenu = new Menu("_View");
        ToggleGroup themeGroup = new ToggleGroup();
        for (Theme t : Theme.values()) {
            RadioMenuItem item = new RadioMenuItem(t.title + " theme");
            item.setToggleGroup(themeGroup);
            item.setOnAction(e -> setTheme(t));
            themeItems.put(t, item);
            viewMenu.getItems().add(item);
        }
        MenuItem nextTheme = new MenuItem("_Next theme");
        nextTheme.setAccelerator(KeyCombination.keyCombination("Shortcut+T"));
        nextTheme.setOnAction(e -> setTheme(theme.next()));
        viewMenu.getItems().addAll(new SeparatorMenuItem(), nextTheme);

        MenuItem shortcuts = new MenuItem("_Keyboard shortcuts");
        shortcuts.setAccelerator(KeyCombination.keyCombination("F1"));
        shortcuts.setOnAction(e -> showShortcuts());
        Menu helpMenu = new Menu("_Help", null, shortcuts);

        return new MenuBar(modeMenu, editMenu, viewMenu, helpMenu);
    }

    private void copy() {
        String text = mode == Mode.CONVERTER ? converterView.resultText() : calculator.copyText();
        if (!text.isEmpty()) {
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    private void paste() {
        String text = Clipboard.getSystemClipboard().getString();
        if (text == null) {
            return;
        }
        if (mode == Mode.CONVERTER) {
            converterView.paste(text);
        } else {
            calculator.paste(text);
            calculatorView.refresh();
        }
    }

    private void showShortcuts() {
        TextArea text = new TextArea(SHORTCUTS);
        text.setEditable(false);
        text.getStyleClass().add("shortcuts");
        text.setPrefRowCount(32);
        text.setPrefColumnCount(48);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle("Keyboard shortcuts");
        alert.setHeaderText("Keyboard shortcuts");
        alert.getDialogPane().setContent(text);
        alert.getDialogPane().getStylesheets().addAll(root.getScene().getStylesheets());
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
