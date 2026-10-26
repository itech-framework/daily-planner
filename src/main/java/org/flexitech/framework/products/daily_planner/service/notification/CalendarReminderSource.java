package org.flexitech.framework.products.daily_planner.service.notification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.framework.products.daily_planner.ApiTestMain;
import org.flexitech.framework.products.daily_planner.model.AgendaItem;
import org.flexitech.framework.products.daily_planner.service.CalendarService;
import org.flexitech.framework.products.daily_planner.session.UserSession;

import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.properties.Property;
import io.github.itech_framework.java_fx.router.Router;
import io.github.itech_framework.java_fx.utils.notification.AppNotification;
import io.github.itech_framework.java_fx.utils.notification.NotificationSource;
import io.github.itech_framework.java_fx.utils.notification.SystemNotificationService;

@Service
public class CalendarReminderSource implements NotificationSource {

    private static final Logger log = LogManager.getLogger(CalendarReminderSource.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    @Property(key = "planner.notification.event-lead-minutes", defaultValue = "10")
    private String leadValue;

    @Property(key = "planner.notification.event-refresh-minutes", defaultValue = "5")
    private int refreshMinutes;

    @Use
    private CalendarService calendarService;

    @Use
    private UserSession session;

    @Use
    private Router router;

    private volatile List<AgendaItem> cache = List.of();
    private volatile LocalDateTime fetchedAt;

    @Override
    public String name() {
        return "calendar";
    }

    public void invalidate() {
        log.info("Calendar cache invalidated");
        fetchedAt = null;
    }

    @Override
    public List<AppNotification> check(LocalDateTime now) throws Exception {
        if (session.getUser() == null) {
            log.info("Calendar check at {} skipped: no logged in user", now);
            return List.of();
        }
        refreshIfNeeded(now);
        List<Integer> leads = leads();
        log.info("Calendar check at {}: {} events in cache, leads={}", now, cache.size(), leads);
        List<AppNotification> result = new ArrayList<>();
        for (AgendaItem event : cache) {
            if (event.allDay() || event.start() == null) {
                log.info("Event '{}' (id={}) skipped: allDay={}, start={}",
                        event.title(), event.id(), event.allDay(), event.start());
                continue;
            }
            boolean matched = false;
            for (int lead : leads) {
                LocalDateTime fireAt = event.start().minusMinutes(lead);
                LocalDateTime until = lead == 0 ? event.start().plusMinutes(2) : event.start();
                boolean inWindow = !now.isBefore(fireAt) && now.isBefore(until);
                log.info("Event '{}' (id={}) lead={} window=[{} .. {}) now={} inWindow={}",
                        event.title(), event.id(), lead, fireAt, until, now, inWindow);
                if (inWindow) {
                    result.add(notification(event, lead, now));
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                log.info("Event '{}' (id={}) not in any window yet", event.title(), event.id());
            }
        }
        log.info("Calendar check returned {} notification(s)", result.size());
        return result;
    }

    private void refreshIfNeeded(LocalDateTime now) throws Exception {
        LocalDateTime last = fetchedAt;
        if (last != null && last.plusMinutes(refreshMinutes).isAfter(now)) {
            log.info("Calendar cache still fresh, fetched at {}, refresh every {} min", last, refreshMinutes);
            return;
        }
        LocalDate today = now.toLocalDate();
        log.info("Calendar cache refreshing for {} to {}", today, today.plusDays(1));
        cache = calendarService.getEvents(today, today.plusDays(1));
        fetchedAt = now;
        log.info("Calendar cache loaded: {} events", cache.size());
    }

	private AppNotification notification(AgendaItem event, int lead, LocalDateTime now) {
		long minutes = Math.max(0, ChronoUnit.MINUTES.between(now, event.start()));
		String when = lead == 0 || minutes == 0 ? "Starting now" : "Starts in " + minutes + " min";
		String link = event.link();
		boolean meet = link != null && link.contains("meet.google.com");
		String place = event.detail() == null || event.detail().isBlank() ? "" : " \u00b7 " + event.detail();
		String join = meet ? " \u00b7 Click to join Google Meet" : "";
		String id = "event:" + event.id() + ":" + event.start() + ":" + lead;
		log.info("Calendar notification created: id={}, title='{}', text='{}', meet={}", id, event.title(), when, meet);

		Runnable action = () -> {
			if (meet && ApiTestMain.hostServices() != null) {
				ApiTestMain.hostServices().showDocument(link);
			} else {
				router.off("calendar", event.start().toLocalDate());
			}
		};
		return new AppNotification(id, event.title(),
				when + " (" + TIME.format(event.start()) + ")" + place + join,
				SystemNotificationService.Type.INFO, action);
	}

    private List<Integer> leads() {
        List<Integer> result = new ArrayList<>();
        for (String part : leadValue.split(",")) {
            try {
                int value = Integer.parseInt(part.trim());
                if (value >= 0) {
                    result.add(value);
                }
            } catch (NumberFormatException e) {
                log.warn("Ignoring invalid lead minutes value '{}' in planner.notification.event-lead-minutes", part);
            }
        }
        if (result.isEmpty()) {
            log.warn("No valid lead minutes found in '{}', using default 10", leadValue);
            result.add(10);
        }
        Collections.sort(result);
        return result;
    }
}