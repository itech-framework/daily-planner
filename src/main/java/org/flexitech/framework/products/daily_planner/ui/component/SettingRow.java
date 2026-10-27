package org.flexitech.framework.products.daily_planner.ui.component;

import java.io.File;
import java.util.function.Consumer;

import org.flexitech.framework.products.daily_planner.model.Setting;
import org.flexitech.framework.products.daily_planner.service.SettingsService;

import io.github.itech_framework.core.annotations.constructor.DefaultConstructor;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.components.FxComponent;
import io.github.itech_framework.java_fx.annotations.components.Root;
import io.github.itech_framework.java_fx.loader.FxComponentLoader;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

@FxComponent("/views/components/setting-row.fxml")
public class SettingRow {

	private static final String DEFAULT_PREFIX = "Default: ";
	private static final String EMPTY_DEFAULT = "(empty)";

	@FXML
	@Root
	private VBox root;

	@FXML
	private Label nameLabel;

	@FXML
	private Label overriddenLabel;

	@FXML
	private Button resetButton;

	@FXML
	private TextField input;

	@FXML
	private Button browseButton;

	@FXML
	private Label hintLabel;

	@FXML
	private Label defaultLabel;

	private final Setting setting;
	private final SettingsService settings;
	private final Consumer<Setting> resetHandler;
	private final Consumer<SettingRow> registrar;

	@DefaultConstructor
	public SettingRow() {
		this(null, null, null, null);
	}

	public SettingRow(Setting setting, SettingsService settings, Consumer<Setting> resetHandler,
			Consumer<SettingRow> registrar) {
		this.setting = setting;
		this.settings = settings;
		this.resetHandler = resetHandler;
		this.registrar = registrar;
	}

	public static Region create(Setting setting, SettingsService settings, Consumer<Setting> resetHandler,
			Consumer<SettingRow> registrar) {
		return FxComponentLoader.load(SettingRow.class, setting, settings, resetHandler, registrar).root;
	}

	@OnInit
	private void bind() {
		if (setting == null) {
			return;
		}
		nameLabel.setText(setting.label());
		String hint = setting.hint();
		boolean hasHint = hint != null && !hint.isBlank();
		hintLabel.setText(hasHint ? hint : "");
		show(hintLabel, hasHint);
		show(browseButton, setting.kind() == Setting.Kind.FOLDER);
		String fallback = settings.getDefault(setting);
		defaultLabel.setText(DEFAULT_PREFIX + (fallback == null || fallback.isEmpty() ? EMPTY_DEFAULT : fallback));
		reload();
		if (registrar != null) {
			registrar.accept(this);
		}
	}

	public Setting setting() {
		return setting;
	}

	public String value() {
		String text = input.getText();
		return text == null ? "" : text.trim();
	}

	public void reload() {
		String current = settings.getString(setting);
		input.setText(current == null ? "" : current);
		boolean overridden = settings.isOverridden(setting);
		show(overriddenLabel, overridden);
		resetButton.setDisable(!overridden);
	}

	@FXML
	private void onReset() {
		if (resetHandler != null) {
			resetHandler.accept(setting);
		}
	}

	@FXML
	private void onBrowse() {
		DirectoryChooser chooser = new DirectoryChooser();
		chooser.setTitle(setting.label());
		File start = existingFolder(value());
		if (start != null) {
			chooser.setInitialDirectory(start);
		}
		Window owner = input.getScene() == null ? null : input.getScene().getWindow();
		File chosen = chooser.showDialog(owner);
		if (chosen != null) {
			input.setText(chosen.getAbsolutePath());
		}
	}

	private static File existingFolder(String text) {
		if (text.isEmpty()) {
			return null;
		}
		File folder = new File(text);
		return folder.isDirectory() ? folder : null;
	}

	private static void show(Node node, boolean visible) {
		node.setVisible(visible);
		node.setManaged(visible);
	}
}