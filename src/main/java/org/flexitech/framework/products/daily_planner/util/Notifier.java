package org.flexitech.framework.products.daily_planner.util;

import io.github.itech_framework.java_fx.ui.toast.Toast;
import io.github.itech_framework.java_fx.ui.toast.ToastType;
import javafx.application.Platform;

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
		Runnable action = () -> Toast.builder().type(type).title(title).message(message == null ? "" : message).show();
		if (Platform.isFxApplicationThread()) {
			action.run();
		} else {
			Platform.runLater(action);
		}
	}
}