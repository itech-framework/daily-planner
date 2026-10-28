package org.flexitech.framework.products.daily_planner.ui;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.framework.products.daily_planner.exception.NoteConflictException;
import org.flexitech.framework.products.daily_planner.model.NoteDetail;
import org.flexitech.framework.products.daily_planner.model.NoteDraft;
import org.flexitech.framework.products.daily_planner.model.NoteEditorArgs;
import org.flexitech.framework.products.daily_planner.model.NoteFolder;
import org.flexitech.framework.products.daily_planner.service.NoteService;
import org.flexitech.framework.products.daily_planner.util.Notifier;
import org.flexitech.framework.products.daily_planner.util.Prompt;

import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.router.Router;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_framework.java_fx.utils.concurrent.TaskScope;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.web.HTMLEditor;
import javafx.util.Duration;
import javafx.util.StringConverter;

@FxController
public class NoteEditorController implements Routable {

	private static final Logger log = LogManager.getLogger(NoteEditorController.class);

	@Use
	private NoteService noteService;

	@Use
	private Router router;

	@FXML
	private TextField titleField;

	@FXML
	private ComboBox<NoteFolder> folderBox;

	@FXML
	private HTMLEditor editor;

	@FXML
	private Button saveButton;

	@FXML
	private ProgressIndicator progress;

	private final TaskScope background = new TaskScope();
	private final PauseTransition baseline = new PauseTransition(Duration.millis(400));

	private String noteId;
	private String startFolderId;
	private String baseTitle = "";
	private String baseHtml;
	private boolean loaded;
	private boolean saving;
	private volatile boolean disposed;
	private boolean modalOpen;

	private record EditorData(List<NoteFolder> folders, NoteDetail detail) {
	}

	@OnInit
	public void initialized() {
		folderBox.setConverter(new StringConverter<NoteFolder>() {
			@Override
			public String toString(NoteFolder folder) {
				return folder == null ? "" : folder.name();
			}

			@Override
			public NoteFolder fromString(String text) {
				return null;
			}
		});
		baseline.setOnFinished(event -> baseHtml = editor.getHtmlText());
	}

	@Override
	public void onNavigate(Object arguments) {
		disposed = false;
		NoteEditorArgs args = arguments instanceof NoteEditorArgs value ? value : new NoteEditorArgs(null, null);
		noteId = args.noteId();
		startFolderId = args.folderId();
		loaded = false;
		saving = false;
		baseHtml = null;
		baseTitle = "";
		baseline.stop();
		titleField.clear();
		editor.setHtmlText("");
		folderBox.getItems().clear();
		setBusy(true);

		background.run(() -> {
			List<NoteFolder> folders = noteService.getFolders();
			NoteDetail detail = noteId == null ? null : noteService.getNote(noteId);
			return new EditorData(folders, detail);
		}, data -> {
			if (disposed) {
				return;
			}
			show(data);
		}, error -> {
			if (disposed) {
				return;
			}
			setBusy(false);
			saveButton.setDisable(true);
			log.error("Could not open the editor", error);
			Notifier.error("Could not open note", error.getMessage());
		});
	}

	@Override
	public void onResume() {
		disposed = false;
	}

	@Override
	public void dispose() {
		disposed = true;
		background.cancelAll();
		baseline.stop();
		saving = false;
		modalOpen = false;
	}

	private void show(EditorData data) {
		folderBox.getItems().setAll(data.folders());
		String wanted = data.detail() == null ? startFolderId : data.detail().note().folderId();
		NoteFolder selected = data.folders().get(0);
		for (NoteFolder folder : data.folders()) {
			if (folder.id().equals(wanted)) {
				selected = folder;
				break;
			}
		}
		folderBox.getSelectionModel().select(selected);
		titleField.setText(data.detail() == null ? "" : data.detail().note().title());
		baseTitle = titleField.getText();
		editor.setHtmlText(data.detail() == null ? "" : data.detail().html());
		loaded = true;
		setBusy(false);
		baseline.playFromStart();
		if (data.detail() == null) {
			titleField.requestFocus();
		}
	}

	@FXML
	private void onSave() {
		save(false);
	}

	private void save(boolean overwrite) {
		NoteFolder folder = folderBox.getValue();
		if (!loaded || saving || folder == null) {
			return;
		}
		NoteDraft draft = new NoteDraft(noteId, titleField.getText(), folder.id(), editor.getHtmlText());
		saving = true;
		setBusy(true);
		background.run(() -> noteService.saveNote(draft, overwrite), saved -> {
			saving = false;
			Notifier.success("Note saved", saved.title());
			if (!disposed) {
				baseTitle = titleField.getText();
				baseHtml = null;
				router.off("note-view", saved.id());
			}
		}, error -> {
			saving = false;
			if (!disposed) {
				setBusy(false);
			}
			if (error instanceof NoteConflictException) {
				if (!disposed && confirmOverwrite()) {
					save(true);
				}
				return;
			}
			log.error("Could not save note", error);
			Notifier.error("Could not save note", error.getMessage());
		});
	}

	private boolean confirmOverwrite() {
		return modal(() -> Prompt.confirm("Note changed in Google Docs",
				"This note was edited in Google Docs after you opened it. Overwrite those changes with your version?",
				"Overwrite"), Boolean.FALSE);
	}

	@FXML
	private void onBack() {
		if (saving || modalOpen) {
			return;
		}
		if (dirty()) {
			boolean discard = modal(
					() -> Prompt.confirm("Discard changes", "Leave without saving your changes?", "Discard"),
					Boolean.FALSE);
			if (!discard) {
				return;
			}
		}
		goBack();
	}

	private void goBack() {
		if (noteId != null) {
			router.off("note-view", noteId);
		} else if (startFolderId != null && !startFolderId.isBlank()) {
			router.off("notes", startFolderId);
		} else {
			router.off("notes");
		}
	}

	private boolean dirty() {
		if (!loaded) {
			return false;
		}
		String title = titleField.getText() == null ? "" : titleField.getText().trim();
		if (!title.equals(baseTitle.trim())) {
			return true;
		}
		return baseHtml != null && !Objects.equals(baseHtml, editor.getHtmlText());
	}

	private void setBusy(boolean busy) {
		progress.setVisible(busy);
		saveButton.setDisable(busy);
		titleField.setDisable(busy);
		folderBox.setDisable(busy);
		editor.setDisable(busy);
	}

	private <T> T modal(Supplier<T> action, T fallback) {
		if (modalOpen) {
			return fallback;
		}
		modalOpen = true;
		try {
			return action.get();
		} finally {
			modalOpen = false;
		}
	}
}