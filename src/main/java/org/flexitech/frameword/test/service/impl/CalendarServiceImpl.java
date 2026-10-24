package org.flexitech.frameword.test.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.frameword.test.api.GoogleCalendarAPI;
import org.flexitech.frameword.test.api.request.CalendarEventRequest;
import org.flexitech.frameword.test.api.response.CalendarEvent;
import org.flexitech.frameword.test.api.response.CalendarEventList;
import org.flexitech.frameword.test.api.response.EventDateTime;
import org.flexitech.frameword.test.model.AgendaItem;
import org.flexitech.frameword.test.model.EventDraft;
import org.flexitech.frameword.test.service.CalendarService;
import org.flexitech.frameword.test.service.GoogleAuthService;
import org.flexitech.frameword.test.util.GoogleApiSupport;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.itech_framework.api_client.exceptions.ApiException;
import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_framework.core.annotations.dependency.Use;

@Service
public class CalendarServiceImpl implements CalendarService {

	private static final Logger log = LogManager.getLogger(CalendarServiceImpl.class);
	private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
	private static final String CALENDAR = "primary";
	private static final String API_NAME = "Google Calendar";
	private static final String PAGE_SIZE = "250";
	private static final int MAX_PAGES = 10;

	@Use
	private GoogleAuthService authService;

	@Use
	private GoogleCalendarAPI calendarAPI;

	private final Map<String, CalendarEvent> cache = new ConcurrentHashMap<String, CalendarEvent>();
	private final ObjectMapper mapper = new ObjectMapper();

	@Override
	public List<AgendaItem> getEvents(LocalDate from, LocalDate to) throws Exception {
		ZoneId zone = ZoneId.systemDefault();
		String timeMin = RFC3339.format(from.atStartOfDay(zone));
		String timeMax = RFC3339.format(to.plusDays(1).atStartOfDay(zone));
		log.info("Loading calendar events from {} to {}", from, to);

		String token = authService.getAccessToken();
		List<AgendaItem> items = new ArrayList<AgendaItem>();

		CalendarEventList page = GoogleApiSupport.call(API_NAME, () -> calendarAPI.listEvents(
				GoogleApiSupport.bearer(token), CALENDAR, timeMin, timeMax, "true", "startTime", PAGE_SIZE));
		int pages = 1;
		while (page != null) {
			for (CalendarEvent event : page.getItems()) {
				if ("cancelled".equals(event.getStatus())) {
					continue;
				}
				cache.put(event.getId(), event);
				items.add(toItem(event, zone));
			}
			String next = page.getNextPageToken();
			if (next == null || next.isBlank() || pages >= MAX_PAGES) {
				break;
			}
			pages++;
			page = GoogleApiSupport.call(API_NAME, () -> calendarAPI.listEventsPage(GoogleApiSupport.bearer(token),
					CALENDAR, timeMin, timeMax, "true", "startTime", PAGE_SIZE, next));
		}
		log.info("Loaded {} calendar events in {} pages", items.size(), pages);
		return items;
	}

	@Override
	public AgendaItem createEvent(EventDraft draft) throws Exception {
		validate(draft);
		ZoneId zone = ZoneId.systemDefault();
		log.info("Creating calendar event, all day: {}", draft.allDay());

		CalendarEventRequest body = buildRequest(draft, zone);

		String token = authService.getAccessToken();
		CalendarEvent created = GoogleApiSupport.call(API_NAME,
				() -> calendarAPI.createEvent(GoogleApiSupport.bearer(token), CALENDAR, body));
		cache.put(created.getId(), created);
		return toItem(created, zone);
	}

	private CalendarEventRequest buildRequest(EventDraft draft, ZoneId zone) {
		EventDateTime start = new EventDateTime();
		EventDateTime end = new EventDateTime();

		if (draft.allDay()) {
			start.setDate(draft.start().toLocalDate().toString());
			end.setDate(draft.end().toLocalDate().plusDays(1).toString());
		} else {
			start.setDateTime(RFC3339.format(draft.start().atZone(zone)));
			end.setDateTime(RFC3339.format(draft.end().atZone(zone)));
			if (zone.getId().contains("/")) {
				start.setTimeZone(zone.getId());
				end.setTimeZone(zone.getId());
			}
		}

		CalendarEventRequest body = new CalendarEventRequest();
		body.setSummary(draft.title().trim());
		body.setDescription(blankToNull(draft.description()));
		body.setLocation(blankToNull(draft.location()));
		body.setStart(start);
		body.setEnd(end);
		return body;
	}

	@Override
	public AgendaItem updateEvent(AgendaItem event, EventDraft draft) throws Exception {
		validate(draft);
		CalendarEvent source = cache.get(event.id());
		if (source == null) {
			throw new Exception("This event is not loaded any more. Refresh and try again.");
		}
		ZoneId zone = ZoneId.systemDefault();
		log.info("Updating calendar event {}", event.id());

		CalendarEvent body = mapper.convertValue(source, CalendarEvent.class);
		apply(body, draft, zone);

		String token = authService.getAccessToken();
		java.util.Map<String, String> headers = GoogleApiSupport.bearer(token);
		if (source.getEtag() != null) {
			headers.put("If-Match", source.getEtag());
		}

		CalendarEvent updated;
		try {
			updated = GoogleApiSupport.call(API_NAME,
					() -> calendarAPI.updateEvent(headers, CALENDAR, event.id(), body));
		} catch (ApiException e) {
			if (e.getStatusCode() == 412) {
				throw new Exception("This event was changed somewhere else. Refresh and try again.", e);
			}
			throw e;
		}
		cache.put(updated.getId(), updated);
		return toItem(updated, zone);
	}

	@Override
	public void deleteEvent(AgendaItem event) throws Exception {
		log.info("Deleting calendar event {}", event.id());
		String token = authService.getAccessToken();
		GoogleApiSupport.call(API_NAME, () -> {
			calendarAPI.deleteEvent(GoogleApiSupport.bearer(token), CALENDAR, event.id());
			return Boolean.TRUE;
		});
		cache.remove(event.id());
	}

	private void validate(EventDraft draft) throws Exception {
		if (draft.title() == null || draft.title().isBlank()) {
			throw new Exception("An event needs a title.");
		}
		if (draft.start() == null || draft.end() == null) {
			throw new Exception("An event needs a start and an end.");
		}
		if (draft.allDay()) {
			if (draft.end().toLocalDate().isBefore(draft.start().toLocalDate())) {
				throw new Exception("The end date cannot be before the start date.");
			}
		} else if (!draft.end().isAfter(draft.start())) {
			throw new Exception("The end time must be after the start time.");
		}
	}

	private void apply(CalendarEvent body, EventDraft draft, ZoneId zone) {
		body.setSummary(draft.title().trim());
		body.setLocation(blankToNull(draft.location()));
		body.setDescription(blankToNull(draft.description()));

		EventDateTime start = new EventDateTime();
		EventDateTime end = new EventDateTime();
		if (draft.allDay()) {
			start.setDate(draft.start().toLocalDate().toString());
			end.setDate(draft.end().toLocalDate().plusDays(1).toString());
		} else {
			start.setDateTime(RFC3339.format(draft.start().atZone(zone)));
			end.setDateTime(RFC3339.format(draft.end().atZone(zone)));
			if (zone.getId().contains("/")) {
				start.setTimeZone(zone.getId());
				end.setTimeZone(zone.getId());
			}
		}
		body.setStart(start);
		body.setEnd(end);
	}

	private AgendaItem toItem(CalendarEvent event, ZoneId zone) {
		EventDateTime start = event.getStart();
		EventDateTime end = event.getEnd();
		boolean allDay = start != null && start.isAllDay();
		String title = event.getSummary() == null || event.getSummary().isBlank() ? "(No title)" : event.getSummary();
		String link = event.getHangoutLink() != null ? event.getHangoutLink() : event.getHtmlLink();

		return new AgendaItem(event.getId(), AgendaItem.Type.EVENT, title, event.getLocation(), parse(start, zone),
				parse(end, zone), allDay, null, false, null, null, link, event.getDescription(),
				event.getRecurringEventId());
	}

	private LocalDateTime parse(EventDateTime value, ZoneId zone) {
		if (value == null) {
			return null;
		}
		if (value.getDateTime() != null) {
			return OffsetDateTime.parse(value.getDateTime()).atZoneSameInstant(zone).toLocalDateTime();
		}
		if (value.getDate() != null) {
			return LocalDate.parse(value.getDate()).atStartOfDay();
		}
		return null;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}