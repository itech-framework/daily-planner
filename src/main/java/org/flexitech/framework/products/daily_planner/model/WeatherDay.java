package org.flexitech.framework.products.daily_planner.model;

import java.time.LocalDate;

public record WeatherDay(LocalDate date, double max, double min, int code, int rainChance) {
}