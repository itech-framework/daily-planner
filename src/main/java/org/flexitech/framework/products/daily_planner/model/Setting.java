package org.flexitech.framework.products.daily_planner.model;

public enum Setting {

	HOLIDAY_CALENDAR("planner.calendar.holiday-calendar", Group.CALENDAR, Kind.TEXT, "Holiday calendar ID",
			"Google Calendar ID of a holiday calendar. Leave empty to turn it off.", 0, 0),
	OFF_DAYS("planner.calendar.off-days", Group.CALENDAR, Kind.TEXT, "Non-working days",
			"Day names separated by commas, for example SATURDAY,SUNDAY.", 0, 0),
	TASK_HOUR("planner.notification.task-hour", Group.NOTIFICATIONS, Kind.NUMBER, "Task reminder hour",
			"Hour of the day (0-23) for the daily task summary, or -1 to turn it off.", -1, 23),
	WEATHER_HOUR("planner.notification.weather-hour", Group.NOTIFICATIONS, Kind.NUMBER, "Weather briefing hour",
			"Hour of the day (0-23) for the weather briefing, or -1 to turn it off.", -1, 23),
	RAIN_CHANCE("planner.notification.weather-rain-chance", Group.NOTIFICATIONS, Kind.NUMBER, "Rain warning (%)",
			"Warn about rain when the chance is at least this percent.", 0, 100),
	HOT("planner.notification.weather-hot", Group.NOTIFICATIONS, Kind.NUMBER, "Hot day from (\u00B0C)",
			"Show a hot-day note when the high reaches this temperature.", -20, 60),
	COLD("planner.notification.weather-cold", Group.NOTIFICATIONS, Kind.NUMBER, "Cold day up to (\u00B0C)",
			"Show a cold-day note when the low drops to this temperature.", -60, 30),
	WEATHER_DAYS("planner.weather.days", Group.WEATHER, Kind.NUMBER, "Forecast days",
			"Number of days in the forecast list (1-7).", 1, 7),
	WEATHER_CACHE("planner.weather.cache-minutes", Group.WEATHER, Kind.NUMBER, "Weather refresh (minutes)",
			"How long a forecast is reused before asking again.", 1, 240),
	DOWNLOAD_FOLDER("planner.drive.download-folder", Group.DRIVE, Kind.FOLDER, "Default download folder",
			"Folder the save dialog opens in for downloads. Leave empty to reuse the last folder you saved to.", 0, 0),
	DRIVE_CONNECTIONS("planner.drive.connections", Group.DRIVE, Kind.NUMBER, "Drive connections",
			"Parallel connections for one large download, and how many uploads run at the same time (1-10).", 1, 10);

	public enum Group {

		CALENDAR("Calendar"),
		NOTIFICATIONS("Notifications"),
		WEATHER("Weather"),
		DRIVE("Drive");

		private final String title;

		Group(String title) {
			this.title = title;
		}

		public String title() {
			return title;
		}
	}

	public enum Kind {
		TEXT,
		NUMBER,
		FOLDER
	}

	private final String key;
	private final Group group;
	private final Kind kind;
	private final String label;
	private final String hint;
	private final int min;
	private final int max;

	Setting(String key, Group group, Kind kind, String label, String hint, int min, int max) {
		this.key = key;
		this.group = group;
		this.kind = kind;
		this.label = label;
		this.hint = hint;
		this.min = min;
		this.max = max;
	}

	public String key() {
		return key;
	}

	public Group group() {
		return group;
	}

	public Kind kind() {
		return kind;
	}

	public String label() {
		return label;
	}

	public String hint() {
		return hint;
	}

	public int min() {
		return min;
	}

	public int max() {
		return max;
	}
}