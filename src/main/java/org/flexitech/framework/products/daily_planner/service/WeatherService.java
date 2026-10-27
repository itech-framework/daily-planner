package org.flexitech.framework.products.daily_planner.service;

import org.flexitech.framework.products.daily_planner.model.WeatherSnapshot;

public interface WeatherService {

	WeatherSnapshot getWeather(boolean refresh) throws Exception;
}