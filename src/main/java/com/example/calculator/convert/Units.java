package com.example.calculator.convert;

import static com.example.calculator.convert.Unit.linear;

import java.util.List;

/**
 * All unit categories. Factors are exact by definition unless noted (for example 1 inch = 0.0254 m exactly).
 * US customary volumes are used where US and imperial differ, with imperial ones listed separately.
 */
public final class Units {

    public static final List<UnitCategory> CATEGORIES = List.of(
            length(), weight(), area(), volume(), speed(), temperature(), time(),
            data(), pressure(), energy(), power(), angle(), frequency(), fuelEconomy());

    private Units() {
    }

    public static double convert(double value, Unit from, Unit to) {
        return to.fromBase().applyAsDouble(from.toBase().applyAsDouble(value));
    }

    private static UnitCategory category(String name, String fromSymbol, String toSymbol, Unit... units) {
        List<Unit> list = List.of(units);
        Unit from = list.stream().filter(u -> u.symbol().equals(fromSymbol)).findFirst().orElseThrow();
        Unit to = list.stream().filter(u -> u.symbol().equals(toSymbol)).findFirst().orElseThrow();
        return new UnitCategory(name, list, from, to);
    }

    private static UnitCategory length() { // base: metre
        return category("Length", "m", "ft",
                linear("Nanometre", "nm", 1e-9),
                linear("Micrometre", "µm", 1e-6),
                linear("Millimetre", "mm", 1e-3),
                linear("Centimetre", "cm", 1e-2),
                linear("Metre", "m", 1),
                linear("Kilometre", "km", 1e3),
                linear("Inch", "in", 0.0254),
                linear("Foot", "ft", 0.3048),
                linear("Yard", "yd", 0.9144),
                linear("Mile", "mi", 1609.344),
                linear("Nautical mile", "nmi", 1852),
                linear("Astronomical unit", "au", 149_597_870_700.0),
                linear("Light-year", "ly", 9_460_730_472_580_800.0));
    }

    private static UnitCategory weight() { // base: kilogram
        return category("Weight", "kg", "lb",
                linear("Microgram", "µg", 1e-9),
                linear("Milligram", "mg", 1e-6),
                linear("Gram", "g", 1e-3),
                linear("Kilogram", "kg", 1),
                linear("Tonne", "t", 1e3),
                linear("Carat", "ct", 2e-4),
                linear("Ounce", "oz", 0.028349523125),
                linear("Pound", "lb", 0.45359237),
                linear("Stone", "st", 6.35029318),
                linear("Short ton (US)", "ton US", 907.18474),
                linear("Long ton (UK)", "ton UK", 1016.0469088));
    }

    private static UnitCategory area() { // base: square metre
        return category("Area", "m²", "ft²",
                linear("Square millimetre", "mm²", 1e-6),
                linear("Square centimetre", "cm²", 1e-4),
                linear("Square metre", "m²", 1),
                linear("Hectare", "ha", 1e4),
                linear("Square kilometre", "km²", 1e6),
                linear("Square inch", "in²", 0.00064516),
                linear("Square foot", "ft²", 0.09290304),
                linear("Square yard", "yd²", 0.83612736),
                linear("Acre", "ac", 4046.8564224),
                linear("Square mile", "mi²", 2_589_988.110336));
    }

    private static UnitCategory volume() { // base: litre
        return category("Volume", "L", "gal US",
                linear("Millilitre", "mL", 1e-3),
                linear("Litre", "L", 1),
                linear("Cubic metre", "m³", 1e3),
                linear("Cubic inch", "in³", 0.016387064),
                linear("Cubic foot", "ft³", 28.316846592),
                linear("Teaspoon (US)", "tsp", 0.00492892159375),
                linear("Tablespoon (US)", "tbsp", 0.01478676478125),
                linear("Fluid ounce (US)", "fl oz US", 0.0295735295625),
                linear("Cup (US)", "cup", 0.2365882365),
                linear("Pint (US)", "pt US", 0.473176473),
                linear("Quart (US)", "qt US", 0.946352946),
                linear("Gallon (US)", "gal US", 3.785411784),
                linear("Fluid ounce (UK)", "fl oz UK", 0.0284130625),
                linear("Pint (UK)", "pt UK", 0.56826125),
                linear("Gallon (UK)", "gal UK", 4.54609));
    }

    private static UnitCategory speed() { // base: metre per second
        return category("Speed", "km/h", "mph",
                linear("Metre per second", "m/s", 1),
                linear("Kilometre per hour", "km/h", 1 / 3.6),
                linear("Mile per hour", "mph", 0.44704),
                linear("Foot per second", "ft/s", 0.3048),
                linear("Knot", "kn", 1852.0 / 3600));
    }

    private static UnitCategory temperature() { // base: kelvin
        return category("Temperature", "°C", "°F",
                new Unit("Celsius", "°C", c -> c + 273.15, k -> k - 273.15),
                new Unit("Fahrenheit", "°F", f -> (f - 32) * 5 / 9 + 273.15, k -> (k - 273.15) * 9 / 5 + 32),
                new Unit("Kelvin", "K", k -> k, k -> k),
                new Unit("Rankine", "°R", r -> r * 5 / 9, k -> k * 9 / 5));
    }

    private static UnitCategory time() { // base: second
        return category("Time", "h", "min",
                linear("Nanosecond", "ns", 1e-9),
                linear("Microsecond", "µs", 1e-6),
                linear("Millisecond", "ms", 1e-3),
                linear("Second", "s", 1),
                linear("Minute", "min", 60),
                linear("Hour", "h", 3600),
                linear("Day", "d", 86_400),
                linear("Week", "wk", 604_800),
                linear("Month (average)", "mo", 2_629_746),   // 1/12 of a Gregorian year
                linear("Year (average)", "yr", 31_556_952));  // 365.2425 days
    }

    private static UnitCategory data() { // base: byte
        return category("Data", "GB", "MB",
                linear("Bit", "bit", 0.125),
                linear("Byte", "B", 1),
                linear("Kilobit", "kbit", 125),
                linear("Megabit", "Mbit", 125_000),
                linear("Gigabit", "Gbit", 125_000_000),
                linear("Kilobyte", "kB", 1e3),
                linear("Megabyte", "MB", 1e6),
                linear("Gigabyte", "GB", 1e9),
                linear("Terabyte", "TB", 1e12),
                linear("Kibibyte", "KiB", 1024),
                linear("Mebibyte", "MiB", 1024.0 * 1024),
                linear("Gibibyte", "GiB", 1024.0 * 1024 * 1024),
                linear("Tebibyte", "TiB", 1024.0 * 1024 * 1024 * 1024));
    }

    private static UnitCategory pressure() { // base: pascal
        return category("Pressure", "bar", "psi",
                linear("Pascal", "Pa", 1),
                linear("Kilopascal", "kPa", 1e3),
                linear("Megapascal", "MPa", 1e6),
                linear("Millibar", "mbar", 100),
                linear("Bar", "bar", 1e5),
                linear("Atmosphere", "atm", 101_325),
                linear("Pound per square inch", "psi", 6894.757293168361),
                linear("Millimetre of mercury", "mmHg", 133.322387415),
                linear("Inch of mercury", "inHg", 3386.388640341));
    }

    private static UnitCategory energy() { // base: joule
        return category("Energy", "kJ", "kcal",
                linear("Joule", "J", 1),
                linear("Kilojoule", "kJ", 1e3),
                linear("Calorie", "cal", 4.184),
                linear("Kilocalorie", "kcal", 4184),
                linear("Watt-hour", "Wh", 3600),
                linear("Kilowatt-hour", "kWh", 3.6e6),
                linear("Electronvolt", "eV", 1.602176634e-19),
                linear("British thermal unit", "BTU", 1055.05585262),
                linear("Foot-pound", "ft·lbf", 1.3558179483314004));
    }

    private static UnitCategory power() { // base: watt
        return category("Power", "kW", "hp",
                linear("Watt", "W", 1),
                linear("Kilowatt", "kW", 1e3),
                linear("Megawatt", "MW", 1e6),
                linear("Horsepower (mechanical)", "hp", 745.6998715822702),
                linear("Horsepower (metric)", "PS", 735.49875),
                linear("BTU per hour", "BTU/h", 0.2930710701722222),
                linear("Foot-pound per second", "ft·lbf/s", 1.3558179483314004));
    }

    private static UnitCategory angle() { // base: radian
        return category("Angle", "°", "rad",
                linear("Degree", "°", Math.PI / 180),
                linear("Radian", "rad", 1),
                linear("Gradian", "grad", Math.PI / 200),
                linear("Arcminute", "′", Math.PI / 10_800),
                linear("Arcsecond", "″", Math.PI / 648_000),
                linear("Turn", "turn", 2 * Math.PI));
    }

    private static UnitCategory frequency() { // base: hertz
        return category("Frequency", "MHz", "kHz",
                linear("Hertz", "Hz", 1),
                linear("Kilohertz", "kHz", 1e3),
                linear("Megahertz", "MHz", 1e6),
                linear("Gigahertz", "GHz", 1e9),
                linear("Revolutions per minute", "rpm", 1.0 / 60));
    }

    private static UnitCategory fuelEconomy() { // base: kilometres per litre
        double mpgUs = 1.609344 / 3.785411784;
        double mpgUk = 1.609344 / 4.54609;
        return category("Fuel economy", "L/100 km", "mpg US",
                linear("Kilometres per litre", "km/L", 1),
                new Unit("Litres per 100 km", "L/100 km", v -> 100 / v, v -> 100 / v), // inverse: less is better
                linear("Miles per gallon (US)", "mpg US", mpgUs),
                linear("Miles per gallon (UK)", "mpg UK", mpgUk));
    }
}
