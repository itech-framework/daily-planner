package org.flexitech.framework.products.daily_planner.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PlannerSnapshot(LocalDate date, List<AgendaItem> events, List<AgendaItem> overdueTasks,
		List<AgendaItem> todayTasks, List<AgendaItem> upcomingTasks, List<String> warnings,
		LocalDateTime loadedAt) {

	public int openTaskCount() {
		return overdueTasks.size() + todayTasks.size() + upcomingTasks.size();
	}
}