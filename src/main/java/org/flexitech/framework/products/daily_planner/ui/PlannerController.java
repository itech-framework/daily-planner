package org.flexitech.framework.products.daily_planner.ui;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.framework.products.daily_planner.ApiTestMain;
import org.flexitech.framework.products.daily_planner.app.AppEvents;
import org.flexitech.framework.products.daily_planner.model.AgendaItem;
import org.flexitech.framework.products.daily_planner.model.DriveItem;
import org.flexitech.framework.products.daily_planner.model.DriveStorage;
import org.flexitech.framework.products.daily_planner.model.EventDraft;
import org.flexitech.framework.products.daily_planner.model.Holiday;
import org.flexitech.framework.products.daily_planner.model.PlannerSnapshot;
import org.flexitech.framework.products.daily_planner.service.CalendarService;
import org.flexitech.framework.products.daily_planner.service.DriveService;
import org.flexitech.framework.products.daily_planner.service.PlannerService;
import org.flexitech.framework.products.daily_planner.service.TaskService;
import org.flexitech.framework.products.daily_planner.session.UserSession;
import org.flexitech.framework.products.daily_planner.ui.calendar.EventEditor;
import org.flexitech.framework.products.daily_planner.ui.component.AgendaCard;
import org.flexitech.framework.products.daily_planner.ui.component.AgendaSkeleton;
import org.flexitech.framework.products.daily_planner.ui.component.DriveRow;
import org.flexitech.framework.products.daily_planner.ui.component.DriveSkeleton;
import org.flexitech.framework.products.daily_planner.util.Notifier;
import org.flexitech.framework.products.daily_planner.util.OffDays;
import org.flexitech.framework.products.daily_planner.util.Pulse;
import org.flexitech.framework.products.daily_planner.util.Sizes;
import org.flexitech.framework.products.daily_planner.util.Uploads;

import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.core.annotations.properties.Property;
import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.events.EventBus;
import io.github.itech_framework.java_fx.router.Router;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_framework.java_fx.ui.dialog.LoadingDialog;
import io.github.itech_framework.java_fx.utils.concurrent.TaskScope;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

@FxController
public class PlannerController implements Routable {

	private static final Logger log = LogManager.getLogger(PlannerController.class);
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter LONG_DAY = DateTimeFormatter.ofPattern("EEE, d MMM");
	private static final String DASH = " \u2013 ";
	private static final String BANNER_RED = "banner-red";
	private static final String STORAGE_FULL = "storage-full";
	private static final String CALENDAR_ROUTE = "calendar";
	private static final String DRIVE_ROUTE = "drive";
	private static final String COUNT_PLACEHOLDER = "00";
	private static final String STORAGE_PLACEHOLDER = "000 MB of 000 GB used";
	private static final int RECENT_LIMIT = 6;
	private static final int HOLIDAY_DAYS = 60;
	private static final int EVENT_SKELETON_ROWS = 2;
	private static final int OVERDUE_SKELETON_ROWS = 1;
	private static final int TODAY_SKELETON_ROWS = 2;
	private static final int UPCOMING_SKELETON_ROWS = 2;
	private static final int DRIVE_SKELETON_ROWS = 4;
	private static final double STORAGE_WARN = 0.9;

	@Property(key = "planner.calendar.holiday-calendar", defaultValue = "")
	private String holidayCalendarId;

	@Property(key = "planner.calendar.off-days", defaultValue = "SUNDAY")
	private String offDaysValue;

	@Use
	private PlannerService plannerService;

	@Use
	private TaskService taskService;

	@Use
	private CalendarService calendarService;

	@Use
	private DriveService driveService;

	@Use
	private UserSession session;

	@Use
	private Router router;

	@FXML
	private Label greetingLabel;

	@FXML
	private Label dateLabel;

	@FXML
	private Label eventsCount;

	@FXML
	private Label overdueCount;

	@FXML
	private Label todayCount;

	@FXML
	private Label upcomingCount;

	@FXML
	private ProgressIndicator progress;

	@FXML
	private Button refreshButton;

	@FXML
	private VBox warningBox;

	@FXML
	private VBox banner;

	@FXML
	private Label bannerTitle;

	@FXML
	private Label bannerNext;

	@FXML
	private VBox nextUpCard;

	@FXML
	private Label nextUpTitle;

	@FXML
	private Label nextUpWhen;

	@FXML
	private Label nextUpCountdown;

	@FXML
	private Button nextUpButton;

	@FXML
	private VBox eventsBox;

	@FXML
	private VBox overdueBox;

	@FXML
	private VBox todayBox;

	@FXML
	private VBox upcomingBox;

	@FXML
	private Label storageLabel;

	@FXML
	private ProgressBar storageBar;

	@FXML
	private VBox recentBox;

	private record DriveSummary(List<DriveItem> recent, DriveStorage storage) {
	}

	private final TaskScope background = new TaskScope();
	private final Timeline ticker = new Timeline(new KeyFrame(Duration.seconds(30), event -> updateNextUp()));
	private volatile boolean disposed;
	private boolean plannerLoaded;
	private boolean driveLoaded;
	private LoadingDialog loading;
	private Set<java.time.DayOfWeek> offDays = OffDays.parse("SUNDAY");
	private List<AgendaItem> todayEvents = List.of();
	private AgendaItem nextEvent;

	@OnInit
	public void initialized() {
		String name = session.getUser() == null || session.getUser().getName() == null ? "there"
				: session.getUser().getName();
		greetingLabel.setText("Hello, " + name);
		dateLabel.setText(DATE.format(LocalDate.now()));
		offDays = OffDays.parse(offDaysValue);
		ticker.setCycleCount(Animation.INDEFINITE);
	}

	@Override
	public void onNavigate(Object arguments) {
		loadAll(false);
	}

	@Override
	public void onResume() {
		disposed = false;
		loadAll(false);
	}

	@Override
	public void dispose() {
		disposed = true;
		ticker.stop();
		closeLoading();
		background.cancelAll();
	}

	@FXML
	private void onRefresh() {
		loadAll(true);
	}

	@FXML
	private void onOpenCalendar() {
		router.off(CALENDAR_ROUTE, LocalDate.now());
	}

	@FXML
	private void onOpenDrive() {
		router.off(DRIVE_ROUTE);
	}

	@FXML
	private void onOpenNextUp() {
		if (nextEvent != null && nextEvent.link() != null && ApiTestMain.hostServices() != null) {
			ApiTestMain.hostServices().showDocument(nextEvent.link());
		}
	}

	@FXML
	private void onNewEvent() {
		LocalDateTime start = LocalDate.now().atTime(Math.min(22, LocalTime.now().getHour() + 1), 0);
		EventEditor.open(ApiTestMain.primaryStage(), null, start, false, draft -> saveEvent(draft));
	}

	@FXML
	private void onUpload() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Upload to Drive");
		List<File> files = chooser.showOpenMultipleDialog(ApiTestMain.primaryStage());
		if (files == null || files.isEmpty()) {
			return;
		}
		for (File file : files) {
			Uploads.start(driveService, file.toPath(), null, item -> {
				if (!disposed) {
					loadDrive();
				}
			});
		}
	}

	private void saveEvent(EventDraft draft) {
		background.run(() -> calendarService.createEvent(draft), saved -> {
			Notifier.success("Event created", saved.title());
			load(false);
		}, error -> {
			log.error("Could not save the event", error);
			Notifier.error("Could not save the event", error.getMessage());
		});
	}

	private void loadAll(boolean withDialog) {
		load(withDialog);
		loadDrive();
		loadBanner();
	}

	private void load(boolean withDialog) {
		progress.setVisible(true);
		refreshButton.setDisable(true);
		if (!plannerLoaded) {
			showPlannerSkeleton();
		}
		if (withDialog) {
			loading = LoadingDialog.showLoading(ApiTestMain.primaryStage(), "Loading your day...");
		}
		background.run(plannerService::loadToday, this::render, this::fail);
	}

	private void showPlannerSkeleton() {
		Pulse.mark(eventsCount, COUNT_PLACEHOLDER);
		Pulse.mark(overdueCount, COUNT_PLACEHOLDER);
		Pulse.mark(todayCount, COUNT_PLACEHOLDER);
		Pulse.mark(upcomingCount, COUNT_PLACEHOLDER);
		AgendaSkeleton.fill(eventsBox, EVENT_SKELETON_ROWS);
		AgendaSkeleton.fill(overdueBox, OVERDUE_SKELETON_ROWS);
		AgendaSkeleton.fill(todayBox, TODAY_SKELETON_ROWS);
		AgendaSkeleton.fill(upcomingBox, UPCOMING_SKELETON_ROWS);
		setVisible(nextUpCard, false);
	}

	private void clearPlannerSkeleton() {
		Pulse.clear(eventsCount);
		Pulse.clear(overdueCount);
		Pulse.clear(todayCount);
		Pulse.clear(upcomingCount);
	}

	private void render(PlannerSnapshot snapshot) {
		finishLoading();
		if (disposed) {
			return;
		}
		plannerLoaded = true;
		clearPlannerSkeleton();
		eventsCount.setText(String.valueOf(snapshot.events().size()));
		overdueCount.setText(String.valueOf(snapshot.overdueTasks().size()));
		todayCount.setText(String.valueOf(snapshot.todayTasks().size()));
		upcomingCount.setText(String.valueOf(snapshot.upcomingTasks().size()));

		AgendaCard.fill(eventsBox, snapshot.events(), null, "No events today.");
		AgendaCard.fill(overdueBox, snapshot.overdueTasks(), this::toggle, "Nothing overdue.");
		AgendaCard.fill(todayBox, snapshot.todayTasks(), this::toggle, "Nothing due today.");
		AgendaCard.fill(upcomingBox, snapshot.upcomingTasks(), this::toggle, "No upcoming tasks.");

		boolean hasWarnings = !snapshot.warnings().isEmpty();
		warningBox.setVisible(hasWarnings);
		warningBox.setManaged(hasWarnings);
		warningBox.getChildren().setAll(snapshot.warnings().stream().map(text -> {
			Label label = new Label(text);
			label.setWrapText(true);
			label.getStyleClass().add("warning-text");
			return (Node) label;
		}).toList());

		todayEvents = snapshot.events();
		updateNextUp();

		EventBus.publish(AppEvents.STATUS, "Planner updated at " + TIME.format(snapshot.loadedAt()));
	}

	private void updateNextUp() {
		LocalDateTime now = LocalDateTime.now();
		nextEvent = todayEvents.stream().filter(event -> !event.allDay() && event.start() != null)
				.filter(event -> (event.end() == null ? event.start() : event.end()).isAfter(now))
				.min(Comparator.comparing(AgendaItem::start)).orElse(null);
		if (nextEvent == null) {
			ticker.stop();
			setVisible(nextUpCard, false);
			return;
		}
		String end = nextEvent.end() == null ? "" : DASH + TIME.format(nextEvent.end());
		nextUpTitle.setText(nextEvent.title());
		nextUpWhen.setText(TIME.format(nextEvent.start()) + end);
		nextUpCountdown.setText(countdown(now, nextEvent.start()));

		String link = nextEvent.link();
		boolean hasLink = link != null && !link.isBlank();
		nextUpButton.setText(hasLink && link.contains("meet.google") ? "Join" : "Open");
		setVisible(nextUpButton, hasLink);
		setVisible(nextUpCard, true);
		if (ticker.getStatus() != Animation.Status.RUNNING) {
			ticker.play();
		}
	}

	private static String countdown(LocalDateTime now, LocalDateTime start) {
		if (!start.isAfter(now)) {
			return "Happening now";
		}
		long minutes = ChronoUnit.MINUTES.between(now, start);
		if (minutes < 1) {
			return "Starting now";
		}
		if (minutes < 60) {
			return "in " + minutes + " min";
		}
		return "in " + minutes / 60 + " h " + minutes % 60 + " min";
	}

	private void loadBanner() {
		LocalDate today = LocalDate.now();
		if (holidayCalendarId == null || holidayCalendarId.isBlank()) {
			renderBanner(List.of(), today);
			return;
		}
		background.run(() -> calendarService.getAllHolidays(holidayCalendarId, today, today.plusDays(HOLIDAY_DAYS)),
				holidays -> renderBanner(holidays, today), error -> {
					log.warn("Holidays could not be loaded for the dashboard: {}", error.getMessage());
					renderBanner(List.of(), today);
				});
	}

	private void renderBanner(List<Holiday> holidays, LocalDate today) {
		List<Holiday> named = holidays.stream().filter(Holiday::hasName).toList();
		List<String> publicToday = namesOn(named, today, true);
		List<String> observancesToday = namesOn(named, today, false);

		String title = null;
		boolean red = false;
		if (!publicToday.isEmpty()) {
			title = "Today is " + String.join(", ", publicToday);
			red = true;
		} else if (offDays.contains(today.getDayOfWeek())) {
			title = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault())
					+ " is a non-working day";
			red = true;
		} else if (!observancesToday.isEmpty()) {
			title = "Today: " + String.join(", ", observancesToday);
		}

		Holiday next = named.stream().filter(holiday -> holiday.publicHoliday() && holiday.date().isAfter(today))
				.min(Comparator.comparing(Holiday::date)).orElse(null);
		String nextText = next == null ? null
				: "Next holiday: " + next.name() + ", " + LONG_DAY.format(next.date()) + " ("
						+ inDays(today, next.date()) + ")";

		bannerTitle.setText(title == null ? "" : title);
		setVisible(bannerTitle, title != null);
		bannerNext.setText(nextText == null ? "" : nextText);
		setVisible(bannerNext, nextText != null);
		setVisible(banner, title != null || nextText != null);
		banner.getStyleClass().remove(BANNER_RED);
		if (red) {
			banner.getStyleClass().add(BANNER_RED);
		}
	}

	private static List<String> namesOn(List<Holiday> holidays, LocalDate day, boolean publicHoliday) {
		return holidays.stream().filter(holiday -> holiday.date().equals(day) && holiday.publicHoliday() == publicHoliday)
				.map(Holiday::name).distinct().toList();
	}

	private static String inDays(LocalDate from, LocalDate to) {
		long days = ChronoUnit.DAYS.between(from, to);
		return days == 1 ? "tomorrow" : "in " + days + " days";
	}

	private void loadDrive() {
		if (!driveLoaded) {
			showDriveSkeleton();
		}
		background.run(this::fetchDrive, this::renderDrive, error -> {
			log.warn("Drive summary could not be loaded: {}", error.getMessage());
			Pulse.clear(storageLabel);
			recentBox.getChildren().setAll(note("Drive could not be loaded."));
			setVisible(storageLabel, false);
			setVisible(storageBar, false);
		});
	}

	private void showDriveSkeleton() {
		setVisible(storageLabel, true);
		setVisible(storageBar, false);
		Pulse.mark(storageLabel, STORAGE_PLACEHOLDER);
		DriveSkeleton.fill(recentBox, DRIVE_SKELETON_ROWS);
	}

	private DriveSummary fetchDrive() throws Exception {
		List<DriveItem> recent = driveService.listRecent(RECENT_LIMIT);
		DriveStorage storage = null;
		try {
			storage = driveService.getStorage();
		} catch (Exception e) {
			log.warn("Drive storage could not be loaded: {}", e.getMessage());
		}
		return new DriveSummary(recent, storage);
	}

	private void renderDrive(DriveSummary summary) {
		driveLoaded = true;
		Pulse.clear(storageLabel);
		recentBox.getChildren().clear();
		if (summary.recent().isEmpty()) {
			recentBox.getChildren().add(note("No files yet."));
		}
		for (DriveItem item : summary.recent()) {
			recentBox.getChildren().add(DriveRow.create(item, this::openFile));
		}

		DriveStorage storage = summary.storage();
		setVisible(storageLabel, storage != null);
		setVisible(storageBar, storage != null && storage.limited());
		if (storage == null) {
			return;
		}
		storageLabel.setText(storage.limited()
				? Sizes.format(storage.used()) + " of " + Sizes.format(storage.limit()) + " used"
				: Sizes.format(storage.used()) + " used");
		storageBar.setProgress(storage.fraction());
		storageBar.getStyleClass().remove(STORAGE_FULL);
		if (storage.fraction() >= STORAGE_WARN) {
			storageBar.getStyleClass().add(STORAGE_FULL);
		}
	}

	private void openFile(DriveItem item) {
		if (item.link() == null || item.link().isBlank() || ApiTestMain.hostServices() == null) {
			Notifier.warning("Cannot open file", item.name());
			return;
		}
		ApiTestMain.hostServices().showDocument(item.link());
	}

	private Label note(String text) {
		Label label = new Label(text);
		label.getStyleClass().add("empty-note");
		return label;
	}

	private void fail(Exception error) {
		finishLoading();
		if (!plannerLoaded) {
			clearPlannerSkeleton();
			eventsCount.setText("0");
			overdueCount.setText("0");
			todayCount.setText("0");
			upcomingCount.setText("0");
			eventsBox.getChildren().setAll(note("Could not load events."));
			overdueBox.getChildren().setAll(note("Could not load tasks."));
			todayBox.getChildren().setAll(note("Could not load tasks."));
			upcomingBox.getChildren().setAll(note("Could not load tasks."));
		}
		Notifier.error("Could not load the planner", error.getMessage());
		EventBus.publish(AppEvents.STATUS, "Planner failed to load");
	}

	private void toggle(AgendaItem item) {
		background.run(() -> taskService.setDone(item, item.done()), updated -> {
			Notifier.success(item.done() ? "Task completed" : "Task reopened", item.title());
			load(false);
		}, error -> {
			Notifier.error("Could not update the task", error.getMessage());
			load(false);
		});
	}

	private void finishLoading() {
		progress.setVisible(false);
		refreshButton.setDisable(false);
		closeLoading();
	}

	private void closeLoading() {
		if (loading != null) {
			loading.close();
			loading = null;
		}
	}

	private void setVisible(Node node, boolean visible) {
		node.setVisible(visible);
		node.setManaged(visible);
	}
}