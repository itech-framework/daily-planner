package org.flexitech.framework.products.daily_planner.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.flexitech.framework.products.daily_planner.ApiTestMain;
import org.flexitech.framework.products.daily_planner.model.DriveItem;
import org.flexitech.framework.products.daily_planner.model.DrivePage;
import org.flexitech.framework.products.daily_planner.service.DriveService;
import org.flexitech.framework.products.daily_planner.util.Notifier;

import io.github.itech_framework.java_fx.ui.dialog.ModalDialog;
import io.github.itech_framework.java_fx.utils.concurrent.BackgroundTaskService;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class FolderPicker {

	private static final String ROOT = "root";

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
	private final Button moveButton = new Button("Move here");
	private final ProgressIndicator progress = new ProgressIndicator();
	private int sequence;
	private boolean chosen;

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
		AtomicReference<Choice> result = new AtomicReference<Choice>();
		AtomicReference<ModalDialog> holder = new AtomicReference<ModalDialog>();

		Label titleLabel = new Label("Move to");
		titleLabel.getStyleClass().add("alert-title");

		moveButton.getStyleClass().addAll("dialog-action-button", "accent-button");
		moveButton.setDefaultButton(true);
		moveButton.setOnAction(event -> {
			if (chosen) {
				return;
			}
			chosen = true;
			moveButton.setDisable(true);
			backButton.setDisable(true);
			list.setDisable(true);
			result.set(current());
			holder.get().close();
		});

		ModalDialog dialog = ModalDialog.builder()
				.addOwner(owner)
				.stylesheets(org.flexitech.framework.products.daily_planner.util.Prompt.appStylesheets(owner))
				.addContent(titleLabel)
				.addContent(buildContent())
				.addCloseButton("Cancel")
				.addButton(moveButton)
				.build();
		holder.set(dialog);

		path.add(new Choice(ROOT, "My Drive"));
		navigate();
		dialog.show();

		Choice choice = result.get();
		if (choice != null) {
			onChoose.accept(choice);
		}
	}

	private VBox buildContent() {
		backButton.getStyleClass().add("ghost-button");
		backButton.setFocusTraversable(false);
		backButton.setOnAction(event -> {
			if (!chosen && path.size() > 1) {
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
			if (chosen) {
				return;
			}
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
		if (chosen) {
			return;
		}
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