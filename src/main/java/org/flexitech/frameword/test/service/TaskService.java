package org.flexitech.frameword.test.service;

import java.time.LocalDate;
import java.util.List;

import org.flexitech.frameword.test.model.AgendaItem;

public interface TaskService {

	List<AgendaItem> getTasks(boolean includeCompleted) throws Exception;

	AgendaItem createTask(String title, String notes, LocalDate due) throws Exception;

	AgendaItem setDone(AgendaItem task, boolean done) throws Exception;
}