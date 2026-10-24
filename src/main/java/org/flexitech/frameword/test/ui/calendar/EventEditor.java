package org.flexitech.frameword.test.ui.calendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import org.flexitech.frameword.test.model.AgendaItem;
import org.flexitech.frameword.test.model.EventDraft;

import io.github.itech_framework.java_fx.ui.dialog.ModalDialog;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class EventEditor {

	private static final DateTimeFormatter TIME_INPUT = DateTimeFormatter.ofPattern("H:mm");
	private static final DateTimeFormatter TIME_SHOW = DateTimeFormatter.ofPattern("HH:mm");

	private EventEditor() {
	}

	public static void open(Stage owner, AgendaItem existing, LocalDateTime suggestedStart, boolean suggestedAllDay,
			Consumer<EventDraft> onSave) {
		boolean editing = existing != null;
		boolean allDayInitial;
		LocalDateTime start;
		LocalDateTime end;

		if (editing) {
			allDayInitial = existing.allDay();
			start = existing.start();
			if (allDayInitial) {
				LocalDate last = existing.end() == null ? start.toLocalDate()
						: existing.end().toLocalDate().minusDays(1);
				if (last.isBefore(start.toLocalDate())) {
					last = start.toLocalDate();
				}
				end = last.atStartOfDay();
			} else {
				end = existing.end() == null || !existing.end().isAfter(start) ? start.plusHours(1) : existing.end();
			}
		} else {
			allDayInitial = suggestedAllDay;
			start = suggestedStart;
			end = suggestedAllDay ? suggestedStart : suggestedStart.plusHours(1);
		}

		Label heading = new Label(editing ? "Edit event" : "New event");
		heading.getStyleClass().add("alert-title");

		TextField title = new TextField(editing ? existing.title() : "");
		title.setPromptText("Title");

		CheckBox allDay = new CheckBox("All day");
		allDay.setSelected(allDayInitial);

		DatePicker startDate = new DatePicker(start.toLocalDate());
		DatePicker endDate = new DatePicker(end.toLocalDate());
		ComboBox<String> startTime = timeBox(start.toLocalTime());
		ComboBox<String> endTime = timeBox(end.toLocalTime());

		TextField location = new TextField(editing && existing.detail() != null ? existing.detail() : "");
		location.setPromptText("Location");

		TextArea description = new TextArea(editing && existing.description() != null ? existing.description() : "");
		description.setPromptText("Description");
		description.setPrefRowCount(3);
		description.setWrapText(true);

		Label error = new Label(" ");
		error.setWrapText(true);
		error.setStyle("-fx-text-fill: #d93025;");

		Runnable syncTimes = () -> {
			boolean timed = !allDay.isSelected();
			for (Node node : new Node[] { startTime, endTime }) {
				node.setVisible(timed);
				node.setManaged(timed);
			}
		};
		allDay.selectedProperty().addListener((observable, previous, current) -> syncTimes.run());
		syncTimes.run();

		startDate.valueProperty().addListener((observable, previous, current) -> {
			if (current != null && (endDate.getValue() == null || endDate.getValue().isBefore(current))) {
				endDate.setValue(current);
			}
		});

		VBox body = new VBox(8, heading, title, allDay, caption("Starts"), new HBox(8, startDate, startTime),
				caption("Ends"), new HBox(8, endDate, endTime), location, description);
		if (editing && existing.recurringEventId() != null) {
			body.getChildren().add(1, caption("Repeating event: changes apply to this occurrence only."));
		}
		body.getChildren().add(error);
		body.setPrefWidth(420);

		ModalDialog[] holder = new ModalDialog[1];
		holder[0] = ModalDialog.builder().addOwner(owner).addContent(body).addCloseButton("Cancel")
				.addActionButton("Save", () -> {
					try {
						EventDraft draft = draftFrom(title, allDay, startDate, startTime, endDate, endTime, location,
								description);
						holder[0].close();
						onSave.accept(draft);
					} catch (IllegalArgumentException e) {
						error.setText(e.getMessage());
					}
				}).build();
		holder[0].show();
	}

	private static EventDraft draftFrom(TextField title, CheckBox allDay, DatePicker startDate,
			ComboBox<String> startTime, DatePicker endDate, ComboBox<String> endTime, TextField location,
			TextArea description) {
		String name = title.getText() == null ? "" : title.getText().trim();
		if (name.isEmpty()) {
			throw new IllegalArgumentException("Please enter a title.");
		}
		LocalDate from = startDate.getValue();
		LocalDate to = endDate.getValue();
		if (from == null || to == null) {
			throw new IllegalArgumentException("Please choose the start and end dates.");
		}

		LocalDateTime start;
		LocalDateTime end;
		if (allDay.isSelected()) {
			if (to.isBefore(from)) {
				throw new IllegalArgumentException("The end date cannot be before the start date.");
			}
			start = from.atStartOfDay();
			end = to.atStartOfDay();
		} else {
			start = LocalDateTime.of(from, parseTime(startTime, "start"));
			end = LocalDateTime.of(to, parseTime(endTime, "end"));
			if (!end.isAfter(start)) {
				throw new IllegalArgumentException("The end must be after the start.");
			}
		}
		return new EventDraft(name, text(location.getText()), text(description.getText()), allDay.isSelected(), start,
				end);
	}

	private static LocalTime parseTime(ComboBox<String> box, String label) {
		try {
			return LocalTime.parse(timeText(box).trim(), TIME_INPUT);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("Enter the " + label + " time as HH:mm, for example 14:30.");
		}
	}

	private static String timeText(ComboBox<String> box) {
		String text = box.getEditor().getText();
		return text == null || text.isBlank() ? box.getValue() : text;
	}

	private static ComboBox<String> timeBox(LocalTime value) {
		ComboBox<String> box = new ComboBox<String>();
		box.setEditable(true);
		for (int minute = 0; minute < 24 * 60; minute += 15) {
			box.getItems().add(TIME_SHOW.format(LocalTime.of(minute / 60, minute % 60)));
		}
		box.setValue(TIME_SHOW.format(value));
		box.setPrefWidth(100);
		return box;
	}

	private static Label caption(String text) {
		Label label = new Label(text);
		label.getStyleClass().add("muted");
		label.setWrapText(true);
		return label;
	}

	private static String text(String value) {
		return value == null ? "" : value.trim();
	}
}