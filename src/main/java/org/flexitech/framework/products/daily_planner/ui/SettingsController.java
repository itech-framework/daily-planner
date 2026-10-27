package org.flexitech.framework.products.daily_planner.ui;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.framework.products.daily_planner.ApiTestMain;
import org.flexitech.framework.products.daily_planner.model.Setting;
import org.flexitech.framework.products.daily_planner.service.SettingsService;
import org.flexitech.framework.products.daily_planner.ui.component.SettingRow;
import org.flexitech.framework.products.daily_planner.util.Notifier;

import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_framework.java_fx.utils.concurrent.TaskScope;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

@FxController
public class SettingsController implements Routable {

	private static final Logger log = LogManager.getLogger(SettingsController.class);
	private static final String SECTION_TITLE = "section-title";
	private static final String CARD = "settings-card";
	private static final String DIVIDER = "settings-divider";
	private static final String RESET_ALL_TEXT = "All settings will return to the values from the properties file.";

	@Use
	private SettingsService settings;

	@FXML
	private VBox sectionsBox;

	@FXML
	private ProgressIndicator progress;

	@FXML
	private Button saveButton;

	@FXML
	private Button revertButton;

	@FXML
	private Button resetAllButton;

	private final TaskScope background = new TaskScope();
	private final Map<Setting, SettingRow> rows = new EnumMap<>(Setting.class);

	@OnInit
	public void initialized() {
		buildSections();
	}

	@Override
	public void onNavigate(Object arguments) {
		reloadRows();
	}

	@Override
	public void onResume() {
		reloadRows();
	}

	@Override
	public void dispose() {
		background.cancelAll();
	}

	@FXML
	private void onSave() {
		Map<Setting, String> values = new EnumMap<>(Setting.class);
		rows.forEach((setting, row) -> values.put(setting, row.value()));
		setBusy(true);
		background.run(() -> {
			settings.save(values);
			return Boolean.TRUE;
		}, saved -> {
			setBusy(false);
			reloadRows();
			Notifier.success("Settings saved", "Changes apply right away.");
		}, error -> {
			setBusy(false);
			log.warn("Settings were not saved: {}", error.getMessage());
			Notifier.error("Settings not saved", error.getMessage());
		});
	}

	@FXML
	private void onRevert() {
		reloadRows();
	}

	@FXML
	private void onResetAll() {
		if (!confirmResetAll()) {
			return;
		}
		setBusy(true);
		background.run(() -> {
			settings.resetAll();
			return Boolean.TRUE;
		}, done -> {
			setBusy(false);
			reloadRows();
			Notifier.success("Settings reset", "All values are back to their defaults.");
		}, error -> {
			setBusy(false);
			log.warn("Settings were not reset: {}", error.getMessage());
			Notifier.error("Settings not reset", error.getMessage());
		});
	}

	private void resetOne(Setting setting) {
		setBusy(true);
		background.run(() -> {
			settings.reset(setting);
			return setting;
		}, done -> {
			setBusy(false);
			SettingRow row = rows.get(done);
			if (row != null) {
				row.reload();
			}
			Notifier.success("Setting reset", done.label());
		}, error -> {
			setBusy(false);
			log.warn("Setting was not reset: {}", error.getMessage());
			Notifier.error("Setting not reset", error.getMessage());
		});
	}

	private void register(SettingRow row) {
		rows.put(row.setting(), row);
	}

	private void buildSections() {
		sectionsBox.getChildren().clear();
		rows.clear();
		for (Setting.Group group : Setting.Group.values()) {
			List<Setting> members = Arrays.stream(Setting.values()).filter(setting -> setting.group() == group)
					.toList();
			if (members.isEmpty()) {
				continue;
			}
			Label title = new Label(group.title());
			title.getStyleClass().add(SECTION_TITLE);
			sectionsBox.getChildren().addAll(title, buildCard(members));
		}
	}

	private VBox buildCard(List<Setting> members) {
		VBox card = new VBox();
		card.getStyleClass().add(CARD);
		for (int index = 0; index < members.size(); index++) {
			if (index > 0) {
				card.getChildren().add(divider());
			}
			card.getChildren().add(SettingRow.create(members.get(index), settings, this::resetOne, this::register));
		}
		return card;
	}

	private Node divider() {
		Region line = new Region();
		line.getStyleClass().add(DIVIDER);
		return line;
	}

	private void reloadRows() {
		rows.values().forEach(SettingRow::reload);
	}

	private boolean confirmResetAll() {
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION, RESET_ALL_TEXT, ButtonType.CANCEL, ButtonType.OK);
		alert.setTitle("Reset all settings");
		alert.setHeaderText("Reset all settings?");
		Window owner = ApiTestMain.primaryStage();
		alert.initOwner(owner);
		Scene scene = owner == null ? null : owner.getScene();
		if (scene != null) {
			alert.getDialogPane().getStylesheets().addAll(scene.getStylesheets());
			alert.getDialogPane().getStyleClass().addAll(scene.getRoot().getStyleClass());
		}
		return alert.showAndWait().filter(button -> button == ButtonType.OK).isPresent();
	}

	private void setBusy(boolean busy) {
		progress.setVisible(busy);
		progress.setManaged(busy);
		saveButton.setDisable(busy);
		revertButton.setDisable(busy);
		resetAllButton.setDisable(busy);
	}
}