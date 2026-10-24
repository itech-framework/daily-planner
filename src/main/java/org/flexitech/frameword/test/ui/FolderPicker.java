package org.flexitech.frameword.test.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.flexitech.frameword.test.ApiTestMain;
import org.flexitech.frameword.test.model.DriveItem;
import org.flexitech.frameword.test.model.DrivePage;
import org.flexitech.frameword.test.service.DriveService;
import org.flexitech.frameword.test.util.Notifier;

import io.github.itech_framework.java_fx.utils.concurrent.BackgroundTaskService;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class FolderPicker {

	private static final String ROOT = "root";
	private static final String DARK = "dark-mode";

	public record Choice(String id, String name) {
	}

	private final DriveService service;
	private final Set<String> excluded;
	private final String sourceFolderId;
	private final BackgroundTaskService background = BackgroundTaskService.getInstance();
	private final List<Choice> path = new ArrayList<Choice>();
	private final ListView<DriveItem> list = new ListView<DriveItem>();
	private final Label pathLabel = new Label();
	private final Button backButton = new Button("\u2190");
	private final ProgressIndicator progress = new ProgressIndicator();
	private Node moveButton;
	private int sequence;

	private FolderPicker(DriveService service, Set<String> excluded, String sourceFolderId) {
		this.service = service;
		this.excluded = excluded;
		this.sourceFolderId = sourceFolderId;
	}

	public static void show(DriveService service, Set<String> excluded, String sourceFolderId,
			Consumer<Choice> onChoose) {
		new FolderPicker(service, excluded, sourceFolderId).open(onChoose);
	}

	private void open(Consumer<Choice> onChoose) {
		Stage owner = ApiTestMain.primaryStage();
		Dialog<Choice> dialog = new Dialog<Choice>();
		dialog.initOwner(owner);
		dialog.setTitle("Move to");
		dialog.setHeaderText(null);

		DialogPane pane = dialog.getDialogPane();
		pane.getStylesheets().setAll(owner.getScene().getStylesheets());
		if (owner.getScene().getRoot().getStyleClass().contains(DARK)) {
			pane.getStyleClass().add(DARK);
		}
		ButtonType move = new ButtonType("Move here", ButtonBar.ButtonData.OK_DONE);
		pane.getButtonTypes().addAll(move, ButtonType.CANCEL);
		moveButton = pane.lookupButton(move);
		moveButton.getStyleClass().add("accent-button");
		pane.setContent(buildContent());

		dialog.setResultConverter(type -> type == move ? current() : null);
		path.add(new Choice(ROOT, "My Drive"));
		navigate();
		dialog.showAndWait().ifPresent(onChoose);
	}

	private VBox buildContent() {
		backButton.getStyleClass().add("ghost-button");
		backButton.setFocusTraversable(false);
		backButton.setOnAction(event -> {
			if (path.size() > 1) {
				path.remove(path.size() - 1);
				navigate();
			}
		});
		pathLabel.getStyleClass().add("crumb-current");
		progress.setPrefSize(16, 16);
		progress.setVisible(false);
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox header = new HBox(8, backButton, pathLabel, spacer, progress);
		header.setAlignment(Pos.CENTER_LEFT);

		Label empty = new Label("No folders here");
		empty.getStyleClass().add("muted");
		list.setPlaceholder(empty);
		list.setPrefSize(420, 320);
		list.setCellFactory(view -> new ListCell<DriveItem>() {
			@Override
			protected void updateItem(DriveItem item, boolean isEmpty) {
				super.updateItem(item, isEmpty);
				setText(isEmpty || item == null ? null : item.name());
			}
		});
		list.setOnMouseClicked(event -> {
			DriveItem selected = list.getSelectionModel().getSelectedItem();
			if (event.getClickCount() == 2 && selected != null) {
				path.add(new Choice(selected.id(), selected.name()));
				navigate();
			}
		});

		Label hint = new Label("Double-click a folder to open it");
		hint.getStyleClass().add("muted");
		return new VBox(8, header, list, hint);
	}

	private void navigate() {
		String id = current().id();
		pathLabel.setText(path.stream().map(Choice::name).collect(Collectors.joining(" \u203a ")));
		backButton.setDisable(path.size() <= 1);
		moveButton.setDisable(id.equals(sourceFolderId));
		list.getItems().clear();
		progress.setVisible(true);
		int current = ++sequence;
		background.executeTask(() -> folders(id), result -> {
			if (current != sequence) {
				return;
			}
			progress.setVisible(false);
			list.getItems().setAll(result);
		}, error -> {
			if (current != sequence) {
				return;
			}
			progress.setVisible(false);
			Notifier.error("Could not load folders", error.getMessage());
		});
	}

	private List<DriveItem> folders(String id) throws Exception {
		List<DriveItem> folders = new ArrayList<DriveItem>();
		String token = null;
		do {
			DrivePage page = service.listFolder(id, token);
			for (DriveItem item : page.items()) {
				if (!item.folder()) {
					return folders;
				}
				if (!excluded.contains(item.id())) {
					folders.add(item);
				}
			}
			token = page.nextPageToken();
		} while (token != null);
		return folders;
	}

	private Choice current() {
		return path.get(path.size() - 1);
	}
}