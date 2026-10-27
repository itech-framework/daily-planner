package org.flexitech.framework.products.daily_planner.service.notification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.flexitech.framework.products.daily_planner.model.Setting;
import org.flexitech.framework.products.daily_planner.model.WeatherDay;
import org.flexitech.framework.products.daily_planner.model.WeatherSnapshot;
import org.flexitech.framework.products.daily_planner.service.SettingsService;
import org.flexitech.framework.products.daily_planner.service.WeatherService;
import org.flexitech.framework.products.daily_planner.session.UserSession;

import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.java_fx.router.Router;
import io.github.itech_framework.java_fx.utils.notification.AppNotification;
import io.github.itech_framework.java_fx.utils.notification.NotificationSource;
import io.github.itech_framework.java_fx.utils.notification.SystemNotificationService;

@Service
public class WeatherReminderSource implements NotificationSource {

    private static final int THUNDERSTORM_CODE = 95;

    @Use
    private SettingsService settings;

    @Use
    private WeatherService weatherService;

    @Use
    private UserSession session;

    @Use
    private Router router;

    private volatile LocalDate lastDay;

    @Override
    public String name() {
        return "weather";
    }

    @Override
    public List<AppNotification> check(LocalDateTime now) throws Exception {
        LocalDate today = now.toLocalDate();
        int hour = settings.getInt(Setting.WEATHER_HOUR);
        if (hour < 0 || session.getUser() == null || now.getHour() < hour || today.equals(lastDay)) {
            return List.of();
        }
        WeatherSnapshot snapshot = weatherService.getWeather(false);
        lastDay = today;
        if (snapshot.days().isEmpty()) {
            return List.of();
        }

        WeatherDay day = snapshot.days().get(0);
        int rainChance = settings.getInt(Setting.RAIN_CHANCE);
        int hot = settings.getInt(Setting.HOT);
        int cold = settings.getInt(Setting.COLD);
        List<String> notes = new ArrayList<String>();
        boolean severe = false;
        if (day.code() >= THUNDERSTORM_CODE) {
            notes.add("Thunderstorm expected");
            severe = true;
        } else if (day.rainChance() >= rainChance) {
            notes.add("Rain likely (" + day.rainChance() + "%), take an umbrella");
        }
        if (isSnow(day.code())) {
            notes.add("Snow expected");
        }
        if (day.max() >= hot) {
            notes.add("Hot day, up to " + Math.round(day.max()) + "\u00B0C, stay hydrated");
        }
        if (day.min() <= cold) {
            notes.add("Cold day, down to " + Math.round(day.min()) + "\u00B0C, dress warm");
        }
        if (notes.isEmpty()) {
            return List.of();
        }

        String message = String.join(". ", notes) + ".";
        if (snapshot.locationUncertain()) {
            message += " Your location may be inaccurate (VPN?).";
        }
        return List.of(new AppNotification("weather:" + today, "Weather today in " + snapshot.location(), message,
                severe ? SystemNotificationService.Type.WARNING : SystemNotificationService.Type.INFO,
                () -> router.off("calendar", today)));
    }

    private static boolean isSnow(int code) {
        return code >= 71 && code <= 77 || code == 85 || code == 86;
    }
}