package org.flexitech.frameword.test.service;

import java.time.LocalDate;
import java.util.List;

import org.flexitech.frameword.test.model.AgendaItem;
import org.flexitech.frameword.test.model.EventDraft;

public interface CalendarService {

	List<AgendaItem> getEvents(LocalDate from, LocalDate to) throws Exception;

	AgendaItem createEvent(EventDraft draft) throws Exception;

	AgendaItem updateEvent(AgendaItem event, EventDraft draft) throws Exception;

	void deleteEvent(AgendaItem event) throws Exception;
}