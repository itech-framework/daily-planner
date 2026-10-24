package org.flexitech.frameword.test.util;

import java.util.function.Consumer;

import org.flexitech.frameword.test.ApiTestMain;
import org.flexitech.frameword.test.ui.component.DownloadEntry;

import io.github.itech_framework.java_fx.loader.FxComponentLoader;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;

public final class DownloadPanel {

	private static final double WIDTH = 344;
	private static final double MARGIN_RIGHT = 4;
	private static final double MARGIN_BOTTOM = 32;
	private static final String ROOT_CLASS = "root";
	private static final String PANEL_CLASS = "download-panel";
	private static final String DARK = "dark-mode";

	private static Popup popup;
	private static VBox box;
	private static Stage owner;

	private DownloadPanel() {
	}

	public static DownloadEntry add(String fileName, DownloadEntry.Direction direction) {
		ensure();
		Consumer<DownloadEntry> closer = DownloadPanel::remove;
		DownloadEntry entry = FxComponentLoader.load(DownloadEntry.class, fileName, direction, closer);
		box.getChildren().add(entry.getRoot());
		if (popup.isShowing()) {
			reposition();
		} else {
			popup.show(owner, anchorX(), anchorY());
		}
		return entry;
	}

	private static void remove(DownloadEntry entry) {
		box.getChildren().remove(entry.getRoot());
		if (box.getChildren().isEmpty()) {
			popup.hide();
		}
	}

	private static void ensure() {
		if (popup != null) {
			return;
		}
		owner = ApiTestMain.primaryStage();
		box = new VBox(8);
		box.getStyleClass().addAll(ROOT_CLASS, PANEL_CLASS);
		box.setMinWidth(WIDTH);
		box.setPrefWidth(WIDTH);
		box.setMaxWidth(WIDTH);
		Bindings.bindContent(box.getStylesheets(), owner.getScene().getStylesheets());
		bindTheme();
		popup = new Popup();
		popup.setAnchorLocation(PopupWindow.AnchorLocation.WINDOW_BOTTOM_RIGHT);
		popup.setAutoHide(false);
		popup.setHideOnEscape(false);
		popup.getContent().add(box);
		owner.xProperty().addListener((observable, previous, current) -> reposition());
		owner.yProperty().addListener((observable, previous, current) -> reposition());
		owner.widthProperty().addListener((observable, previous, current) -> reposition());
		owner.heightProperty().addListener((observable, previous, current) -> reposition());
	}

	private static void bindTheme() {
		Scene scene = owner.getScene();
		ListChangeListener<String> listener = change -> syncTheme();
		scene.getRoot().getStyleClass().addListener(listener);
		scene.rootProperty().addListener((observable, previous, current) -> {
			previous.getStyleClass().removeListener(listener);
			current.getStyleClass().addListener(listener);
			syncTheme();
		});
		syncTheme();
	}

	private static void syncTheme() {
		boolean dark = owner.getScene().getRoot().getStyleClass().contains(DARK);
		if (dark && !box.getStyleClass().contains(DARK)) {
			box.getStyleClass().add(DARK);
		} else if (!dark) {
			box.getStyleClass().remove(DARK);
		}
	}

	private static void reposition() {
		if (popup != null && popup.isShowing()) {
			popup.setAnchorX(anchorX());
			popup.setAnchorY(anchorY());
		}
	}

	private static double anchorX() {
		return owner.getX() + owner.getWidth() - MARGIN_RIGHT;
	}

	private static double anchorY() {
		return owner.getY() + owner.getHeight() - MARGIN_BOTTOM;
	}
}