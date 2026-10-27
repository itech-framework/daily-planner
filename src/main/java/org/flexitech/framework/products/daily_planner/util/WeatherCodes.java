package org.flexitech.framework.products.daily_planner.util;

public final class WeatherCodes {

	public enum Kind {
		CLEAR("\u2600", "wx-clear"),
		NIGHT("\u263E", "wx-night"),
		PARTLY("\u26C5", "wx-partly"),
		CLOUDY("\u2601", "wx-cloudy"),
		FOG("\u2248", "wx-fog"),
		RAIN("\u2602", "wx-rain"),
		SNOW("\u2744", "wx-snow"),
		STORM("\u26A1", "wx-storm");

		private final String glyph;
		private final String styleClass;

		Kind(String glyph, String styleClass) {
			this.glyph = glyph;
			this.styleClass = styleClass;
		}

		public String glyph() {
			return glyph;
		}

		public String styleClass() {
			return styleClass;
		}
	}

	private WeatherCodes() {
	}

	public static Kind kind(int code, boolean day) {
		return switch (code) {
		case 0, 1 -> day ? Kind.CLEAR : Kind.NIGHT;
		case 2 -> day ? Kind.PARTLY : Kind.CLOUDY;
		case 3 -> Kind.CLOUDY;
		case 45, 48 -> Kind.FOG;
		case 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Kind.RAIN;
		case 71, 73, 75, 77, 85, 86 -> Kind.SNOW;
		case 95, 96, 99 -> Kind.STORM;
		default -> Kind.CLOUDY;
		};
	}

	public static String describe(int code) {
		return switch (code) {
		case 0 -> "Clear sky";
		case 1 -> "Mostly clear";
		case 2 -> "Partly cloudy";
		case 3 -> "Overcast";
		case 45, 48 -> "Foggy";
		case 51, 53, 55 -> "Drizzle";
		case 56, 57 -> "Freezing drizzle";
		case 61 -> "Light rain";
		case 63 -> "Rain";
		case 65 -> "Heavy rain";
		case 66, 67 -> "Freezing rain";
		case 71 -> "Light snow";
		case 73 -> "Snow";
		case 75 -> "Heavy snow";
		case 77 -> "Snow grains";
		case 80, 81 -> "Rain showers";
		case 82 -> "Violent showers";
		case 85, 86 -> "Snow showers";
		case 95 -> "Thunderstorm";
		case 96, 99 -> "Thunderstorm with hail";
		default -> "Unknown";
		};
	}
}