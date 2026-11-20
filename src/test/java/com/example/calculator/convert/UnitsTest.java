package com.example.calculator.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.calculator.engine.NumberFormatter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UnitsTest {

    private static UnitCategory category(String name) {
        return Units.CATEGORIES.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    @ParameterizedTest(name = "{1} {2} = {4} {3}")
    @CsvSource({
            "Length,       1,       in,       cm,       2.54",
            "Length,       1,       mi,       km,       1.609344",
            "Length,       3,       ft,       yd,       1",
            "Weight,       1,       kg,       lb,       2.20462262185",
            "Weight,       16,      oz,       lb,       1",
            "Area,         1,       ha,       m²,       10000",
            "Area,         640,     ac,       mi²,      1",
            "Volume,       1,       gal US,   L,        3.785411784",
            "Volume,       1,       gal UK,   pt UK,    8",
            "Speed,        100,     km/h,     m/s,      27.7777777778",
            "Speed,        60,      mph,      km/h,     96.56064",
            "Temperature,  100,     °C,       °F,       212",
            "Temperature,  -40,     °F,       °C,       -40",
            "Temperature,  0,       K,        °C,       -273.15",
            "Time,         1,       d,        min,      1440",
            "Data,         1,       GiB,      MiB,      1024",
            "Data,         8,       bit,      B,        1",
            "Pressure,     1,       atm,      Pa,       101325",
            "Energy,       1,       kWh,      kJ,       3600",
            "Power,        1,       kW,       W,        1000",
            "Angle,        180,     °,        rad,      3.14159265359",
            "Frequency,    60,      rpm,      Hz,       1",
            "Fuel economy, 5,       L/100 km, km/L,     20",
            "Fuel economy, 20,      km/L,     L/100 km, 5",
    })
    void converts(String category, double value, String from, String to, String expected) {
        UnitCategory c = category(category);
        assertEquals(expected, NumberFormatter.format(Units.convert(value, c.unit(from), c.unit(to))));
    }

    @ParameterizedTest
    @CsvSource({"Length", "Weight", "Area", "Volume", "Speed", "Temperature", "Time", "Data", "Pressure",
            "Energy", "Power", "Angle", "Frequency", "Fuel economy"})
    void everyUnitRoundTrips(String name) {
        UnitCategory c = category(name);
        for (Unit a : c.units()) {
            for (Unit b : c.units()) {
                double there = Units.convert(7.5, a, b);
                double back = Units.convert(there, b, a);
                assertTrue(Math.abs(back - 7.5) < 1e-9, () -> a + " → " + b + " → " + a + " gave " + back);
            }
        }
    }
}
