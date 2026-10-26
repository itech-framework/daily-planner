package org.flexitech.framework.products.daily_planner.util;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public final class OffDays {

	private OffDays() {
	}

	public static Set<DayOfWeek> parse(String value) {
		Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
		if (value == null) {
			return days;
		}
		for (String part : value.split(",")) {
			String name = part.trim().toUpperCase(Locale.ROOT);
			if (name.isEmpty()) {
				continue;
			}
			try {
				days.add(DayOfWeek.valueOf(name));
			} catch (IllegalArgumentException ignored) {
			}
		}
		return days;
	}
}