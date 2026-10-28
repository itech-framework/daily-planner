package org.flexitech.framework.products.daily_planner.ui.calendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.flexitech.framework.products.daily_planner.DailyPlannerAppMain;
import org.flexitech.framework.products.daily_planner.model.AgendaItem;
import org.flexitech.framework.products.daily_planner.model.EventDraft;

import io.github.itech_framework.java_fx.ui.dialog.ModalDialog;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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
		boolean organizer = !editing || existing.owner();
		boolean canEdit = !editing || existing.canEdit();
		boolean canInvite = organizer || existing.guestsCanInvite();
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

		Label heading = new Label(editing ? (canEdit ? "Edit event" : "Event details") : "New event");
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

		CheckBox meet = new CheckBox("Add Google Meet video call");

		TextField attendees = new TextField();
		attendees.setPromptText(editing ? "Add guests (emails separated by commas)"
				: "Invite guests (emails separated by commas)");

		CheckBox guestsModify = new CheckBox("Guests can modify event");
		guestsModify.setSelected(editing && existing.guestsCanModify());

		CheckBox guestsInvite = new CheckBox("Guests can invite others");
		guestsInvite.setSelected(editing && existing.guestsCanInvite());

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
		if (!canEdit) {
			body.getChildren().add(1, caption("Only the organizer can change this event."));
		}

		if (editing) {
			String link = existing.link();
			boolean hasMeet = link != null && link.contains("meet.google.com");
			if (hasMeet) {
				Hyperlink join = new Hyperlink("Open in Google Meet");
				join.setOnAction(event -> {
					if (DailyPlannerAppMain.hostServices() != null) {
						DailyPlannerAppMain.hostServices().showDocument(link);
					}
				});
				body.getChildren().addAll(caption("Google Meet is already added to this event."), join);
			} else if (canEdit) {
				body.getChildren().add(meet);
			}
			if (!existing.guests().isEmpty()) {
				body.getChildren().add(guestList(existing.guests()));
			}
			if (canEdit && canInvite) {
				body.getChildren().add(attendees);
			}
		} else {
			body.getChildren().addAll(meet, attendees);
		}

		if (organizer) {
			body.getChildren().addAll(guestsModify, guestsInvite);
		}

		if (!canEdit) {
			for (Node node : new Node[] { title, allDay, startDate, startTime, endDate, endTime, location,
					description }) {
				node.setDisable(true);
			}
		}

		body.getChildren().add(error);
		body.setPrefWidth(420);

		ModalDialog.ModalDialogBuilder builder = ModalDialog.builder().addOwner(owner).addContent(body);
		if (canEdit) {
			ModalDialog[] holder = new ModalDialog[1];
			boolean[] submitted = { false };
			builder.addCloseButton("Cancel").addActionButton("Save", () -> {
				if (submitted[0]) {
					return;
				}
				EventDraft draft;
				try {
					draft = draftFrom(title, allDay, startDate, startTime, endDate, endTime, location,
							description, meet, attendees, organizer ? guestsModify : null,
							organizer ? guestsInvite : null);
				} catch (IllegalArgumentException e) {
					error.setText(e.getMessage());
					return;
				}
				submitted[0] = true;
				body.setDisable(true);
				holder[0].close();
				onSave.accept(draft);
			});
			holder[0] = builder.build();
			holder[0].show();
		} else {
			builder.addCloseButton("Close").build().show();
		}
	}

	private static EventDraft draftFrom(TextField title, CheckBox allDay, DatePicker startDate,
			ComboBox<String> startTime, DatePicker endDate, ComboBox<String> endTime, TextField location,
			TextArea description, CheckBox meet, TextField attendees, CheckBox guestsModify,
			CheckBox guestsInvite) {
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
		Boolean modify = guestsModify == null ? null : guestsModify.isSelected();
		Boolean invite = guestsInvite == null ? null : guestsInvite.isSelected();
		return new EventDraft(name, text(location.getText()), text(description.getText()), allDay.isSelected(), start,
				end, meet.isSelected(), parseEmails(attendees.getText()), modify, invite);
	}

	private static List<String> parseEmails(String value) {
		Set<String> emails = new LinkedHashSet<String>();
		if (value != null) {
			for (String part : value.split("[,;\\s]+")) {
				String email = part.trim();
				if (email.isEmpty()) {
					continue;
				}
				int at = email.indexOf('@');
				if (at < 1 || at == email.length() - 1) {
					throw new IllegalArgumentException("'" + email + "' is not a valid email address.");
				}
				emails.add(email.toLowerCase());
			}
		}
		return new ArrayList<String>(emails);
	}

	private static Node guestList(List<AgendaItem.Guest> guests) {
		VBox rows = new VBox(4);
		for (AgendaItem.Guest guest : guests) {
			String status = effectiveStatus(guest);

			Label mark = new Label(statusMark(status));
			mark.setMinWidth(18);
			mark.setStyle("-fx-font-weight: bold; -fx-text-fill: " + statusColor(status) + ";");

			String shown = guest.name().isBlank() ? guest.email() : guest.name() + " (" + guest.email() + ")";
			Label name = new Label(shown);
			name.setWrapText(true);
			HBox row = new HBox(8, mark, name);

			String tag = guest.organizer() && guest.self() ? "Organizer, you"
					: guest.organizer() ? "Organizer" : guest.self() ? "You" : "";
			if (!tag.isEmpty()) {
				row.getChildren().add(caption(tag));
			}

			Label state = new Label(statusText(status));
			state.setStyle("-fx-font-size: 11px; -fx-text-fill: " + statusColor(status) + ";");
			row.getChildren().add(state);
			rows.getChildren().add(row);
		}

		Node content = rows;
		if (guests.size() > 5) {
			ScrollPane scroll = new ScrollPane(rows);
			scroll.setFitToWidth(true);
			scroll.setPrefHeight(150);
			content = scroll;
		}
		return new VBox(4, caption("Guests (" + guests.size() + ")"), content);
	}

	private static String effectiveStatus(AgendaItem.Guest guest) {
		String status = guest.status() == null ? "" : guest.status();
		if (guest.organizer() && (status.isEmpty() || status.equals("needsAction"))) {
			return "accepted";
		}
		return status;
	}

	private static String statusMark(String status) {
		return switch (status) {
		case "accepted" -> "\u2713";
		case "declined" -> "\u2717";
		case "tentative" -> "?";
		default -> "\u25CB";
		};
	}

	private static String statusText(String status) {
		return switch (status) {
		case "accepted" -> "Accepted";
		case "declined" -> "Declined";
		case "tentative" -> "Maybe";
		default -> "Waiting";
		};
	}

	private static String statusColor(String status) {
		return switch (status) {
		case "accepted" -> "#188038";
		case "declined" -> "#d93025";
		case "tentative" -> "#e37400";
		default -> "#5f6368";
		};
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