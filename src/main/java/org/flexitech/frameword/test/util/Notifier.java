package org.flexitech.frameword.test.util;

import org.flexitech.frameword.test.ApiTestMain;

import io.github.itech_framework.java_fx.ui.toast.Toast;
import io.github.itech_framework.java_fx.ui.toast.ToastType;
import javafx.application.Platform;
import javafx.stage.Stage;

public final class Notifier {

	private Notifier() {
	}

	public static void info(String title, String message) {
		show(ToastType.INFO, title, message);
	}

	public static void success(String title, String message) {
		show(ToastType.SUCCESS, title, message);
	}

	public static void warning(String title, String message) {
		show(ToastType.WARNING, title, message);
	}

	public static void error(String title, String message) {
		show(ToastType.ERROR, title, message);
	}

	private static void show(ToastType type, String title, String message) {
		Runnable action = () -> {
			Toast.ToastBuilder builder = Toast.builder().type(type).title(title).message(message == null ? "" : message);
			Stage stage = ApiTestMain.primaryStage();
			if (stage != null) {
				builder.owner(stage);
			}
			builder.show();
		};
		if (Platform.isFxApplicationThread()) {
			action.run();
		} else {
			Platform.runLater(action);
		}
	}
}