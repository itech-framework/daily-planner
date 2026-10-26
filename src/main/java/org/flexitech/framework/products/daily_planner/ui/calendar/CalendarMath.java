package org.flexitech.framework.products.daily_planner.ui.calendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.flexitech.framework.products.daily_planner.model.AgendaItem;

public final class CalendarMath {

	public static final int MINUTES_PER_DAY = 24 * 60;
	private static final int MIN_VISUAL_MINUTES = 25;

	private CalendarMath() {
	}

	public static final class Slot {
		public final AgendaItem item;
		public final int startMinute;
		public final int endMinute;
		public int column;
		public int columns;

		Slot(AgendaItem item, int startMinute, int endMinute) {
			this.item = item;
			this.startMinute = startMinute;
			this.endMinute = endMinute;
		}
	}

	public static LocalDateTime effectiveEnd(AgendaItem event) {
		LocalDateTime start = event.start();
		LocalDateTime end = event.end();
		return end == null || !end.isAfter(start) ? start.plusMinutes(30) : end;
	}

	public static boolean occursOn(AgendaItem event, LocalDate day) {
		if (event.start() == null) {
			return false;
		}
		LocalDate first = event.start().toLocalDate();
		if (event.allDay()) {
			LocalDate endExclusive = event.end() == null ? first.plusDays(1) : event.end().toLocalDate();
			if (!endExclusive.isAfter(first)) {
				endExclusive = first.plusDays(1);
			}
			return !day.isBefore(first) && day.isBefore(endExclusive);
		}
		LocalDateTime dayStart = day.atStartOfDay();
		LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
		return event.start().isBefore(dayEnd) && effectiveEnd(event).isAfter(dayStart);
	}

	public static List<AgendaItem> allDayEvents(List<AgendaItem> events, LocalDate day) {
		List<AgendaItem> result = new ArrayList<AgendaItem>();
		for (AgendaItem event : events) {
			if (event.allDay() && occursOn(event, day)) {
				result.add(event);
			}
		}
		return result;
	}

	public static List<AgendaItem> timedEvents(List<AgendaItem> events, LocalDate day) {
		List<AgendaItem> result = new ArrayList<AgendaItem>();
		for (AgendaItem event : events) {
			if (!event.allDay() && occursOn(event, day)) {
				result.add(event);
			}
		}
		result.sort(Comparator.comparing(AgendaItem::start));
		return result;
	}

	public static List<AgendaItem> tasksDueOn(List<AgendaItem> tasks, LocalDate day) {
		List<AgendaItem> result = new ArrayList<AgendaItem>();
		for (AgendaItem task : tasks) {
			if (task.due() != null && task.due().equals(day)) {
				result.add(task);
			}
		}
		return result;
	}

	public static List<Slot> arrange(List<AgendaItem> timed, LocalDate day) {
		LocalDateTime dayStart = day.atStartOfDay();
		LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();

		List<Slot> slots = new ArrayList<Slot>();
		for (AgendaItem event : timed) {
			LocalDateTime from = event.start().isBefore(dayStart) ? dayStart : event.start();
			LocalDateTime to = effectiveEnd(event).isAfter(dayEnd) ? dayEnd : effectiveEnd(event);
			int startMinute = (int) ChronoUnit.MINUTES.between(dayStart, from);
			int endMinute = (int) ChronoUnit.MINUTES.between(dayStart, to);
			endMinute = Math.max(endMinute, Math.min(startMinute + MIN_VISUAL_MINUTES, MINUTES_PER_DAY));
			slots.add(new Slot(event, startMinute, endMinute));
		}
		slots.sort(Comparator.<Slot>comparingInt(slot -> slot.startMinute)
				.thenComparing(Comparator.<Slot>comparingInt(slot -> slot.endMinute).reversed()));

		List<Slot> cluster = new ArrayList<Slot>();
		List<Integer> columnEnds = new ArrayList<Integer>();
		int clusterEnd = -1;

		for (Slot slot : slots) {
			if (!cluster.isEmpty() && slot.startMinute >= clusterEnd) {
				finish(cluster, columnEnds.size());
				cluster = new ArrayList<Slot>();
				columnEnds = new ArrayList<Integer>();
			}
			int column = -1;
			for (int i = 0; i < columnEnds.size(); i++) {
				if (columnEnds.get(i) <= slot.startMinute) {
					column = i;
					break;
				}
			}
			if (column < 0) {
				columnEnds.add(slot.endMinute);
				column = columnEnds.size() - 1;
			} else {
				columnEnds.set(column, slot.endMinute);
			}
			slot.column = column;
			cluster.add(slot);
			clusterEnd = cluster.size() == 1 ? slot.endMinute : Math.max(clusterEnd, slot.endMinute);
		}
		finish(cluster, columnEnds.size());
		return slots;
	}

	private static void finish(List<Slot> cluster, int columns) {
		for (Slot slot : cluster) {
			slot.columns = Math.max(1, columns);
		}
	}
}