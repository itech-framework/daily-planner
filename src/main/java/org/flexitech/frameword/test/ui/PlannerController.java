package org.flexitech.frameword.test.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.flexitech.frameword.test.ApiTestMain;
import org.flexitech.frameword.test.app.AppEvents;
import org.flexitech.frameword.test.model.AgendaItem;
import org.flexitech.frameword.test.model.PlannerSnapshot;
import org.flexitech.frameword.test.service.PlannerService;
import org.flexitech.frameword.test.service.TaskService;
import org.flexitech.frameword.test.session.UserSession;
import org.flexitech.frameword.test.ui.component.AgendaCard;
import org.flexitech.frameword.test.util.Notifier;

import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.events.EventBus;
import io.github.itech_framework.java_fx.router.Router;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_framework.java_fx.ui.dialog.LoadingDialog;
import io.github.itech_framework.java_fx.utils.concurrent.BackgroundTaskService;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;

@FxController
public class PlannerController implements Routable {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	@Use
	private PlannerService plannerService;

	@Use
	private TaskService taskService;

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
	private VBox eventsBox;

	@FXML
	private VBox overdueBox;

	@FXML
	private VBox todayBox;

	@FXML
	private VBox upcomingBox;

	private final BackgroundTaskService background = BackgroundTaskService.getInstance();
	private volatile boolean disposed;
	private LoadingDialog loading;

	@OnInit
	public void initialized() {
		String name = session.getUser() == null || session.getUser().getName() == null ? "there"
				: session.getUser().getName();
		greetingLabel.setText("Hello, " + name);
		dateLabel.setText(DATE.format(LocalDate.now()));
	}

	@Override
	public void onNavigate(Object arguments) {
		load(false);
	}

	@Override
	public void onResume() {
		disposed = false;
		load(false);
	}

	@Override
	public void dispose() {
		disposed = true;
		closeLoading();
	}

	@FXML
	private void onRefresh() {
		load(true);
	}

	@FXML
	private void onOpenCalendar() {
		router.off("calendar", LocalDate.now());
	}

	private void load(boolean withDialog) {
		progress.setVisible(true);
		refreshButton.setDisable(true);
		if (withDialog) {
			loading = LoadingDialog.showLoading(ApiTestMain.primaryStage(), "Loading your day...");
		}
		background.executeTask(plannerService::loadToday, this::render, this::fail);
	}

	private void render(PlannerSnapshot snapshot) {
		finishLoading();
		if (disposed) {
			return;
		}
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

		EventBus.publish(AppEvents.STATUS, "Planner updated at " + TIME.format(snapshot.loadedAt()));
	}

	private void fail(Exception error) {
		finishLoading();
		Notifier.error("Could not load the planner", error.getMessage());
		EventBus.publish(AppEvents.STATUS, "Planner failed to load");
	}

	private void toggle(AgendaItem item) {
		background.executeTask(() -> taskService.setDone(item, item.done()), updated -> {
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
}