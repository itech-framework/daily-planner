package org.flexitech.framework.products.daily_planner.ui.component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.flexitech.framework.products.daily_planner.model.Note;
import org.flexitech.framework.products.daily_planner.model.NoteFolder;

import io.github.itech_framework.core.annotations.constructor.DefaultConstructor;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.components.FxComponent;
import io.github.itech_framework.java_fx.annotations.components.Root;
import io.github.itech_framework.java_fx.loader.FxComponentLoader;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;

@FxComponent("/views/components/note-card.fxml")
public class NoteCard {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

	public record Handlers(Consumer<Note> open, Consumer<Note> edit, Consumer<Note> delete,
			Supplier<List<NoteFolder>> folders, BiConsumer<Note, NoteFolder> move) {
	}

	@FXML
	@Root
	private StackPane root;

	@FXML
	private Label nameLabel;

	@FXML
	private Label metaLabel;

	@FXML
	private Button menuButton;

	@FXML
	private StackPane busyPane;

	private final Note note;
	private final Handlers handlers;
	private ContextMenu menu;

	@DefaultConstructor
	public NoteCard() {
		this(null, null);
	}

	public NoteCard(Note note, Handlers handlers) {
		this.note = note;
		this.handlers = handlers == null ? new Handlers(null, null, null, null, null) : handlers;
	}

	public StackPane getRoot() {
		return root;
	}

	public Note getNote() {
		return note;
	}

	public static List<NoteCard> append(FlowPane grid, List<Note> notes, Handlers handlers) {
		List<NoteCard> created = new ArrayList<NoteCard>(notes.size());
		for (Note note : notes) {
			NoteCard card = FxComponentLoader.load(NoteCard.class, note, handlers);
			grid.getChildren().add(card.getRoot());
			created.add(card);
		}
		return created;
	}

	public void setBusy(boolean busy) {
		busyPane.setVisible(busy);
	}

	@OnInit
	private void bind() {
		if (note == null) {
			return;
		}
		nameLabel.setText(note.title());
		metaLabel.setText(note.modified() == null ? "" : DATE.format(note.modified()));
		Tooltip.install(root, new Tooltip(note.title()));
	}

	@FXML
	private void onClick(MouseEvent event) {
		if (note != null && event.getClickCount() == 2 && handlers.open() != null) {
			handlers.open().accept(note);
		}
	}

	@FXML
	private void onBusyClick(MouseEvent event) {
		event.consume();
	}

	@FXML
	private void onMenuClick(MouseEvent event) {
		event.consume();
	}

	@FXML
	private void onMenu() {
		if (note == null) {
			return;
		}
		if (menu != null && menu.isShowing()) {
			menu.hide();
			return;
		}
		menu = buildMenu();
		menu.show(menuButton, Side.BOTTOM, 0, 0);
	}

	private ContextMenu buildMenu() {
		ContextMenu contextMenu = new ContextMenu();
		contextMenu.getItems().add(actionItem("Open", handlers.open()));
		contextMenu.getItems().add(actionItem("Edit", handlers.edit()));
		contextMenu.getItems().add(moveMenu());
		contextMenu.getItems().add(new SeparatorMenuItem());
		contextMenu.getItems().add(actionItem("Move to Trash", handlers.delete()));
		return contextMenu;
	}

	private Menu moveMenu() {
		Menu move = new Menu("Move to\u2026");
		List<NoteFolder> folders = handlers.folders() == null ? List.of() : handlers.folders().get();
		for (NoteFolder folder : folders) {
			if (folder.id().equals(note.folderId())) {
				continue;
			}
			MenuItem item = new MenuItem(folder.name());
			item.setOnAction(e -> {
				if (handlers.move() != null) {
					handlers.move().accept(note, folder);
				}
			});
			move.getItems().add(item);
		}
		move.setDisable(move.getItems().isEmpty());
		return move;
	}

	private MenuItem actionItem(String text, Consumer<Note> action) {
		MenuItem item = new MenuItem(text);
		if (action == null) {
			item.setDisable(true);
		} else {
			item.setOnAction(e -> action.accept(note));
		}
		return item;
	}
}