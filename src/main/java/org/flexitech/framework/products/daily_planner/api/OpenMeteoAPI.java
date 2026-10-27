package org.flexitech.framework.products.daily_planner.api;

import org.flexitech.framework.products.daily_planner.api.response.OpenMeteoResponse;

import io.github.itech_framework.api_client.annotations.ApiClient;
import io.github.itech_framework.api_client.annotations.methods.GET;
import io.github.itech_framework.api_client.annotations.parameters.Query;

@ApiClient(baseUrl = "https://api.open-meteo.com")
public interface OpenMeteoAPI {

	@GET("/v1/forecast")
	OpenMeteoResponse forecast(@Query("latitude") String latitude,
			@Query("longitude") String longitude,
			@Query("current") String current,
			@Query("daily") String daily,
			@Query("timezone") String timezone,
			@Query("forecast_days") String forecastDays);
}