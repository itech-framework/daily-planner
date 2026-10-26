package org.flexitech.framework.products.daily_planner.service;

import java.time.LocalDate;
import java.util.List;

import org.flexitech.framework.products.daily_planner.model.AgendaItem;
import org.flexitech.framework.products.daily_planner.model.EventDraft;
import org.flexitech.framework.products.daily_planner.model.Holiday;

public interface CalendarService {

	List<AgendaItem> getEvents(LocalDate from, LocalDate to) throws Exception;

	AgendaItem createEvent(EventDraft draft) throws Exception;

	AgendaItem updateEvent(AgendaItem event, EventDraft draft) throws Exception;

	void deleteEvent(AgendaItem event) throws Exception;
	
	List<Holiday> getHolidays(String calendarId, LocalDate from, LocalDate to) throws Exception;
	
	List<Holiday> getAllHolidays(String calendarIds, LocalDate from, LocalDate to);
}