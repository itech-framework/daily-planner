package org.flexitech.framework.products.daily_planner.model;

import java.time.LocalDateTime;
import java.util.List;

public record EventDraft(String title, String location, String description, boolean allDay, LocalDateTime start,
		LocalDateTime end, boolean addMeet, List<String> attendees, Boolean guestsCanModify,
		Boolean guestsCanInvite) {

	public EventDraft {
		attendees = attendees == null ? List.of() : List.copyOf(attendees);
	}

	public EventDraft(String title, String location, String description, boolean allDay, LocalDateTime start,
			LocalDateTime end, boolean addMeet, List<String> attendees) {
		this(title, location, description, allDay, start, end, addMeet, attendees, null, null);
	}

	public EventDraft(String title, String location, String description, boolean allDay, LocalDateTime start,
			LocalDateTime end) {
		this(title, location, description, allDay, start, end, false, List.of(), null, null);
	}
}