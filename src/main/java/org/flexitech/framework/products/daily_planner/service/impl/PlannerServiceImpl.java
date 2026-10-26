package org.flexitech.framework.products.daily_planner.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.framework.products.daily_planner.model.AgendaItem;
import org.flexitech.framework.products.daily_planner.model.PlannerSnapshot;
import org.flexitech.framework.products.daily_planner.service.CalendarService;
import org.flexitech.framework.products.daily_planner.service.PlannerService;
import org.flexitech.framework.products.daily_planner.service.TaskService;

import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_framework.core.annotations.dependency.Use;

@Service
public class PlannerServiceImpl implements PlannerService {

	private static final Logger log = LogManager.getLogger(PlannerServiceImpl.class);

	@Use
	private CalendarService calendarService;

	@Use
	private TaskService taskService;

	@Override
	public PlannerSnapshot loadToday() throws Exception {
		LocalDate today = LocalDate.now();
		List<String> warnings = new ArrayList<String>();
		Exception firstError = null;

		List<AgendaItem> events = new ArrayList<AgendaItem>();
		try {
			events = calendarService.getEvents(today, today);
		} catch (Exception e) {
			log.error("Could not load calendar events", e);
			warnings.add("Calendar: " + e.getMessage());
			firstError = e;
		}

		List<AgendaItem> tasks = new ArrayList<AgendaItem>();
		boolean tasksLoaded = true;
		try {
			tasks = taskService.getTasks(false);
		} catch (Exception e) {
			log.error("Could not load tasks", e);
			warnings.add("Tasks: " + e.getMessage());
			tasksLoaded = false;
			if (firstError == null) {
				firstError = e;
			}
		}

		if (warnings.size() == 2) {
			throw firstError;
		}

		events.sort(Comparator.comparing(AgendaItem::allDay).reversed()
				.thenComparing(AgendaItem::start, Comparator.nullsLast(Comparator.naturalOrder())));

		List<AgendaItem> overdue = new ArrayList<AgendaItem>();
		List<AgendaItem> dueToday = new ArrayList<AgendaItem>();
		List<AgendaItem> upcoming = new ArrayList<AgendaItem>();
		if (tasksLoaded) {
			for (AgendaItem task : tasks) {
				if (task.done()) {
					continue;
				}
				if (task.due() != null && task.due().isBefore(today)) {
					overdue.add(task);
				} else if (task.due() != null && task.due().isEqual(today)) {
					dueToday.add(task);
				} else {
					upcoming.add(task);
				}
			}
		}
		Comparator<AgendaItem> byDue = Comparator.comparing(AgendaItem::due,
				Comparator.nullsLast(Comparator.naturalOrder()));
		overdue.sort(byDue);
		upcoming.sort(byDue);

		log.info("Planner loaded: {} events, {} overdue, {} due today, {} upcoming, {} warnings", events.size(),
				overdue.size(), dueToday.size(), upcoming.size(), warnings.size());
		return new PlannerSnapshot(today, events, overdue, dueToday, upcoming, warnings, LocalDateTime.now());
	}
}