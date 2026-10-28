package org.flexitech.framework.products.daily_planner.ui;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.framework.products.daily_planner.DailyPlannerAppMain;
import org.flexitech.framework.products.daily_planner.model.NoteDetail;
import org.flexitech.framework.products.daily_planner.model.NoteEditorArgs;
import org.flexitech.framework.products.daily_planner.service.NoteService;
import org.flexitech.framework.products.daily_planner.util.Notifier;
import org.flexitech.framework.products.daily_planner.util.Prompt;

import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.router.Router;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_framework.java_fx.utils.concurrent.TaskScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

@FxController
public class NoteViewController implements Routable {

	private static final Logger log = LogManager.getLogger(NoteViewController.class);
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");
	private static final String PAPER_CSS = "html{background:#ffffff;}body{margin:0 auto;max-width:860px;padding:24px 32px !important;background:#ffffff;}";
	private static final String STYLE = "data:text/css;base64,"
			+ Base64.getEncoder().encodeToString(PAPER_CSS.getBytes(StandardCharsets.UTF_8));

	@Use
	private NoteService noteService;

	@Use
	private Router router;

	@FXML
	private Label titleLabel;

	@FXML
	private Label metaLabel;

	@FXML
	private WebView webView;

	@FXML
	private ProgressIndicator progress;

	@FXML
	private Button editButton;

	@FXML
	private Button browserButton;

	@FXML
	private Button deleteButton;

	private final TaskScope background = new TaskScope();

	private NoteDetail detail;
	private String lastContent = "";
	private volatile boolean disposed;
	private boolean modalOpen;
	private boolean deleting;

	@OnInit
	public void initialized() {
		WebEngine engine = webView.getEngine();
		engine.setUserStyleSheetLocation(STYLE);
		engine.locationProperty().addListener((observable, previous, current) -> {
			if (current != null && (current.startsWith("http://") || current.startsWith("https://"))) {
				Platform.runLater(() -> {
					openExternal(current);
					engine.loadContent(lastContent);
				});
			}
		});
		setActions(false);
	}

	@Override
	public void onNavigate(Object arguments) {
		disposed = false;
		detail = null;
		lastContent = "";
		titleLabel.setText("");
		metaLabel.setText("");
		webView.getEngine().loadContent("");
		setActions(false);
		if (!(arguments instanceof String id) || id.isBlank()) {
			router.off("notes");
			return;
		}
		progress.setVisible(true);
		background.run(() -> noteService.getNote(id), loaded -> {
			if (disposed) {
				return;
			}
			progress.setVisible(false);
			detail = loaded;
			titleLabel.setText(loaded.note().title());
			metaLabel.setText(loaded.note().modified() == null ? ""
					: "Modified " + DATE.format(loaded.note().modified()));
			lastContent = loaded.html();
			webView.getEngine().loadContent(lastContent);
			setActions(true);
		}, error -> {
			if (disposed) {
				return;
			}
			progress.setVisible(false);
			log.error("Could not open note {}", id, error);
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
		modalOpen = false;
		deleting = false;
	}

	@FXML
	private void onBack() {
		goToList();
	}

	@FXML
	private void onEdit() {
		if (detail != null) {
			router.off("note-edit", new NoteEditorArgs(detail.note().id(), detail.note().folderId()));
		}
	}

	@FXML
	private void onOpenInBrowser() {
		if (detail != null) {
			openExternal(detail.note().link());
		}
	}

	@FXML
	private void onDelete() {
		if (detail == null || deleting || modalOpen) {
			return;
		}
		String id = detail.note().id();
		String title = detail.note().title();
		boolean confirmed = modal(
				() -> Prompt.confirm("Move to Trash", "Move \"" + title + "\" to the Drive trash?", "Move to Trash"),
				Boolean.FALSE);
		if (!confirmed || deleting) {
			return;
		}
		deleting = true;
		setActions(false);
		progress.setVisible(true);
		background.run(() -> {
			noteService.deleteNote(id);
			return Boolean.TRUE;
		}, done -> {
			deleting = false;
			Notifier.success("Moved to Trash", title);
			if (!disposed) {
				goToList();
			}
		}, error -> {
			deleting = false;
			progress.setVisible(false);
			setActions(true);
			log.error("Could not delete note {}", id, error);
			Notifier.error("Could not delete note", error.getMessage());
		});
	}

	private void goToList() {
		String folder = detail == null ? null : detail.note().folderId();
		if (folder == null || folder.isBlank()) {
			router.off("notes");
		} else {
			router.off("notes", folder);
		}
	}

	private void openExternal(String link) {
		if (link == null || link.isBlank() || DailyPlannerAppMain.hostServices() == null) {
			Notifier.warning("Cannot open link", String.valueOf(link));
			return;
		}
		DailyPlannerAppMain.hostServices().showDocument(link);
	}

	private void setActions(boolean enabled) {
		editButton.setDisable(!enabled);
		browserButton.setDisable(!enabled);
		deleteButton.setDisable(!enabled);
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