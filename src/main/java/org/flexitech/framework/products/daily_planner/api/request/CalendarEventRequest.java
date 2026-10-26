package org.flexitech.framework.products.daily_planner.api.request;

import java.util.List;

import org.flexitech.framework.products.daily_planner.api.response.ConferenceData;
import org.flexitech.framework.products.daily_planner.api.response.EventAttendee;
import org.flexitech.framework.products.daily_planner.api.response.EventDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CalendarEventRequest {
	private String summary;
	private String description;
	private String location;
	private EventDateTime start;
	private EventDateTime end;
	private ConferenceData conferenceData;
	private List<EventAttendee> attendees;
	private Boolean guestsCanModify;
	private Boolean guestsCanInviteOthers;
}