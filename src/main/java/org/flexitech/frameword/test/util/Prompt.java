package org.flexitech.frameword.test.util;

import java.util.Optional;

import org.flexitech.frameword.test.ApiTestMain;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;

public final class Prompt {

	private static final String DARK = "dark-mode";

	private Prompt() {
	}

	public static Optional<String> text(String title, String label, String initial) {
		TextInputDialog dialog = new TextInputDialog(initial);
		dialog.setTitle(title);
		dialog.setHeaderText(null);
		dialog.setContentText(label);
		style(dialog);
		Platform.runLater(() -> dialog.getEditor().selectAll());
		return dialog.showAndWait().map(String::trim).filter(value -> !value.isEmpty());
	}

	public static boolean confirm(String title, String message, String action) {
		ButtonType confirm = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
		Alert alert = new Alert(Alert.AlertType.NONE, message, confirm, ButtonType.CANCEL);
		alert.setTitle(title);
		alert.setHeaderText(null);
		style(alert);
		return alert.showAndWait().filter(type -> type == confirm).isPresent();
	}

	private static void style(Dialog<?> dialog) {
		Stage owner = ApiTestMain.primaryStage();
		dialog.initOwner(owner);
		DialogPane pane = dialog.getDialogPane();
		pane.getStylesheets().setAll(owner.getScene().getStylesheets());
		if (owner.getScene().getRoot().getStyleClass().contains(DARK)) {
			pane.getStyleClass().add(DARK);
		}
	}
}