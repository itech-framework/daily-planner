package org.flexitech.frameword.test.ui.calendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.flexitech.frameword.test.model.AgendaItem;
import org.flexitech.frameword.test.ui.component.EventChip;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public final class TimeGrid extends VBox {

	private static final double GUTTER = 56;
	private static final int MAX_ALL_DAY = 4;
	private static final int TWO_LINE_MINUTES = 50;
	private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

	private final double hourHeight;
	private final Consumer<AgendaItem> onOpen;
	private final Consumer<LocalDate> onDay;
	
	private final Consumer<LocalDateTime> onCreate;

	public TimeGrid(List<LocalDate> days, List<AgendaItem> events, List<AgendaItem> tasks, double hourHeight,
			int focusHour, Consumer<AgendaItem> onOpen, Consumer<LocalDate> onDay, Consumer<LocalDateTime> onCreate) {
		this.hourHeight = hourHeight;
		this.onOpen = onOpen;
		this.onDay = onDay;
		this.onCreate = onCreate;
		getStyleClass().add("time-grid");

		getChildren().add(buildHeader(days));

		HBox allDay = buildAllDay(days, events, tasks);
		if (allDay != null) {
			getChildren().add(allDay);
		}

		ScrollPane body = buildBody(days, events);
		VBox.setVgrow(body, Priority.ALWAYS);
		getChildren().add(body);
		scrollToHour(body, focusHour);
	}

	private HBox buildHeader(List<LocalDate> days) {
		HBox header = new HBox();
		header.getStyleClass().add("time-header");
		header.getChildren().add(gutterSpacer());

		LocalDate today = LocalDate.now();
		for (LocalDate day : days) {
			Label name = new Label(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault()).toUpperCase());
			name.getStyleClass().add("weekday-label");

			Label number = new Label(String.valueOf(day.getDayOfMonth()));
			number.getStyleClass().add("date-badge");
			if (day.equals(today)) {
				number.getStyleClass().add("date-today");
			}

			VBox cell = new VBox(1, name, number);
			cell.setAlignment(Pos.CENTER);
			cell.getStyleClass().add("time-header-cell");
			sizeAsColumn(cell);
			if (days.size() > 1) {
				cell.setCursor(Cursor.HAND);
				cell.setOnMouseClicked(event -> onDay.accept(day));
			}
			header.getChildren().add(cell);
		}
		return header;
	}

	private HBox buildAllDay(List<LocalDate> days, List<AgendaItem> events, List<AgendaItem> tasks) {
		boolean any = false;
		HBox row = new HBox();
		row.getStyleClass().add("allday-row");

		Label caption = new Label("all-day");
		caption.getStyleClass().add("time-label");
		caption.setAlignment(Pos.TOP_RIGHT);
		caption.setMinWidth(GUTTER);
		caption.setPrefWidth(GUTTER);
		caption.setMaxWidth(GUTTER);
		row.getChildren().add(caption);

		for (LocalDate day : days) {
			List<AgendaItem> items = new ArrayList<AgendaItem>();
			items.addAll(CalendarMath.allDayEvents(events, day));
			items.addAll(CalendarMath.tasksDueOn(tasks, day));
			any = any || !items.isEmpty();

			VBox cell = new VBox(2);
			cell.getStyleClass().add("allday-cell");
			sizeAsColumn(cell);

			int visible = items.size() > MAX_ALL_DAY ? MAX_ALL_DAY - 1 : items.size();
			for (int i = 0; i < visible; i++) {
				cell.getChildren().add(EventChip.create(items.get(i), false, onOpen));
			}
			if (items.size() > visible) {
				Label more = new Label("+" + (items.size() - visible) + " more");
				more.getStyleClass().add("more-link");
				more.setOnMouseClicked(event -> onDay.accept(day));
				cell.getChildren().add(more);
			}
			row.getChildren().add(cell);
		}
		return any ? row : null;
	}

	private ScrollPane buildBody(List<LocalDate> days, List<AgendaItem> events) {
		VBox gutter = new VBox();
		gutter.setMinWidth(GUTTER);
		gutter.setPrefWidth(GUTTER);
		gutter.setMaxWidth(GUTTER);
		for (int hour = 0; hour < 24; hour++) {
			Label label = new Label(hour == 0 ? "" : HOUR.format(LocalTime.of(hour, 0)));
			label.getStyleClass().add("time-label");
			label.setAlignment(Pos.TOP_RIGHT);
			label.setMinHeight(hourHeight);
			label.setPrefHeight(hourHeight);
			label.setMaxHeight(hourHeight);
			label.setMaxWidth(Double.MAX_VALUE);
			label.setTranslateY(-7);
			gutter.getChildren().add(label);
		}

		HBox content = new HBox(gutter);
		content.getStyleClass().add("time-body");

		LocalDate today = LocalDate.now();
		for (LocalDate day : days) {
			DayColumn column = new DayColumn(hourHeight, day.equals(today), day, onCreate);
			sizeAsColumn(column);
			for (CalendarMath.Slot slot : CalendarMath.arrange(CalendarMath.timedEvents(events, day), day)) {
				boolean twoLines = slot.endMinute - slot.startMinute >= TWO_LINE_MINUTES;
				Region chip = EventChip.create(slot.item, twoLines, onOpen);
				clipTo(chip);
				column.place(slot, chip);
			}
			column.finish();
			content.getChildren().add(column);
		}

		ScrollPane body = new ScrollPane(content);
		body.setFitToWidth(true);
		body.setHbarPolicy(ScrollBarPolicy.NEVER);
		body.setVbarPolicy(ScrollBarPolicy.NEVER);
		body.getStyleClass().add("page-scroll");
		return body;
	}

	private void scrollToHour(ScrollPane body, int hour) {
		body.viewportBoundsProperty().addListener(new ChangeListener<Bounds>() {
			@Override
			public void changed(javafx.beans.value.ObservableValue<? extends Bounds> observable, Bounds oldValue,
					Bounds newValue) {
				if (newValue.getHeight() <= 0) {
					return;
				}
				double scrollable = 24 * hourHeight - newValue.getHeight();
				if (scrollable > 0) {
					body.setVvalue(Math.max(0, Math.min(1, (hour * hourHeight - 8) / scrollable)));
				}
				body.viewportBoundsProperty().removeListener(this);
			}
		});
	}

	private static Region gutterSpacer() {
		Region spacer = new Region();
		spacer.setMinWidth(GUTTER);
		spacer.setPrefWidth(GUTTER);
		spacer.setMaxWidth(GUTTER);
		return spacer;
	}

	private static void sizeAsColumn(Region region) {
		region.setMinWidth(0);
		region.setPrefWidth(0);
		region.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(region, Priority.ALWAYS);
	}

	private static void clipTo(Region region) {
		Rectangle clip = new Rectangle();
		clip.widthProperty().bind(region.widthProperty());
		clip.heightProperty().bind(region.heightProperty());
		region.setClip(clip);
	}

	private static final class DayColumn extends Pane {

		private final double hourHeight;
		private final List<Region> hourLines = new ArrayList<Region>();
		private final List<CalendarMath.Slot> slots = new ArrayList<CalendarMath.Slot>();
		private final List<Region> chips = new ArrayList<Region>();
		private final boolean today;
		private Region nowLine;

		DayColumn(double hourHeight, boolean today, LocalDate day, Consumer<LocalDateTime> onCreate) {
			this.hourHeight = hourHeight;
			this.today = today;
			getStyleClass().add("day-column");
			if (today) {
				getStyleClass().add("day-column-today");
			}
			for (int hour = 0; hour < 24; hour++) {
				Region line = new Region();
				line.getStyleClass().add("hour-line");
				line.setMouseTransparent(true);
				hourLines.add(line);
				getChildren().add(line);
			}
			setOnMouseClicked(event -> {
				if (onCreate == null) {
					return;
				}
				int minute = (int) (event.getY() / hourHeight * 60);
				minute = Math.max(0, Math.min(CalendarMath.MINUTES_PER_DAY - 15, minute / 15 * 15));
				onCreate.accept(day.atTime(minute / 60, minute % 60));
			});
		}

		void place(CalendarMath.Slot slot, Region chip) {
			slots.add(slot);
			chips.add(chip);
			getChildren().add(chip);
		}

		void finish() {
			if (!today) {
				return;
			}
			nowLine = new Region();
			nowLine.getStyleClass().add("now-line");
			nowLine.setMouseTransparent(true);
			getChildren().add(nowLine);

			Timeline ticker = new Timeline(new KeyFrame(Duration.seconds(60), event -> requestLayout()));
			ticker.setCycleCount(Animation.INDEFINITE);
			ticker.play();
			sceneProperty().addListener((observable, oldScene, newScene) -> {
				if (newScene == null) {
					ticker.stop();
				}
			});
		}

		@Override
		protected double computePrefHeight(double width) {
			return hourHeight * 24;
		}

		@Override
		protected double computePrefWidth(double height) {
			return 0;
		}

		@Override
		protected void layoutChildren() {
			double width = getWidth();
			for (int hour = 0; hour < hourLines.size(); hour++) {
				hourLines.get(hour).resizeRelocate(0, hour * hourHeight, width, 1);
			}
			for (int i = 0; i < chips.size(); i++) {
				CalendarMath.Slot slot = slots.get(i);
				double columnWidth = width / slot.columns;
				double x = slot.column * columnWidth;
				double y = slot.startMinute * hourHeight / 60.0;
				double height = Math.max(16, (slot.endMinute - slot.startMinute) * hourHeight / 60.0);
				chips.get(i).resizeRelocate(x + 1, y + 1, Math.max(0, columnWidth - 3), Math.max(0, height - 2));
			}
			if (nowLine != null) {
				LocalTime now = LocalTime.now();
				double y = (now.getHour() * 60 + now.getMinute()) * hourHeight / 60.0;
				nowLine.resizeRelocate(0, y - 1, width, 2);
			}
		}
	}
}