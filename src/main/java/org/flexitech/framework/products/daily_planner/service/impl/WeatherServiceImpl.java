package org.flexitech.framework.products.daily_planner.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.framework.products.daily_planner.api.OpenMeteoAPI;
import org.flexitech.framework.products.daily_planner.api.response.OpenMeteoCurrent;
import org.flexitech.framework.products.daily_planner.api.response.OpenMeteoDaily;
import org.flexitech.framework.products.daily_planner.api.response.OpenMeteoResponse;
import org.flexitech.framework.products.daily_planner.model.GeoLocation;
import org.flexitech.framework.products.daily_planner.model.Setting;
import org.flexitech.framework.products.daily_planner.model.WeatherCurrent;
import org.flexitech.framework.products.daily_planner.model.WeatherDay;
import org.flexitech.framework.products.daily_planner.model.WeatherSnapshot;
import org.flexitech.framework.products.daily_planner.service.LocationService;
import org.flexitech.framework.products.daily_planner.service.SettingsService;
import org.flexitech.framework.products.daily_planner.service.WeatherService;
import org.flexitech.framework.products.daily_planner.util.VpnCheck;

import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_framework.core.annotations.dependency.Use;

@Service
public class WeatherServiceImpl implements WeatherService {

	private static final Logger log = LogManager.getLogger(WeatherServiceImpl.class);
	private static final String CURRENT_FIELDS = "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day";
	private static final String DAILY_FIELDS = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max";
	private static final String TIMEZONE = "auto";

	private record CachedWeather(WeatherSnapshot snapshot, int days) {
	}

	@Use
	private OpenMeteoAPI weatherAPI;

	@Use
	private LocationService locationService;

	@Use
	private SettingsService settings;

	private volatile CachedWeather cached;

	@Override
	public WeatherSnapshot getWeather(boolean refresh) throws Exception {
		int days = settings.getInt(Setting.WEATHER_DAYS);
		int cacheMinutes = settings.getInt(Setting.WEATHER_CACHE);
		CachedWeather current = cached;
		if (!refresh && current != null && current.days() == days
				&& current.snapshot().loadedAt().isAfter(LocalDateTime.now().minusMinutes(cacheMinutes))) {
			log.debug("Returning cached weather loaded at {}", current.snapshot().loadedAt());
			return current.snapshot();
		}
		GeoLocation location = locationService.getLocation(refresh);
		log.info("Loading weather for {}", location.name());

		OpenMeteoResponse response;
		try {
			response = weatherAPI.forecast(String.valueOf(location.latitude()), String.valueOf(location.longitude()),
					CURRENT_FIELDS, DAILY_FIELDS, TIMEZONE, String.valueOf(days));
		} catch (Exception e) {
			log.warn("Weather request failed: {}", e.getMessage());
			throw new Exception("Weather could not be loaded: " + e.getMessage(), e);
		}
		if (response == null || response.getCurrent() == null) {
			throw new Exception("The weather service returned no data.");
		}

		WeatherSnapshot loaded = toSnapshot(response, location);
		cached = new CachedWeather(loaded, days);
		log.info("Loaded weather with {} forecast days", loaded.days().size());
		return loaded;
	}

	private WeatherSnapshot toSnapshot(OpenMeteoResponse response, GeoLocation location) {
		OpenMeteoCurrent current = response.getCurrent();
		WeatherCurrent now = new WeatherCurrent(number(current.getTemperature_2m()),
				number(current.getApparent_temperature()), number(current.getRelative_humidity_2m()),
				number(current.getWind_speed_10m()), number(current.getWeather_code()),
				number(current.getIs_day()) == 1);

		List<WeatherDay> forecast = new ArrayList<WeatherDay>();
		OpenMeteoDaily daily = response.getDaily();
		if (daily != null && daily.getTime() != null) {
			for (int i = 0; i < daily.getTime().size(); i++) {
				forecast.add(new WeatherDay(LocalDate.parse(daily.getTime().get(i)),
						number(at(daily.getTemperature_2m_max(), i)), number(at(daily.getTemperature_2m_min(), i)),
						number(at(daily.getWeather_code(), i)),
						number(at(daily.getPrecipitation_probability_max(), i))));
			}
		}
		return new WeatherSnapshot(location.name(), now, List.copyOf(forecast), VpnCheck.suspected(location),
				LocalDateTime.now());
	}

	private static <T> T at(List<T> list, int index) {
		return list != null && index < list.size() ? list.get(index) : null;
	}

	private static double number(Double value) {
		return value == null ? 0 : value;
	}

	private static int number(Integer value) {
		return value == null ? 0 : value;
	}
}