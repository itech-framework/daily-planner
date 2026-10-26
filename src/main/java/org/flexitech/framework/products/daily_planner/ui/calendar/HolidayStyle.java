package org.flexitech.framework.products.daily_planner.ui.calendar;

import java.util.ArrayList;
import java.util.List;

import org.flexitech.framework.products.daily_planner.model.Holiday;

import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;

public final class HolidayStyle {

	private HolidayStyle() {
	}

	public static void apply(Region cell, List<Holiday> holidays) {
		if (holidays == null || holidays.isEmpty()) {
			return;
		}
		if (holidays.stream().anyMatch(Holiday::publicHoliday)) {
			cell.getStyleClass().add("day-holiday");
		}
	}

	public static List<Label> labels(List<Holiday> holidays) {
		List<Label> result = new ArrayList<Label>();
		if (holidays == null) {
			return result;
		}
		holidays.stream().filter(Holiday::hasName).filter(Holiday::publicHoliday)
				.forEach(holiday -> result.add(chip(holiday, "chip-holiday")));
		holidays.stream().filter(Holiday::hasName).filter(holiday -> !holiday.publicHoliday())
				.forEach(holiday -> result.add(chip(holiday, "chip-observance")));
		return result;
	}

	private static Label chip(Holiday holiday, String kind) {
		Label label = new Label(holiday.name());
		label.getStyleClass().addAll("chip", "chip-title", kind);
		label.setMaxWidth(Double.MAX_VALUE);
		label.setMinWidth(0);
		label.setTooltip(new Tooltip(holiday.name()));
		return label;
	}
}