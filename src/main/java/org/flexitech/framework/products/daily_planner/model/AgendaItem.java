package org.flexitech.framework.products.daily_planner.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AgendaItem(String id, Type type, String title, String detail, LocalDateTime start, LocalDateTime end,
		boolean allDay, LocalDate due, boolean done, String listId, String listTitle, String link,
		String description, String recurringEventId, List<Guest> guests, boolean owner, boolean guestsCanModify,
		boolean guestsCanInvite) {

	public enum Type {
		EVENT, TASK
	}

	public record Guest(String email, String name, String status, boolean organizer, boolean self) {
	}

	public AgendaItem {
		guests = guests == null ? List.of() : List.copyOf(guests);
	}

	public AgendaItem(String id, Type type, String title, String detail, LocalDateTime start, LocalDateTime end,
			boolean allDay, LocalDate due, boolean done, String listId, String listTitle, String link,
			String description, String recurringEventId) {
		this(id, type, title, detail, start, end, allDay, due, done, listId, listTitle, link, description,
				recurringEventId, List.of(), true, false, true);
	}

	public AgendaItem(String id, Type type, String title, String detail, LocalDateTime start, LocalDateTime end,
			boolean allDay, LocalDate due, boolean done, String listId, String listTitle, String link,
			String description, String recurringEventId, List<Guest> guests) {
		this(id, type, title, detail, start, end, allDay, due, done, listId, listTitle, link, description,
				recurringEventId, guests, true, false, true);
	}

	public boolean canEdit() {
		return owner || guestsCanModify;
	}

	public AgendaItem withDone(boolean value) {
		return new AgendaItem(id, type, title, detail, start, end, allDay, due, value, listId, listTitle, link,
				description, recurringEventId, guests, owner, guestsCanModify, guestsCanInvite);
	}
}