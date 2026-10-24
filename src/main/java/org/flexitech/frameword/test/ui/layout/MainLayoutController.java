package org.flexitech.frameword.test.ui.layout;

import java.util.Map;

import org.flexitech.frameword.test.ApiTestMain;
import org.flexitech.frameword.test.app.AppEvents;
import org.flexitech.frameword.test.api.response.GoogleUserInfo;
import org.flexitech.frameword.test.service.GoogleAuthService;
import org.flexitech.frameword.test.session.UserSession;
import org.flexitech.frameword.test.util.Notifier;

import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.utils.DataStorageUtil;
import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.events.EventBus;
import io.github.itech_framework.java_fx.router.Router;
import io.github.itech_framework.java_fx.router.config.RouterConfig;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_framework.java_fx.router.core.Route;
import io.github.itech_framework.java_fx.ui.dialog.AlertDialog;
import io.github.itech_framework.java_fx.ui.layout.LayoutController;
import io.github.itech_framework.java_fx.utils.concurrent.BackgroundTaskService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

@FxController
public class MainLayoutController extends LayoutController implements Routable {

	private static final double SIDEBAR_WIDTH_RATIO = 0.18;
	private static final double SIDEBAR_MIN_WIDTH = 200;
	private static final double SIDEBAR_MAX_WIDTH = 320;

	@Use
	private Router router;

	@Use
	private GoogleAuthService authService;

	@Use
	private UserSession session;

	@FXML
	private BorderPane rootPane;

	@FXML
	private VBox sidebar;

	@FXML
	private StackPane contentContainer;

	@FXML
	private Label userNameLabel;

	@FXML
	private Label userEmailLabel;

	@FXML
	private Label statusLabel;

	@FXML
	private Button plannerButton;

	@FXML
	private Button tasksButton;

	@FXML
	private Button calendarButton;

	@FXML
	private Button themeButton;

	@FXML
	private Label avatarLabel;

	private String statusListenerId;
	
	@FXML
	private Button driveButton;

	@Override
	public void setContent(Parent content) {
		contentContainer.getChildren().setAll(content);
	}

	@Override
	public Parent getContentContainer() {
		return contentContainer;
	}

	@Override
	public void onLayoutLoaded() {
		bindSidebarWidth();
		GoogleUserInfo user = session.getUser();
		userNameLabel.setText(user == null ? "" : String.valueOf(user.getName()));
		userEmailLabel.setText(user == null ? "" : String.valueOf(user.getEmail()));
		String displayName = user == null || user.getName() == null ? "" : user.getName().trim();
		avatarLabel.setText(displayName.isEmpty() ? "?" : displayName.substring(0, 1).toUpperCase());
		updateThemeButton();
		statusListenerId = EventBus.subscribe(AppEvents.STATUS, message -> statusLabel.setText(String.valueOf(message)));
		Platform.runLater(this::highlight);
	}

	@Override
	public void dispose() {
		if (statusListenerId != null) {
			EventBus.unsubscribe(AppEvents.STATUS, statusListenerId);
			statusListenerId = null;
		}
		sidebar.prefWidthProperty().unbind();
	}

	private void bindSidebarWidth() {
		sidebar.setMinWidth(SIDEBAR_MIN_WIDTH);
		sidebar.setMaxWidth(SIDEBAR_MAX_WIDTH);
		sidebar.prefWidthProperty().bind(rootPane.widthProperty().multiply(SIDEBAR_WIDTH_RATIO));
	}

	@FXML
	private void onPlanner() {
		go("planner");
	}

	@FXML
	private void onTasks() {
		go("tasks");
	}

	@FXML
	private void onCalendar() {
		go("calendar");
	}

	@FXML
	private void onDrive() {
		go("drive");
	}
	
	@FXML
	private void onToggleTheme() {
		DataStorageUtil.save(RouterConfig.getDarkModeKey(), !isDark());
		router.refreshTheme();
		updateThemeButton();
	}

	@FXML
	private void onSignOut() {
		AlertDialog.builder().level(AlertDialog.Level.WARNING).title("Sign out")
				.message("Sign out and revoke the Google access for this app?").addOwner(ApiTestMain.primaryStage())
				.addButton("Cancel", null).addButton("Sign out", this::signOut).build().show();
	}

	private void signOut() {
		BackgroundTaskService.getInstance().executeTask(() -> {
			authService.logout(true);
			return Boolean.TRUE;
		}, done -> finishSignOut(), error -> {
			authService.logout(false);
			Notifier.warning("Signed out locally", "Revoke failed: " + error.getMessage());
			finishSignOut();
		});
	}

	private void finishSignOut() {
		session.clear();
		router.offAll("login", "Signed out.");
	}

	private void go(String routeName) {
		Route current = router.getCurrentRoute();
		if (current != null && current.name().equals(routeName)) {
			return;
		}
		router.off(routeName);
	}

	private void highlight() {
		Route current = router.getCurrentRoute();
		String name = current == null ? "" : current.name();
		Map<String, Button> buttons = Map.of("planner", plannerButton, "tasks", tasksButton, "calendar", calendarButton,
				"drive", driveButton);
		buttons.forEach((route, button) -> {
			button.getStyleClass().remove("nav-active");
			if (route.equals(name)) {
				button.getStyleClass().add("nav-active");
			}
		});
	}

	private boolean isDark() {
		Object stored = DataStorageUtil.load(RouterConfig.getDarkModeKey());
		return Boolean.parseBoolean(String.valueOf(stored).replace("\"", ""));
	}

	private void updateThemeButton() {
		themeButton.setText(isDark() ? "Light mode" : "Dark mode");
	}
}