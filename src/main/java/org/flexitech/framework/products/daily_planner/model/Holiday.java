package org.flexitech.framework.products.daily_planner.model;

import java.time.LocalDate;

public record Holiday(LocalDate date, String name, boolean publicHoliday) {

	public static Holiday nonWorking(LocalDate date) {
		return new Holiday(date, "", true);
	}

	public boolean hasName() {
		return name != null && !name.isBlank();
	}
}