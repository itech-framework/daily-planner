package org.flexitech.framework.products.daily_planner.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.framework.products.daily_planner.app.AppEvents;
import org.flexitech.framework.products.daily_planner.model.Note;
import org.flexitech.framework.products.daily_planner.model.NoteEditorArgs;
import org.flexitech.framework.products.daily_planner.model.NoteFolder;
import org.flexitech.framework.products.daily_planner.service.NoteService;
import org.flexitech.framework.products.daily_planner.ui.component.NoteCard;
import org.flexitech.framework.products.daily_planner.util.Notifier;
import org.flexitech.framework.products.daily_planner.util.Prompt;

import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.events.EventBus;
import io.github.itech_framework.java_fx.router.Router;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_framework.java_fx.utils.concurrent.TaskScope;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;

@FxController
public class NotesController implements Routable {

	private static final Logger log = LogManager.getLogger(NotesController.class);

	@Use
	private NoteService noteService;

	@Use
	private Router router;

	@FXML
	private FlowPane folderBar;

	@FXML
	private TextField searchField;

	@FXML
	private ProgressIndicator progress;

	@FXML
	private Button refreshButton;

	@FXML
	private Button renameFolderButton;

	@FXML
	private Button deleteFolderButton;

	@FXML
	private FlowPane cardGrid;

	@FXML
	private Label emptyLabel;

	private final TaskScope background = new TaskScope();
	private final List<NoteCard> cards = new ArrayList<NoteCard>();
	private final Set<String> workingIds = new HashSet<String>();

	private List<NoteFolder> folders = new ArrayList<NoteFolder>();
	private List<Note> notes = new ArrayList<Note>();
	private String folderId;
	private int loadSequence;
	private volatile boolean disposed;
	private boolean modalOpen;
	private boolean folderBusy;

	private interface NoteAction {
		void run() throws Exception;
	}

	private record NotesData(List<NoteFolder> folders, String folderId, List<Note> notes) {
	}

	@OnInit
	public void initialized() {
		searchField.textProperty().addListener((observable, previous, current) -> renderNotes());
	}

	@Override
	public void onNavigate(Object arguments) {
		disposed = false;
		searchField.clear();
		if (arguments instanceof String id && !id.isBlank()) {
			folderId = id;
		}
		loadAll();
	}

	@Override
	public void onResume() {
		disposed = false;
	}

	@Override
	public void dispose() {
		disposed = true;
		background.cancelAll();
		workingIds.clear();
		modalOpen = false;
		folderBusy = false;
	}

	@FXML
	private void onRefresh() {
		loadAll();
	}

	@FXML
	private void onNewNote() {
		router.off("note-edit", new NoteEditorArgs(null, folderId));
	}

	@FXML
	private void onNewFolder() {
		if (folderBusy) {
			return;
		}
		Optional<String> name = ask("New folder", "Name", "New folder");
		if (name.isEmpty() || folderBusy) {
			return;
		}
		folderBusy = true;
		setLoading(true);
		background.run(() -> noteService.createFolder(name.get()), created -> {
			folderBusy = false;
			Notifier.success("Folder created", created.name());
			if (!disposed) {
				folderId = created.id();
				loadAll();
			}
		}, error -> {
			folderBusy = false;
			setLoading(false);
			log.error("Could not create folder", error);
			Notifier.error("Could not create folder", error.getMessage());
		});
	}

	@FXML
	private void onRenameFolder() {
		if (folderBusy || isGeneral()) {
			return;
		}
		String current = folderName(folderId);
		Optional<String> name = ask("Rename folder", "Name", current);
		if (name.isEmpty() || name.get().equals(current) || folderBusy) {
			return;
		}
		String target = folderId;
		folderBusy = true;
		setLoading(true);
		background.run(() -> noteService.renameFolder(target, name.get()), renamed -> {
			folderBusy = false;
			Notifier.success("Folder renamed", renamed.name());
			if (!disposed) {
				loadAll();
			}
		}, error -> {
			folderBusy = false;
			setLoading(false);
			log.error("Could not rename folder", error);
			Notifier.error("Could not rename folder", error.getMessage());
		});
	}

	@FXML
	private void onDeleteFolder() {
		if (folderBusy || isGeneral()) {
			return;
		}
		String target = folderId;
		String name = folderName(target);
		if (!confirm("Move folder to Trash", "Move \"" + name + "\" and all notes in it to the Drive trash?",
				"Move to Trash") || folderBusy) {
			return;
		}
		folderBusy = true;
		setLoading(true);
		background.run(() -> {
			noteService.deleteFolder(target);
			return Boolean.TRUE;
		}, done -> {
			folderBusy = false;
			Notifier.success("Folder moved to Trash", name);
			if (!disposed) {
				folderId = null;
				loadAll();
			}
		}, error -> {
			folderBusy = false;
			setLoading(false);
			log.error("Could not delete folder", error);
			Notifier.error("Could not delete folder", error.getMessage());
		});
	}

	private void loadAll() {
		int sequence = ++loadSequence;
		String wanted = folderId;
		setLoading(true);
		background.run(() -> {
			List<NoteFolder> loaded = noteService.getFolders();
			String selected = resolve(loaded, wanted);
			return new NotesData(loaded, selected, noteService.getNotes(selected));
		}, data -> {
			if (disposed || sequence != loadSequence) {
				return;
			}
			setLoading(false);
			folders = data.folders();
			folderId = data.folderId();
			notes = data.notes();
			renderFolders();
			renderNotes();
		}, error -> {
			if (disposed || sequence != loadSequence) {
				return;
			}
			setLoading(false);
			log.error("Notes failed to load", error);
			Notifier.error("Could not load notes", error.getMessage());
			EventBus.publish(AppEvents.STATUS, "Notes failed to load");
		});
	}

	private void loadNotes() {
		int sequence = ++loadSequence;
		String target = folderId;
		setLoading(true);
		background.run(() -> noteService.getNotes(target), loaded -> {
			if (disposed || sequence != loadSequence) {
				return;
			}
			setLoading(false);
			notes = loaded;
			renderNotes();
		}, error -> {
			if (disposed || sequence != loadSequence) {
				return;
			}
			setLoading(false);
			log.error("Notes failed to load", error);
			Notifier.error("Could not load notes", error.getMessage());
		});
	}

	private static String resolve(List<NoteFolder> list, String wanted) {
		for (NoteFolder folder : list) {
			if (folder.id().equals(wanted)) {
				return folder.id();
			}
		}
		return list.get(0).id();
	}

	private void renderFolders() {
		folderBar.getChildren().clear();
		ToggleGroup group = new ToggleGroup();
		group.selectedToggleProperty().addListener((observable, previous, current) -> {
			if (current == null && previous != null) {
				previous.setSelected(true);
			}
		});
		for (NoteFolder folder : folders) {
			ToggleButton button = new ToggleButton(folder.name());
			button.getStyleClass().add("seg");
			button.setToggleGroup(group);
			button.setSelected(folder.id().equals(folderId));
			button.setOnAction(event -> selectFolder(folder.id()));
			folderBar.getChildren().add(button);
		}
		updateFolderButtons();
	}

	private void selectFolder(String id) {
		if (id.equals(folderId)) {
			return;
		}
		folderId = id;
		updateFolderButtons();
		loadNotes();
	}

	private void updateFolderButtons() {
		boolean general = isGeneral();
		renameFolderButton.setDisable(general);
		deleteFolderButton.setDisable(general);
	}

	private boolean isGeneral() {
		return folders.isEmpty() || folders.get(0).id().equals(folderId);
	}

	private String folderName(String id) {
		for (NoteFolder folder : folders) {
			if (folder.id().equals(id)) {
				return folder.name();
			}
		}
		return "";
	}

	private void renderNotes() {
		cardGrid.getChildren().clear();
		cards.clear();
		String filter = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
		List<Note> visible = notes.stream()
				.filter(note -> filter.isEmpty() || note.title().toLowerCase(Locale.ROOT).contains(filter)).toList();
		cards.addAll(NoteCard.append(cardGrid, visible, handlers()));
		emptyLabel.setText(filter.isEmpty() ? "No notes in this folder" : "No notes match your search");
		emptyLabel.setVisible(visible.isEmpty());
		emptyLabel.setManaged(visible.isEmpty());
		for (NoteCard card : cards) {
			card.setBusy(workingIds.contains(card.getNote().id()));
		}
		EventBus.publish(AppEvents.STATUS, notes.size() + (notes.size() == 1 ? " note" : " notes"));
	}

	private NoteCard.Handlers handlers() {
		return new NoteCard.Handlers(this::open, this::edit, this::delete, () -> folders, this::move);
	}

	private void open(Note note) {
		router.off("note-view", note.id());
	}

	private void edit(Note note) {
		router.off("note-edit", new NoteEditorArgs(note.id(), note.folderId()));
	}

	private void delete(Note note) {
		if (workingIds.contains(note.id())) {
			return;
		}
		if (!confirm("Move to Trash", "Move \"" + note.title() + "\" to the Drive trash?", "Move to Trash")
				|| workingIds.contains(note.id())) {
			return;
		}
		runNoteTask(note, "Moving to Trash\u2026", () -> noteService.deleteNote(note.id()), "Moved to Trash",
				note.title());
	}

	private void move(Note note, NoteFolder target) {
		if (workingIds.contains(note.id())) {
			return;
		}
		runNoteTask(note, "Moving note\u2026", () -> noteService.moveNote(note.id(), target.id()), "Note moved",
				"Moved to " + target.name());
	}

	private void runNoteTask(Note note, String status, NoteAction action, String title, String message) {
		if (!workingIds.add(note.id())) {
			return;
		}
		setBusy(note.id(), true);
		EventBus.publish(AppEvents.STATUS, status);
		int sequence = loadSequence;
		background.run(() -> {
			action.run();
			return Boolean.TRUE;
		}, done -> {
			workingIds.remove(note.id());
			Notifier.success(title, message);
			if (!disposed && sequence == loadSequence) {
				notes.removeIf(item -> item.id().equals(note.id()));
				renderNotes();
			}
		}, error -> {
			workingIds.remove(note.id());
			setBusy(note.id(), false);
			log.error("{} failed for {}", title, note.title(), error);
			Notifier.error(title + " failed", error.getMessage());
		});
	}

	private void setBusy(String id, boolean busy) {
		for (NoteCard card : cards) {
			if (card.getNote().id().equals(id)) {
				card.setBusy(busy);
			}
		}
	}

	private void setLoading(boolean loading) {
		progress.setVisible(loading);
		refreshButton.setDisable(loading);
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

	private boolean confirm(String title, String message, String action) {
		return modal(() -> Prompt.confirm(title, message, action), Boolean.FALSE);
	}

	private Optional<String> ask(String title, String label, String initial) {
		return modal(() -> Prompt.text(title, label, initial), Optional.<String>empty());
	}
}