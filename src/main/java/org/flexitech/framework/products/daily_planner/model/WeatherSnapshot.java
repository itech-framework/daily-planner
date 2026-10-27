package org.flexitech.framework.products.daily_planner.model;

import java.time.LocalDateTime;
import java.util.List;

public record WeatherSnapshot(String location, WeatherCurrent current, List<WeatherDay> days,
		boolean locationUncertain, LocalDateTime loadedAt) {
}