package org.flexitech.framework.products.daily_planner.api.response;

public class OpenMeteoCurrent {

	private Double temperature_2m;
	private Double apparent_temperature;
	private Integer relative_humidity_2m;
	private Integer weather_code;
	private Double wind_speed_10m;
	private Integer is_day;

	public Double getTemperature_2m() {
		return temperature_2m;
	}

	public void setTemperature_2m(Double temperature_2m) {
		this.temperature_2m = temperature_2m;
	}

	public Double getApparent_temperature() {
		return apparent_temperature;
	}

	public void setApparent_temperature(Double apparent_temperature) {
		this.apparent_temperature = apparent_temperature;
	}

	public Integer getRelative_humidity_2m() {
		return relative_humidity_2m;
	}

	public void setRelative_humidity_2m(Integer relative_humidity_2m) {
		this.relative_humidity_2m = relative_humidity_2m;
	}

	public Integer getWeather_code() {
		return weather_code;
	}

	public void setWeather_code(Integer weather_code) {
		this.weather_code = weather_code;
	}

	public Double getWind_speed_10m() {
		return wind_speed_10m;
	}

	public void setWind_speed_10m(Double wind_speed_10m) {
		this.wind_speed_10m = wind_speed_10m;
	}

	public Integer getIs_day() {
		return is_day;
	}

	public void setIs_day(Integer is_day) {
		this.is_day = is_day;
	}
}