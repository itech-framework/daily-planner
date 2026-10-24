package org.flexitech.frameword.test.ui;

import org.flexitech.frameword.test.ApiTestMain;
import org.flexitech.frameword.test.api.response.GoogleUserInfo;
import org.flexitech.frameword.test.service.GoogleAuthService;
import org.flexitech.frameword.test.session.UserSession;
import org.flexitech.frameword.test.util.Notifier;

import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.core.annotations.methods.PreDestroy;
import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.router.Router;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_framework.java_fx.ui.toast.Toast;
import io.github.itech_framework.java_fx.ui.toast.ToastType;
import io.github.itech_framework.java_fx.utils.concurrent.BackgroundTaskService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

@FxController
public class LoginController  implements Routable {

	@Use
	private GoogleAuthService authService;
	
	@Use
	private Router router;

	@Use
	private UserSession session;

	@FXML
	private Label statusLabel;

	@FXML
	private ProgressIndicator progress;

	@FXML
	private VBox browserPane;

	@FXML
	private Hyperlink authLink;

	@FXML
	private VBox userPane;

	@FXML
	private Label nameLabel;

	@FXML
	private Label emailLabel;

	@FXML
	private Label subLabel;

	@FXML
	private Label verifiedLabel;

	@FXML
	private Button loginButton;

	@FXML
	private Button cancelButton;

	@FXML
	private Button logoutButton;

	private final BackgroundTaskService background = BackgroundTaskService.getInstance();

	private String authUrl;

	@OnInit
	public void initialized() {
		showSignedOut("Not signed in.");

		if (!authService.isConfigured()) {
			loginButton.setDisable(true);
			showStatus("Set google.oauth.client-id and google.oauth.client-secret in application.properties.");
			return;
		}

		if (authService.hasSavedSession()) {
			showBusy("Restoring the saved session...");
			background.executeTask(() -> {
				if (!authService.restoreSession()) {
					return null;
				}
				return authService.getUserInfo();
			}, info -> {
				if (info == null) {
					showSignedOut("Saved session expired. Please sign in again.");
				} else {
					showSignedIn(info);
				}
			}, error -> showSignedOut("Could not restore the session: " + error.getMessage()));
		}
	}
	
	@Override
	public void onNavigate(Object arguments) {
		if (arguments instanceof String message) {
			showStatus(message);
		}
	}

	@FXML
	private void onLogin() {
		showBusy("Preparing the Google sign-in...");
		background.executeTask(() -> authService.beginLogin(), this::onAuthUrl, this::onLoginFailed);
	}

	@FXML
	private void onCancel() {
		authService.cancelLogin();
		cancelButton.setDisable(true);
		showStatus("Cancelling...");
	}

	@FXML
	private void onOpenLink() {
		if (authUrl == null) {
			return;
		}
		if (ApiTestMain.hostServices() == null) {
			toast(ToastType.ERROR, "Cannot open browser", "Use the Copy link button and open it manually.");
			return;
		}
		ApiTestMain.hostServices().showDocument(authUrl);
	}

	@FXML
	private void onCopyLink() {
		if (authUrl == null) {
			return;
		}
		ClipboardContent content = new ClipboardContent();
		content.putString(authUrl);
		Clipboard.getSystemClipboard().setContent(content);
		toast(ToastType.INFO, "Copied", "Sign-in link copied to the clipboard.");
	}

	@FXML
	private void onLogout() {
		showBusy("Signing out...");
		background.executeTask(() -> {
			authService.logout(true);
			return Boolean.TRUE;
		}, done -> {
			showSignedOut("Signed out.");
			toast(ToastType.SUCCESS, "Signed out", "Google token revoked.");
		}, error -> {
			authService.logout(false);
			showSignedOut("Signed out locally. Revoke failed: " + error.getMessage());
		});
	}

	@PreDestroy
	public void cleanUp() {
		authService.cancelLogin();
	}

	private void onAuthUrl(String url) {
		authUrl = url;
		browserPane.setVisible(true);
		browserPane.setManaged(true);
		cancelButton.setDisable(false);
		cancelButton.setVisible(true);
		cancelButton.setManaged(true);
		showStatus("Finish signing in in your browser. Waiting for approval...");
		onOpenLink();

		background.executeTask(() -> {
			authService.awaitLogin();
			return authService.getUserInfo();
		}, this::showSignedIn, this::onLoginFailed);
	}

	private void onLoginFailed(Exception error) {
		if (GoogleAuthService.isCancelled(error)) {
			showSignedOut("Login cancelled.");
			return;
		}
		showSignedOut("Login failed: " + error.getMessage());
		toast(ToastType.ERROR, "Login failed", error.getMessage());
	}

	private void showSignedIn(GoogleUserInfo info) {
		session.setUser(info);
		Notifier.success("Welcome", "Logged in as " + info.getEmail());
		router.offAll("planner");
	}

	private void showSignedOut(String message) {
		hideBrowserPane();
		progress.setVisible(false);
		userPane.setVisible(false);
		userPane.setManaged(false);
		loginButton.setVisible(true);
		loginButton.setManaged(true);
		loginButton.setDisable(!authService.isConfigured());
		logoutButton.setVisible(false);
		logoutButton.setManaged(false);
		showStatus(message);
	}

	private void showBusy(String message) {
		progress.setVisible(true);
		loginButton.setDisable(true);
		showStatus(message);
	}

	private void hideBrowserPane() {
		browserPane.setVisible(false);
		browserPane.setManaged(false);
		cancelButton.setVisible(false);
		cancelButton.setManaged(false);
	}

	private void showStatus(String message) {
		statusLabel.setText(message);
	}

	private void toast(ToastType type, String title, String message) {
		Window owner = statusLabel.getScene() != null ? statusLabel.getScene().getWindow() : null;
		Toast.ToastBuilder builder = Toast.builder().type(type).title(title).message(message);
		if (owner != null) {
			builder.owner(owner);
		}
		builder.show();
	}
}