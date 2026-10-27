package org.flexitech.framework.products.daily_planner.ui;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.framework.products.daily_planner.ApiTestMain;
import org.flexitech.framework.products.daily_planner.app.AppEvents;
import org.flexitech.framework.products.daily_planner.model.AgendaItem;
import org.flexitech.framework.products.daily_planner.model.EventDraft;
import org.flexitech.framework.products.daily_planner.model.Holiday;
import org.flexitech.framework.products.daily_planner.model.Setting;
import org.flexitech.framework.products.daily_planner.service.CalendarService;
import org.flexitech.framework.products.daily_planner.service.SettingsService;
import org.flexitech.framework.products.daily_planner.service.TaskService;
import org.flexitech.framework.products.daily_planner.service.notification.CalendarReminderSource;
import org.flexitech.framework.products.daily_planner.ui.calendar.CalendarMode;
import org.flexitech.framework.products.daily_planner.ui.calendar.EventEditor;
import org.flexitech.framework.products.daily_planner.ui.calendar.MonthGrid;
import org.flexitech.framework.products.daily_planner.ui.calendar.TimeGrid;
import org.flexitech.framework.products.daily_planner.util.Notifier;
import org.flexitech.framework.products.daily_planner.util.OffDays;

import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.core.annotations.properties.Property;
import io.github.itech_framework.core.utils.DataStorageUtil;
import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.events.EventBus;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_framework.java_fx.ui.dialog.ModalDialog;
import io.github.itech_framework.java_fx.utils.concurrent.TaskScope;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

@FxController
public class CalendarController implements Routable {

	private static final Logger log = LogManager.getLogger(CalendarController.class);
	private static final String MODE_KEY = "calendarMode";
	private static final String DASH = " \u2013 ";
	private static final DateTimeFormatter MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy");
	private static final DateTimeFormatter DAY_TITLE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");
	private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM");
	private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy");
	private static final DateTimeFormatter LONG_DAY = DateTimeFormatter.ofPattern("EEE, d MMM yyyy");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	@Property(key = "planner.calendar.first-day", defaultValue = "MONDAY")
	private String firstDayName;

	@Property(key = "planner.calendar.default-view", defaultValue = "WEEK")
	private String defaultViewName;

	@Property(key = "planner.calendar.hour-height", defaultValue = "48")
	private int hourHeight;

	@Property(key = "planner.calendar.focus-hour", defaultValue = "7")
	private int focusHour;

	@Use
	private SettingsService settings;

	@Use
	private CalendarService calendarService;

	@Use
	private TaskService taskService;

	@Use
	private CalendarReminderSource reminders;

	@FXML
	private Label titleLabel;

	@FXML
	private ProgressIndicator progress;

	@FXML
	private Button refreshButton;

	@FXML
	private Label warningLabel;

	@FXML
	private StackPane contentHost;

	@FXML
	private ToggleGroup modeGroup;

	@FXML
	private ToggleButton dayToggle;

	@FXML
	private ToggleButton weekToggle;

	@FXML
	private ToggleButton monthToggle;

	private final TaskScope background = new TaskScope();
	private final Set<AgendaItem> pending = new HashSet<AgendaItem>();

	private CalendarMode mode = CalendarMode.WEEK;
	private LocalDate anchor = LocalDate.now();
	private DayOfWeek firstDay = DayOfWeek.MONDAY;
	private List<AgendaItem> tasksCache;
	private int loadSequence;
	private boolean saving;
	private volatile boolean disposed;

	private record CalendarData(List<AgendaItem> events, List<AgendaItem> tasks, List<Holiday> holidays,
			String warning) {
	}

	@OnInit
	public void initialized() {
		firstDay = parseEnum(DayOfWeek.class, firstDayName, DayOfWeek.MONDAY);
		mode = parseEnum(CalendarMode.class, storedMode(),
				parseEnum(CalendarMode.class, defaultViewName, CalendarMode.WEEK));
		selectToggle();
		modeGroup.selectedToggleProperty().addListener((observable, previous, current) -> {
			if (current == null && previous != null) {
				previous.setSelected(true);
			}
		});
	}

	@Override
	public void onNavigate(Object arguments) {
		if (arguments instanceof LocalDate date) {
			anchor = date;
		}
		load(true);
	}

	@Override
	public void onResume() {
		disposed = false;
	}

	@Override
	public void dispose() {
		disposed = true;
		background.cancelAll();
		pending.clear();
		saving = false;
	}

	@FXML
	private void onToday() {
		anchor = LocalDate.now();
		load(false);
	}

	@FXML
	private void onPrevious() {
		shift(-1);
	}

	@FXML
	private void onNext() {
		shift(1);
	}

	@FXML
	private void onRefresh() {
		load(true);
	}

	@FXML
	private void onDayMode() {
		switchMode(CalendarMode.DAY);
	}

	@FXML
	private void onWeekMode() {
		switchMode(CalendarMode.WEEK);
	}

	@FXML
	private void onMonthMode() {
		switchMode(CalendarMode.MONTH);
	}

	@FXML
	private void onNewEvent() {
		openEditor(null, defaultStart(anchor), false);
	}

	private void createAt(LocalDateTime start) {
		openEditor(null, start, false);
	}

	private void createAllDay(LocalDate day) {
		openEditor(null, day.atStartOfDay(), true);
	}

	private LocalDateTime defaultStart(LocalDate day) {
		if (day.equals(LocalDate.now())) {
			return day.atTime(Math.min(22, LocalTime.now().getHour() + 1), 0);
		}
		return day.atTime(9, 0);
	}

	private void openEditor(AgendaItem existing, LocalDateTime start, boolean allDay) {
		if (saving) {
			return;
		}
		EventEditor.open(ApiTestMain.primaryStage(), existing, start, allDay, draft -> saveEvent(existing, draft));
	}

	private void saveEvent(AgendaItem existing, EventDraft draft) {
		if (saving) {
			return;
		}
		saving = true;
		background.run(() -> existing == null ? calendarService.createEvent(draft)
				: calendarService.updateEvent(existing, draft), saved -> {
					saving = false;
					reminders.invalidate();
					Notifier.success(existing == null ? "Event created" : "Event updated", saved.title());
					load(false);
				}, error -> {
					saving = false;
					log.error("Could not save the event", error);
					Notifier.error("Could not save the event", error.getMessage());
				});
	}

	private void removeEvent(AgendaItem item) {
		if (!pending.add(item)) {
			return;
		}
		background.run(() -> {
			calendarService.deleteEvent(item);
			return Boolean.TRUE;
		}, done -> {
			pending.remove(item);
			reminders.invalidate();
			Notifier.success("Event deleted", item.title());
			load(false);
		}, error -> {
			pending.remove(item);
			log.error("Could not delete the event", error);
			Notifier.error("Could not delete the event", error.getMessage());
		});
	}

	private Runnable guarded(boolean[] gate, Runnable action) {
		return () -> {
			if (gate[0]) {
				return;
			}
			gate[0] = true;
			action.run();
		};
	}

	private void confirmDelete(AgendaItem item) {
		Label heading = new Label("Delete this event?");
		heading.getStyleClass().add("alert-title");
		String message = item.recurringEventId() == null ? item.title()
				: item.title() + "\nOnly this occurrence will be deleted.";
		Label text = new Label(message);
		text.setWrapText(true);
		VBox body = new VBox(6, heading, text);
		body.setPrefWidth(340);

		boolean[] gate = { false };
		ModalDialog[] holder = new ModalDialog[1];
		holder[0] = ModalDialog.builder().addOwner(ApiTestMain.primaryStage()).addContent(body)
				.addCloseButton("Cancel").addActionButton("Delete", guarded(gate, () -> {
					holder[0].close();
					removeEvent(item);
				})).build();
		holder[0].show();
	}

	private void afterDialogClosed(Runnable action) {
		PauseTransition pause = new PauseTransition(Duration.millis(350));
		pause.setOnFinished(event -> action.run());
		pause.play();
	}

	private void switchMode(CalendarMode newMode) {
		if (newMode == mode) {
			return;
		}
		mode = newMode;
		DataStorageUtil.save(MODE_KEY, mode.name());
		load(false);
	}

	private void openDay(LocalDate day) {
		anchor = day;
		mode = CalendarMode.DAY;
		DataStorageUtil.save(MODE_KEY, mode.name());
		selectToggle();
		load(false);
	}

	private void shift(int direction) {
		anchor = switch (mode) {
		case DAY -> anchor.plusDays(direction);
		case WEEK -> anchor.plusWeeks(direction);
		case MONTH -> anchor.plusMonths(direction);
		};
		load(false);
	}

	private LocalDate rangeStart() {
		return switch (mode) {
		case DAY -> anchor;
		case WEEK -> anchor.with(TemporalAdjusters.previousOrSame(firstDay));
		case MONTH -> anchor.withDayOfMonth(1).with(TemporalAdjusters.previousOrSame(firstDay));
		};
	}

	private LocalDate rangeEnd() {
		return switch (mode) {
		case DAY -> anchor;
		case WEEK -> rangeStart().plusDays(6);
		case MONTH -> anchor.withDayOfMonth(anchor.lengthOfMonth())
				.with(TemporalAdjusters.nextOrSame(firstDay.minus(1)));
		};
	}

	private String title() {
		return switch (mode) {
		case DAY -> DAY_TITLE.format(anchor);
		case MONTH -> MONTH_TITLE.format(anchor);
		case WEEK -> weekTitle(rangeStart(), rangeEnd());
		};
	}

	private String weekTitle(LocalDate start, LocalDate end) {
		if (start.getYear() != end.getYear()) {
			return DAY_MONTH_YEAR.format(start) + DASH + DAY_MONTH_YEAR.format(end);
		}
		if (start.getMonth() != end.getMonth()) {
			return DAY_MONTH.format(start) + DASH + DAY_MONTH_YEAR.format(end);
		}
		return start.getDayOfMonth() + DASH + DAY_MONTH_YEAR.format(end);
	}

	private void load(boolean reloadTasks) {
		LocalDate from = rangeStart();
		LocalDate to = rangeEnd();
		boolean fetchTasks = reloadTasks || tasksCache == null;
		int sequence = ++loadSequence;

		titleLabel.setText(title());
		progress.setVisible(true);
		refreshButton.setDisable(true);
		log.debug("Loading calendar {} from {} to {}, tasks: {}", mode, from, to, fetchTasks);

		background.run(() -> fetch(from, to, fetchTasks), data -> {
			if (disposed || sequence != loadSequence) {
				return;
			}
			finishLoading();
			render(data, from, to);
		}, error -> {
			if (disposed || sequence != loadSequence) {
				return;
			}
			finishLoading();
			log.error("Calendar failed to load", error);
			Notifier.error("Could not load the calendar", error.getMessage());
			EventBus.publish(AppEvents.STATUS, "Calendar failed to load");
		});
	}

	private CalendarData fetch(LocalDate from, LocalDate to, boolean fetchTasks) throws Exception {
		List<AgendaItem> events = calendarService.getEvents(from, to);
		List<Holiday> holidays = loadHolidays(from, to);
		if (!fetchTasks) {
			return new CalendarData(events, null, holidays, null);
		}
		try {
			return new CalendarData(events, taskService.getTasks(true), holidays, null);
		} catch (Exception e) {
			log.warn("Tasks could not be loaded for the calendar: {}", e.getMessage());
			return new CalendarData(events, null, holidays, "Tasks: " + e.getMessage());
		}
	}

	private List<Holiday> loadHolidays(LocalDate from, LocalDate to) {
		String calendars = settings.getString(Setting.HOLIDAY_CALENDAR);
		if (calendars == null || calendars.isBlank()) {
			return List.of();
		}
		Map<String, Holiday> unique = new LinkedHashMap<String, Holiday>();
		for (String id : calendars.split(",")) {
			String calendarId = id.trim();
			if (calendarId.isEmpty()) {
				continue;
			}
			try {
				for (Holiday holiday : calendarService.getHolidays(calendarId, from, to)) {
					unique.merge(holiday.date() + "|" + holiday.name(), holiday,
							(first, second) -> first.publicHoliday() ? first : second);
				}
			} catch (Exception e) {
				log.warn("Holidays could not be loaded for {}: {}", calendarId, e.getMessage());
			}
		}
		return new ArrayList<Holiday>(unique.values());
	}

	private void render(CalendarData data, LocalDate from, LocalDate to) {
		if (data.tasks() != null) {
			tasksCache = data.tasks();
		}
		var offDays = OffDays.parse(settings.getString(Setting.OFF_DAYS));
		List<AgendaItem> tasks = tasksCache == null ? new ArrayList<AgendaItem>() : tasksCache;
		Map<LocalDate, List<Holiday>> holidays = new HashMap<LocalDate, List<Holiday>>();
		for (Holiday holiday : data.holidays()) {
			holidays.computeIfAbsent(holiday.date(), day -> new ArrayList<Holiday>()).add(holiday);
		}
		for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
			if (offDays.contains(day.getDayOfWeek())) {
				holidays.computeIfAbsent(day, key -> new ArrayList<Holiday>()).add(Holiday.nonWorking(day));
			}
		}

		contentHost.getChildren().setAll(buildView(data.events(), tasks, holidays, from));

		boolean hasWarning = data.warning() != null;
		warningLabel.setText(hasWarning ? data.warning() : "");
		warningLabel.setVisible(hasWarning);
		warningLabel.setManaged(hasWarning);

		long shownTasks = tasks.stream()
				.filter(task -> task.due() != null && !task.due().isBefore(from) && !task.due().isAfter(to)).count();
		EventBus.publish(AppEvents.STATUS, data.events().size() + " events, " + shownTasks + " tasks in view");
	}

	private Node buildView(List<AgendaItem> events, List<AgendaItem> tasks, Map<LocalDate, List<Holiday>> holidays,
			LocalDate from) {
		return switch (mode) {
		case MONTH -> MonthGrid.create(anchor, firstDay, events, tasks, holidays, this::openDetails, this::openDay,
				this::createAllDay);
		case WEEK -> new TimeGrid(daysFrom(from, 7), events, tasks, holidays, hourHeight, startHour(from, 7),
				this::openDetails, this::openDay, this::createAt);
		case DAY -> new TimeGrid(daysFrom(anchor, 1), events, tasks, holidays, hourHeight, startHour(anchor, 1),
				this::openDetails, this::openDay, this::createAt);
		};
	}

	private List<LocalDate> daysFrom(LocalDate start, int count) {
		return Stream.iterate(start, day -> day.plusDays(1)).limit(count).collect(Collectors.toList());
	}

	private int startHour(LocalDate start, int count) {
		LocalDate today = LocalDate.now();
		boolean containsToday = !today.isBefore(start) && today.isBefore(start.plusDays(count));
		return containsToday ? Math.max(0, LocalTime.now().getHour() - 1) : focusHour;
	}

	private void openDetails(AgendaItem item) {
		boolean task = item.type() == AgendaItem.Type.TASK;

		Label heading = new Label(item.title());
		heading.getStyleClass().add("alert-title");
		heading.setWrapText(true);

		VBox body = new VBox(6, heading, metaLabel(describeWhen(item, task)));
		body.setPrefWidth(380);
		if (task && item.listTitle() != null) {
			body.getChildren().add(metaLabel("List: " + item.listTitle()));
		}
		if (!task && item.detail() != null && !item.detail().isBlank()) {
			body.getChildren().add(metaLabel("Location: " + item.detail()));
		}
		if (task && item.detail() != null && !item.detail().isBlank()) {
			Label notes = new Label(item.detail());
			notes.setWrapText(true);
			body.getChildren().add(notes);
		}
		if (!task && item.description() != null && !item.description().isBlank()) {
			Label notes = new Label(item.description());
			notes.setWrapText(true);
			body.getChildren().add(notes);
		}

		boolean[] gate = { false };
		ModalDialog[] holder = new ModalDialog[1];
		ModalDialog.ModalDialogBuilder builder = ModalDialog.builder().addOwner(ApiTestMain.primaryStage())
				.addContent(body).addCloseButton("Close");
		if (item.link() != null && !item.link().isBlank() && ApiTestMain.hostServices() != null) {
			boolean meet = item.link().contains("meet.google.com");
			builder.addActionButton(meet ? "Join Google Meet" : "Open in Google",
					() -> ApiTestMain.hostServices().showDocument(item.link()));
		}
		if (task) {
			builder.addActionButton(item.done() ? "Mark as not done" : "Mark as done", guarded(gate, () -> {
				holder[0].close();
				toggleTask(item);
			}));
		} else {
			builder.addActionButton("Delete", guarded(gate, () -> {
				holder[0].close();
				afterDialogClosed(() -> confirmDelete(item));
			}));
			builder.addActionButton("Edit", guarded(gate, () -> {
				holder[0].close();
				afterDialogClosed(() -> openEditor(item, null, false));
			}));
		}
		holder[0] = builder.build();
		holder[0].show();
	}

	private Label metaLabel(String text) {
		Label label = new Label(text);
		label.getStyleClass().add("muted");
		label.setWrapText(true);
		return label;
	}

	private String describeWhen(AgendaItem item, boolean task) {
		if (task) {
			return item.due() == null ? "No due date" : "Due " + LONG_DAY.format(item.due());
		}
		if (item.start() == null) {
			return "";
		}
		if (item.allDay()) {
			LocalDate first = item.start().toLocalDate();
			LocalDate last = item.end() == null ? first : item.end().toLocalDate().minusDays(1);
			return last.isAfter(first) ? "All day, " + DAY_MONTH.format(first) + DASH + LONG_DAY.format(last)
					: "All day, " + LONG_DAY.format(first);
		}
		String end = item.end() == null ? "" : DASH + TIME.format(item.end());
		return LONG_DAY.format(item.start()) + ", " + TIME.format(item.start()) + end;
	}

	private void toggleTask(AgendaItem item) {
		if (!pending.add(item)) {
			return;
		}
		background.run(() -> taskService.setDone(item, !item.done()), updated -> {
			pending.remove(item);
			Notifier.success(item.done() ? "Task reopened" : "Task completed", item.title());
			load(true);
		}, error -> {
			pending.remove(item);
			log.error("Could not update the task", error);
			Notifier.error("Could not update the task", error.getMessage());
		});
	}

	private void finishLoading() {
		progress.setVisible(false);
		refreshButton.setDisable(false);
	}

	private void selectToggle() {
		Toggle target = switch (mode) {
		case DAY -> dayToggle;
		case WEEK -> weekToggle;
		case MONTH -> monthToggle;
		};
		modeGroup.selectToggle(target);
	}

	private String storedMode() {
		try {
			Object stored = DataStorageUtil.load(MODE_KEY);
			return stored == null ? "" : String.valueOf(stored).replace("\"", "");
		} catch (Exception e) {
			return "";
		}
	}

	private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
		try {
			return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
		} catch (Exception e) {
			return fallback;
		}
	}
}