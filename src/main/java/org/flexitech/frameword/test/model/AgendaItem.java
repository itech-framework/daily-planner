package org.flexitech.frameword.test.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AgendaItem(String id, Type type, String title, String detail, LocalDateTime start, LocalDateTime end,
		boolean allDay, LocalDate due, boolean done, String listId, String listTitle, String link,
		String description, String recurringEventId) {

	public enum Type {
		EVENT, TASK
	}

	public AgendaItem withDone(boolean value) {
		return new AgendaItem(id, type, title, detail, start, end, allDay, due, value, listId, listTitle, link,
				description, recurringEventId);
	}
}