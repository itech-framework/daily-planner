package org.flexitech.framework.products.daily_planner.api;

import java.util.Map;

import org.flexitech.framework.products.daily_planner.api.request.CalendarEventRequest;
import org.flexitech.framework.products.daily_planner.api.response.CalendarEvent;
import org.flexitech.framework.products.daily_planner.api.response.CalendarEventList;

import io.github.itech_framework.api_client.annotations.ApiClient;
import io.github.itech_framework.api_client.annotations.methods.DELETE;
import io.github.itech_framework.api_client.annotations.methods.GET;
import io.github.itech_framework.api_client.annotations.methods.POST;
import io.github.itech_framework.api_client.annotations.methods.PUT;
import io.github.itech_framework.api_client.annotations.parameters.Body;
import io.github.itech_framework.api_client.annotations.parameters.Headers;
import io.github.itech_framework.api_client.annotations.parameters.Path;
import io.github.itech_framework.api_client.annotations.parameters.Query;

@ApiClient(baseUrl = "https://www.googleapis.com")
public interface GoogleCalendarAPI {

	@GET("/calendar/v3/calendars/{calendarId}/events")
	CalendarEventList listEvents(@Headers Map<String, String> headers,
			@Path("calendarId") String calendarId,
			@Query("timeMin") String timeMin,
			@Query("timeMax") String timeMax,
			@Query("singleEvents") String singleEvents,
			@Query("orderBy") String orderBy,
			@Query("maxResults") String maxResults);

	@GET("/calendar/v3/calendars/{calendarId}/events")
	CalendarEventList listEventsPage(@Headers Map<String, String> headers,
			@Path("calendarId") String calendarId,
			@Query("timeMin") String timeMin,
			@Query("timeMax") String timeMax,
			@Query("singleEvents") String singleEvents,
			@Query("orderBy") String orderBy,
			@Query("maxResults") String maxResults,
			@Query("pageToken") String pageToken);

	@POST("/calendar/v3/calendars/{calendarId}/events")
	CalendarEvent createEvent(@Headers Map<String, String> headers,
			@Path("calendarId") String calendarId,
			@Body CalendarEventRequest body);

	@PUT("/calendar/v3/calendars/{calendarId}/events/{eventId}")
	CalendarEvent updateEvent(@Headers Map<String, String> headers,
			@Path("calendarId") String calendarId,
			@Path("eventId") String eventId,
			@Body CalendarEvent body);

	@DELETE("/calendar/v3/calendars/{calendarId}/events/{eventId}")
	void deleteEvent(@Headers Map<String, String> headers,
			@Path("calendarId") String calendarId,
			@Path("eventId") String eventId);
	
	@POST("/calendar/v3/calendars/{calendarId}/events")
	CalendarEvent createEventWithConference(@Headers Map<String, String> headers,
			@Path("calendarId") String calendarId,
			@Query("conferenceDataVersion") String conferenceDataVersion,
			@Query("sendUpdates") String sendUpdates,
			@Body CalendarEventRequest body);
	
	@GET("/calendar/v3/calendars/{calendarId}/events/{eventId}")
	CalendarEvent getEvent(@Headers Map<String, String> headers,
			@Path("calendarId") String calendarId,
			@Path("eventId") String eventId);
	
	@PUT("/calendar/v3/calendars/{calendarId}/events/{eventId}")
	CalendarEvent updateEventWithConference(@Headers Map<String, String> headers,
			@Path("calendarId") String calendarId,
			@Path("eventId") String eventId,
			@Query("conferenceDataVersion") String conferenceDataVersion,
			@Query("sendUpdates") String sendUpdates,
			@Body CalendarEvent body);
}