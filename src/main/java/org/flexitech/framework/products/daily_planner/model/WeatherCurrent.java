package org.flexitech.framework.products.daily_planner.model;

public record WeatherCurrent(double temperature, double feelsLike, int humidity, double windSpeed, int code,
		boolean day) {
}