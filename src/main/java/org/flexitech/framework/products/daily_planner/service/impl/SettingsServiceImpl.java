package org.flexitech.framework.products.daily_planner.service.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.framework.products.daily_planner.model.Setting;
import org.flexitech.framework.products.daily_planner.service.SettingsService;

import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_framework.core.annotations.properties.Property;

@Service
public class SettingsServiceImpl implements SettingsService {

	private static final Logger log = LogManager.getLogger(SettingsServiceImpl.class);
	private static final Path FILE = Path.of(System.getProperty("user.home"), ".daily-planner", "settings.properties");
	private static final String NONE = "none";

	@Property(key = "planner.calendar.holiday-calendar", defaultValue = NONE)
	private String holidayCalendar;

	@Property(key = "planner.calendar.off-days", defaultValue = "SUNDAY")
	private String offDays;

	@Property(key = "planner.notification.task-hour", defaultValue = "9")
	private String taskHour;

	@Property(key = "planner.notification.weather-hour", defaultValue = "7")
	private String weatherHour;

	@Property(key = "planner.notification.weather-rain-chance", defaultValue = "50")
	private String rainChance;

	@Property(key = "planner.notification.weather-hot", defaultValue = "35")
	private String hot;

	@Property(key = "planner.notification.weather-cold", defaultValue = "5")
	private String cold;

	@Property(key = "planner.weather.days", defaultValue = "5")
	private String weatherDays;

	@Property(key = "planner.weather.cache-minutes", defaultValue = "15")
	private String weatherCache;

	@Property(key = "planner.drive.download-folder", defaultValue = NONE)
	private String downloadFolder;

	@Property(key = "planner.drive.connections", defaultValue = "6")
	private String driveConnections;

	private final Properties overrides = new Properties();
	private boolean loaded;

	@Override
	public synchronized String getString(Setting setting) {
		ensureLoaded();
		String value = overrides.getProperty(setting.key());
		return value != null ? value : getDefault(setting);
	}

	@Override
	public synchronized int getInt(Setting setting) {
		String value = getString(setting);
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			log.warn("Setting {} is not a number: {}", setting.key(), value);
			try {
				return Integer.parseInt(getDefault(setting).trim());
			} catch (NumberFormatException ignored) {
				return setting.min();
			}
		}
	}

	@Override
	public String getDefault(Setting setting) {
		String value = switch (setting) {
		case HOLIDAY_CALENDAR -> unset(holidayCalendar);
		case OFF_DAYS -> offDays;
		case TASK_HOUR -> taskHour;
		case WEATHER_HOUR -> weatherHour;
		case RAIN_CHANCE -> rainChance;
		case HOT -> hot;
		case COLD -> cold;
		case WEATHER_DAYS -> weatherDays;
		case WEATHER_CACHE -> weatherCache;
		case DOWNLOAD_FOLDER -> downloadFolderDefault();
		case DRIVE_CONNECTIONS -> driveConnections;
		};
		return value == null ? "" : value;
	}

	@Override
	public synchronized boolean isOverridden(Setting setting) {
		ensureLoaded();
		return overrides.containsKey(setting.key());
	}

	@Override
	public synchronized void save(Map<Setting, String> values) throws Exception {
		ensureLoaded();
		Properties updated = new Properties();
		updated.putAll(overrides);
		for (Map.Entry<Setting, String> entry : values.entrySet()) {
			Setting setting = entry.getKey();
			String value = entry.getValue() == null ? "" : entry.getValue().trim();
			if (setting.kind() == Setting.Kind.NUMBER && value.isEmpty()) {
				updated.remove(setting.key());
				continue;
			}
			validate(setting, value);
			if (value.equals(getDefault(setting).trim())) {
				updated.remove(setting.key());
			} else {
				updated.setProperty(setting.key(), value);
			}
		}
		commit(updated);
		log.info("Saved settings, {} overrides active", overrides.size());
	}

	@Override
	public synchronized void reset(Setting setting) throws Exception {
		ensureLoaded();
		Properties updated = new Properties();
		updated.putAll(overrides);
		updated.remove(setting.key());
		commit(updated);
	}

	@Override
	public synchronized void resetAll() throws Exception {
		ensureLoaded();
		commit(new Properties());
	}

	private String downloadFolderDefault() {
		String configured = unset(downloadFolder);
		if (!configured.isEmpty()) {
			return configured;
		}
		Path downloads = Path.of(System.getProperty("user.home"), "Downloads");
		return Files.isDirectory(downloads) ? downloads.toString() : "";
	}

	private static String unset(String value) {
		if (value == null || value.isBlank() || NONE.equalsIgnoreCase(value.trim())) {
			return "";
		}
		return value;
	}

	private void validate(Setting setting, String value) throws Exception {
		if (setting.kind() == Setting.Kind.NUMBER) {
			int number;
			try {
				number = Integer.parseInt(value);
			} catch (NumberFormatException e) {
				throw new Exception(setting.label() + " must be a whole number.");
			}
			if (number < setting.min() || number > setting.max()) {
				throw new Exception(setting.label() + " must be between " + setting.min() + " and " + setting.max() + ".");
			}
			return;
		}
		if (setting.kind() == Setting.Kind.FOLDER) {
			if (!value.isEmpty() && !isFolder(value)) {
				throw new Exception(setting.label() + " must be an existing folder.");
			}
			return;
		}
		if (setting == Setting.OFF_DAYS && !value.isEmpty()) {
			for (String part : value.split(",")) {
				try {
					DayOfWeek.valueOf(part.trim().toUpperCase(Locale.ROOT));
				} catch (IllegalArgumentException e) {
					throw new Exception("Non-working days must be day names separated by commas, for example SATURDAY,SUNDAY.");
				}
			}
		}
	}

	private static boolean isFolder(String value) {
		try {
			return Files.isDirectory(Path.of(value));
		} catch (InvalidPathException e) {
			return false;
		}
	}

	private void commit(Properties updated) throws IOException {
		Files.createDirectories(FILE.getParent());
		Path temp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
			updated.store(writer, null);
		}
		Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
		overrides.clear();
		overrides.putAll(updated);
	}

	private void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		if (!Files.exists(FILE)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
			overrides.load(reader);
			log.info("Loaded {} setting overrides", overrides.size());
		} catch (IOException e) {
			log.warn("Settings file could not be read: {}", e.getMessage());
		}
	}
}