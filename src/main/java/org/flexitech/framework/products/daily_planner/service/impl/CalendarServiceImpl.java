package org.flexitech.framework.products.daily_planner.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.framework.products.daily_planner.api.GoogleCalendarAPI;
import org.flexitech.framework.products.daily_planner.api.request.CalendarEventRequest;
import org.flexitech.framework.products.daily_planner.api.response.CalendarEvent;
import org.flexitech.framework.products.daily_planner.api.response.CalendarEventList;
import org.flexitech.framework.products.daily_planner.api.response.ConferenceData;
import org.flexitech.framework.products.daily_planner.api.response.EventAttendee;
import org.flexitech.framework.products.daily_planner.api.response.EventDateTime;
import org.flexitech.framework.products.daily_planner.model.AgendaItem;
import org.flexitech.framework.products.daily_planner.model.EventDraft;
import org.flexitech.framework.products.daily_planner.model.Holiday;
import org.flexitech.framework.products.daily_planner.service.CalendarService;
import org.flexitech.framework.products.daily_planner.service.GoogleAuthService;
import org.flexitech.framework.products.daily_planner.util.GoogleApiSupport;

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
		boolean conference = draft.addMeet();
		boolean invite = !draft.attendees().isEmpty();
		log.info("Creating calendar event, all day: {}, meet: {}, attendees: {}", draft.allDay(), conference,
				draft.attendees().size());

		CalendarEventRequest body = buildRequest(draft, zone);
		String token = authService.getAccessToken();

		CalendarEvent created;
		if (conference || invite) {
			String versionParam = conference ? "1" : null;
			String updatesParam = invite ? "all" : "none";
			created = GoogleApiSupport.call(API_NAME, () -> calendarAPI.createEventWithConference(
					GoogleApiSupport.bearer(token), CALENDAR, versionParam, updatesParam, body));
		} else {
			created = GoogleApiSupport.call(API_NAME,
					() -> calendarAPI.createEvent(GoogleApiSupport.bearer(token), CALENDAR, body));
		}

		if (conference && created.getHangoutLink() == null) {
			created = reloadForMeetLink(created, token);
		}
		cache.put(created.getId(), created);
		return toItem(created, zone);
	}

	private CalendarEvent reloadForMeetLink(CalendarEvent created, String token) throws Exception {
		log.info("Meet link is not ready for event {}, reading it again", created.getId());
		Thread.sleep(1500);
		CalendarEvent reloaded = GoogleApiSupport.call(API_NAME,
				() -> calendarAPI.getEvent(GoogleApiSupport.bearer(token), CALENDAR, created.getId()));
		if (reloaded.getHangoutLink() == null) {
			log.warn("Event {} was created but Google did not return a Meet link", created.getId());
		}
		return reloaded;
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

		if (!draft.attendees().isEmpty()) {
			List<EventAttendee> attendees = new ArrayList<EventAttendee>();
			for (String email : draft.attendees()) {
				attendees.add(new EventAttendee(email.trim()));
			}
			body.setAttendees(attendees);
		}
		if (draft.addMeet()) {
			body.setConferenceData(ConferenceData.newMeet(UUID.randomUUID().toString()));
		}
		
		body.setGuestsCanModify(draft.guestsCanModify() != null && draft.guestsCanModify());
		body.setGuestsCanInviteOthers(draft.guestsCanInvite() != null && draft.guestsCanInvite());
		
		return body;
	}

	@Override
	public AgendaItem updateEvent(AgendaItem event, EventDraft draft) throws Exception {
		validate(draft);
		CalendarEvent source = cache.get(event.id());
		if (source == null) {
			throw new Exception("This event is not loaded any more. Refresh and try again.");
		}
		if (!canChange(source)) {
			throw new Exception("Only the organizer can change this event.");
		}
		ZoneId zone = ZoneId.systemDefault();

		CalendarEvent body = mapper.convertValue(source, CalendarEvent.class);
		apply(body, draft, zone);
		if (draft.guestsCanModify() != null) {
			body.putExtra("guestsCanModify", draft.guestsCanModify());
		}
		if (draft.guestsCanInvite() != null) {
			body.putExtra("guestsCanInviteOthers", draft.guestsCanInvite());
		}
		boolean newMeet = addMeetTo(body, draft);
		int addedGuests = addGuestsTo(body, draft);
		log.info("Updating calendar event {}, new meet: {}, new guests: {}", event.id(), newMeet, addedGuests);

		String token = authService.getAccessToken();
		java.util.Map<String, String> headers = GoogleApiSupport.bearer(token);
		if (source.getEtag() != null) {
			headers.put("If-Match", source.getEtag());
		}

		CalendarEvent updated;
		try {
			if (newMeet || addedGuests > 0) {
				String version = newMeet ? "1" : "0";
				String updates = addedGuests > 0 ? "all" : "none";
				updated = GoogleApiSupport.call(API_NAME, () -> calendarAPI.updateEventWithConference(headers,
						CALENDAR, event.id(), version, updates, body));
			} else {
				updated = GoogleApiSupport.call(API_NAME,
						() -> calendarAPI.updateEvent(headers, CALENDAR, event.id(), body));
			}
		} catch (ApiException e) {
			if (e.getStatusCode() == 412) {
				throw new Exception("This event was changed somewhere else. Refresh and try again.", e);
			}
			throw e;
		}

		if (newMeet && updated.getHangoutLink() == null) {
			updated = reloadForMeetLink(updated, token);
		}
		cache.put(updated.getId(), updated);
		return toItem(updated, zone);
	}
	
	private boolean canChange(CalendarEvent source) {
		return isOrganizer(source) || Boolean.TRUE.equals(source.getExtras().get("guestsCanModify"));
	}

	private boolean addMeetTo(CalendarEvent body, EventDraft draft) {
		if (!draft.addMeet()) {
			return false;
		}
		if (body.getHangoutLink() != null || body.getExtras().containsKey("conferenceData")) {
			return false;
		}
		body.putExtra("conferenceData", ConferenceData.newMeet(UUID.randomUUID().toString()));
		return true;
	}

	private int addGuestsTo(CalendarEvent body, EventDraft draft) {
		if (draft.attendees().isEmpty()) {
			return 0;
		}
		List<Object> guests = new ArrayList<Object>();
		Object raw = body.getExtras().get("attendees");
		if (raw instanceof List<?> existing) {
			guests.addAll(existing);
		}
		Set<String> known = new HashSet<String>();
		for (Object guest : guests) {
			if (guest instanceof Map<?, ?> map && map.get("email") != null) {
				known.add(String.valueOf(map.get("email")).toLowerCase(java.util.Locale.ROOT));
			}
		}
		int added = 0;
		for (String email : draft.attendees()) {
			String value = email.trim();
			if (known.add(value.toLowerCase(java.util.Locale.ROOT))) {
				guests.add(new EventAttendee(value));
				added++;
			}
		}
		if (added > 0) {
			body.putExtra("attendees", guests);
		}
		return added;
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
	
	private List<CalendarEvent> fetchEvents(String calendarId, LocalDate from, LocalDate to, ZoneId zone)
			throws Exception {
		String timeMin = RFC3339.format(from.atStartOfDay(zone));
		String timeMax = RFC3339.format(to.plusDays(1).atStartOfDay(zone));
		String token = authService.getAccessToken();
		List<CalendarEvent> result = new ArrayList<CalendarEvent>();

		CalendarEventList page = GoogleApiSupport.call(API_NAME, () -> calendarAPI.listEvents(
				GoogleApiSupport.bearer(token), calendarId, timeMin, timeMax, "true", "startTime", PAGE_SIZE));
		int pages = 1;
		while (page != null) {
			for (CalendarEvent event : page.getItems()) {
				if (!"cancelled".equals(event.getStatus())) {
					result.add(event);
				}
			}
			String next = page.getNextPageToken();
			if (next == null || next.isBlank() || pages >= MAX_PAGES) {
				break;
			}
			pages++;
			page = GoogleApiSupport.call(API_NAME, () -> calendarAPI.listEventsPage(GoogleApiSupport.bearer(token),
					calendarId, timeMin, timeMax, "true", "startTime", PAGE_SIZE, next));
		}
		return result;
	}


	@Override
	public List<Holiday> getHolidays(String calendarId, LocalDate from, LocalDate to) throws Exception {
		ZoneId zone = ZoneId.systemDefault();
		log.info("Loading holidays from {} to {}", from, to);
		log.info("Holiday calendar id: '{}'", calendarId);
		List<Holiday> holidays = new ArrayList<Holiday>();
		for (CalendarEvent event : fetchEvents(calendarId, from, to, zone)) {
			EventDateTime start = event.getStart();
			EventDateTime end = event.getEnd();
			if (start == null || start.getDate() == null) {
				continue;
			}
			LocalDate first = LocalDate.parse(start.getDate());
			LocalDate last = end == null || end.getDate() == null ? first
					: LocalDate.parse(end.getDate()).minusDays(1);
			String name = event.getSummary() == null ? "" : event.getSummary();
			String description = event.getDescription() == null ? "" : event.getDescription();
			boolean publicHoliday = description.toLowerCase(java.util.Locale.ROOT).contains("public holiday");
			log.debug("Holiday '{}' description: {}", name, description);

			for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
				if (!day.isBefore(from) && !day.isAfter(to)) {
					holidays.add(new Holiday(day, name, publicHoliday));
				}
			}
		}
		log.info("Loaded {} holiday days", holidays.size());
		return holidays;
	}
	
	@Override
	public List<Holiday> getAllHolidays(String calendarIds, LocalDate from, LocalDate to) {
		Map<String, Holiday> unique = new LinkedHashMap<String, Holiday>();
		if (calendarIds != null) {
			for (String id : calendarIds.split(",")) {
				String calendarId = id.trim();
				if (calendarId.isEmpty()) {
					continue;
				}
				try {
					for (Holiday holiday : getHolidays(calendarId, from, to)) {
						unique.merge(holiday.date() + "|" + holiday.name(), holiday,
								(first, second) -> first.publicHoliday() ? first : second);
					}
				} catch (Exception e) {
					log.warn("Holidays could not be loaded for {}: {}", calendarId, e.getMessage());
				}
			}
		}
		return new ArrayList<Holiday>(unique.values());
	}

	/*
	 * private static String encodeCalendarId(String calendarId) { return
	 * java.net.URLEncoder.encode(calendarId,
	 * java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"); }
	 */

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
		
		for (String email : draft.attendees()) {
			String value = email == null ? "" : email.trim();
			int at = value.indexOf('@');
			if (at < 1 || at == value.length() - 1 || value.contains(" ")) {
				throw new Exception("'" + value + "' is not a valid email address.");
			}
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
				event.getRecurringEventId(), guestsOf(event), isOrganizer(event),
				Boolean.TRUE.equals(event.getExtras().get("guestsCanModify")),
				!Boolean.FALSE.equals(event.getExtras().get("guestsCanInviteOthers")));
	}
	
	private boolean isOrganizer(CalendarEvent event) {
		Object organizer = event.getExtras().get("organizer");
		if (!(organizer instanceof Map<?, ?> map)) {
			return true;
		}
		return Boolean.TRUE.equals(map.get("self"));
	}
	
	private List<AgendaItem.Guest> guestsOf(CalendarEvent event) {
		List<AgendaItem.Guest> guests = new ArrayList<AgendaItem.Guest>();
		Object raw = event.getExtras().get("attendees");
		if (raw instanceof List<?> list) {
			for (Object entry : list) {
				if (entry instanceof Map<?, ?> map && map.get("email") != null) {
					guests.add(new AgendaItem.Guest(asText(map.get("email")), asText(map.get("displayName")),
							asText(map.get("responseStatus")), Boolean.TRUE.equals(map.get("organizer")),
							Boolean.TRUE.equals(map.get("self"))));
				}
			}
		}
		return guests;
	}

	private static String asText(Object value) {
		return value == null ? "" : String.valueOf(value);
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