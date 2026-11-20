package com.example.calculator.engine;

/** The unit trigonometric functions take (and inverse functions return) angles in. */
public enum AngleUnit {
    DEGREES("DEG", 360),
    RADIANS("RAD", 2 * Math.PI),
    GRADIANS("GRAD", 400);

    private final String label;
    private final double fullTurn;

    AngleUnit(String label, double fullTurn) {
        this.label = label;
        this.fullTurn = fullTurn;
    }

    public String label() {
        return label;
    }

    public double toRadians(double angle) {
        return this == RADIANS ? angle : angle * (2 * Math.PI / fullTurn);
    }

    public double fromRadians(double radians) {
        return this == RADIANS ? radians : radians * (fullTurn / (2 * Math.PI));
    }

    /** Half a turn in this unit (180°, π rad, 200 grad). */
    double halfTurn() {
        return fullTurn / 2;
    }

    /** The next unit in DEG → RAD → GRAD → DEG order. */
    public AngleUnit next() {
        AngleUnit[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
