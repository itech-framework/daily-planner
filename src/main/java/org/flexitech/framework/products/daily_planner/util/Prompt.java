package org.flexitech.framework.products.daily_planner.util;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.flexitech.framework.products.daily_planner.DailyPlannerAppMain;

import io.github.itech_framework.java_fx.ui.dialog.AlertDialog;
import io.github.itech_framework.java_fx.ui.dialog.ModalDialog;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public final class Prompt {

	private static final double FIELD_WIDTH = 340;

	private Prompt() {
	}

	public static Optional<String> text(String title, String label, String initial) {
		Stage owner = DailyPlannerAppMain.primaryStage();

		Label titleLabel = new Label(title);
		titleLabel.getStyleClass().add("alert-title");
		Label fieldLabel = new Label(label);
		fieldLabel.getStyleClass().add("alert-message");
		TextField field = new TextField(initial);
		field.setPrefWidth(FIELD_WIDTH);

		AtomicReference<String> result = new AtomicReference<String>();
		AtomicReference<ModalDialog> holder = new AtomicReference<ModalDialog>();
		Runnable submit = () -> {
			result.set(field.getText());
			holder.get().close();
		};

		Button ok = new Button("OK");
		ok.getStyleClass().add("dialog-action-button");
		ok.setDefaultButton(true);
		ok.setOnAction(event -> submit.run());
		field.setOnAction(event -> submit.run());

		ModalDialog dialog = ModalDialog.builder().addOwner(owner).stylesheets(appStylesheets(owner))
				.addContent(titleLabel).addContent(fieldLabel).addContent(field).addCloseButton("Cancel").addButton(ok)
				.build();
		holder.set(dialog);

		Platform.runLater(() -> {
			field.requestFocus();
			field.selectAll();
		});
		dialog.show();

		return Optional.ofNullable(result.get()).map(String::trim).filter(value -> !value.isEmpty());
	}

	public static boolean confirm(String title, String message, String action) {
		AtomicBoolean confirmed = new AtomicBoolean();
		AlertDialog.builder().level(AlertDialog.Level.WARNING).title(title).message(message)
				.addOwner(DailyPlannerAppMain.primaryStage()).addButton("Cancel", "dialog-close-button", null)
				.addButton(action, () -> confirmed.set(true)).build().show();
		return confirmed.get();
	}

	public static String[] appStylesheets(Stage owner) {
		if (owner == null || owner.getScene() == null) {
			return new String[0];
		}
		return owner.getScene().getStylesheets().toArray(new String[0]);
	}
}