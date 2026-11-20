package com.example.calculator.convert;

import java.util.List;

/** A group of interchangeable units, plus the pair selected when the category is first opened. */
public record UnitCategory(String name, List<Unit> units, Unit defaultFrom, Unit defaultTo) {

    public UnitCategory {
        units = List.copyOf(units);
        if (!units.contains(defaultFrom) || !units.contains(defaultTo)) {
            throw new IllegalArgumentException("Default units must belong to " + name);
        }
    }

    /** Looks up a unit by symbol; mainly for tests. */
    public Unit unit(String symbol) {
        return units.stream()
                .filter(u -> u.symbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(name + " has no unit " + symbol));
    }

    @Override
    public String toString() {
        return name;
    }
}
