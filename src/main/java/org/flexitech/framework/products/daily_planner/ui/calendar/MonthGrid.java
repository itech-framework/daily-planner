package org.flexitech.framework.products.daily_planner.ui.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import org.flexitech.framework.products.daily_planner.model.AgendaItem;
import org.flexitech.framework.products.daily_planner.model.Holiday;
import org.flexitech.framework.products.daily_planner.ui.component.EventChip;

import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public final class MonthGrid {

	private static final int MAX_ROWS = 4;
	private static final double ROW_HEIGHT = 112;
	private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM");

	private MonthGrid() {
	}

	public static Node create(LocalDate anchor, DayOfWeek firstDay, List<AgendaItem> events, List<AgendaItem> tasks,
			Map<LocalDate, List<Holiday>> holidays, Consumer<AgendaItem> onOpen, Consumer<LocalDate> onDay,
			Consumer<LocalDate> onCreate) {
		Map<LocalDate, List<Holiday>> holidayMap = holidays == null ? Map.of() : holidays;
		LocalDate monthStart = anchor.withDayOfMonth(1);
		LocalDate gridStart = monthStart.with(TemporalAdjusters.previousOrSame(firstDay));
		LocalDate gridEnd = anchor.withDayOfMonth(anchor.lengthOfMonth())
				.with(TemporalAdjusters.nextOrSame(firstDay.minus(1)));
		int weeks = (int) (ChronoUnit.DAYS.between(gridStart, gridEnd) + 1) / 7;

		GridPane grid = new GridPane();
		grid.getStyleClass().add("month-grid");

		for (int column = 0; column < 7; column++) {
			ColumnConstraints constraints = new ColumnConstraints();
			constraints.setPercentWidth(100.0 / 7);
			constraints.setHgrow(Priority.ALWAYS);
			grid.getColumnConstraints().add(constraints);
		}

		RowConstraints headerRow = new RowConstraints();
		headerRow.setMinHeight(24);
		headerRow.setPrefHeight(24);
		headerRow.setMaxHeight(24);
		grid.getRowConstraints().add(headerRow);
		for (int week = 0; week < weeks; week++) {
			RowConstraints row = new RowConstraints();
			row.setMinHeight(ROW_HEIGHT);
			row.setPrefHeight(ROW_HEIGHT);
			row.setVgrow(Priority.ALWAYS);
			grid.getRowConstraints().add(row);
		}

		for (int column = 0; column < 7; column++) {
			Label name = new Label(firstDay.plus(column).getDisplayName(TextStyle.SHORT, Locale.getDefault()).toUpperCase());
			name.getStyleClass().add("weekday-label");
			name.setMaxWidth(Double.MAX_VALUE);
			name.setAlignment(Pos.CENTER);
			grid.add(name, column, 0);
		}

		LocalDate today = LocalDate.now();
		for (int index = 0; index < weeks * 7; index++) {
			LocalDate day = gridStart.plusDays(index);
			Node cell = buildCell(day, day.getMonth() == anchor.getMonth(), day.equals(today), events, tasks,
					holidayMap.get(day), onOpen, onDay, onCreate);
			grid.add(cell, index % 7, index / 7 + 1);
		}

		ScrollPane scroll = new ScrollPane(grid);
		scroll.setFitToWidth(true);
		scroll.setFitToHeight(true);
		scroll.setHbarPolicy(ScrollBarPolicy.NEVER);
		scroll.getStyleClass().add("page-scroll");
		return scroll;
	}

	private static Node buildCell(LocalDate day, boolean inMonth, boolean today, List<AgendaItem> events,
			List<AgendaItem> tasks, List<Holiday> dayHolidays, Consumer<AgendaItem> onOpen,
			Consumer<LocalDate> onDay, Consumer<LocalDate> onCreate) {
		VBox cell = new VBox(2);
		cell.getStyleClass().add("month-cell");
		if (!inMonth) {
			cell.getStyleClass().add("month-cell-other");
		}
		if (today) {
			cell.getStyleClass().add("month-cell-today");
		}
		cell.setMinSize(0, 0);
		HolidayStyle.apply(cell, dayHolidays);

		Label number = new Label(day.getDayOfMonth() == 1 ? DAY_MONTH.format(day) : String.valueOf(day.getDayOfMonth()));
		number.getStyleClass().add("date-badge");
		if (today) {
			number.getStyleClass().add("date-today");
		}
		HBox top = new HBox(number);
		top.setAlignment(Pos.CENTER);
		cell.getChildren().add(top);

		List<Label> observances = HolidayStyle.labels(dayHolidays);
		cell.getChildren().addAll(observances);

		List<AgendaItem> items = new ArrayList<AgendaItem>();
		items.addAll(CalendarMath.allDayEvents(events, day));
		items.addAll(CalendarMath.timedEvents(events, day));
		items.addAll(CalendarMath.tasksDueOn(tasks, day));

		int budget = Math.max(1, MAX_ROWS - observances.size());
		int visible = items.size() > budget ? Math.max(0, budget - 1) : items.size();
		for (int i = 0; i < visible; i++) {
			cell.getChildren().add(EventChip.create(items.get(i), false, onOpen));
		}
		if (items.size() > visible) {
			Label more = new Label("+" + (items.size() - visible) + " more");
			more.getStyleClass().add("more-link");
			more.setOnMouseClicked(event -> {
				onDay.accept(day);
				event.consume();
			});
			cell.getChildren().add(more);
		}

		PauseTransition pending = new PauseTransition(Duration.millis(250));
		pending.setOnFinished(event -> onDay.accept(day));
		cell.setOnMouseClicked(event -> {
			if (event.getClickCount() >= 2) {
				pending.stop();
				onCreate.accept(day);
			} else {
				pending.playFromStart();
			}
		});

		Rectangle clip = new Rectangle();
		clip.widthProperty().bind(cell.widthProperty());
		clip.heightProperty().bind(cell.heightProperty());
		cell.setClip(clip);
		return cell;
	}
}