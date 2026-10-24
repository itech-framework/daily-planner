package org.flexitech.frameword.test.ui;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.frameword.test.ApiTestMain;
import org.flexitech.frameword.test.app.AppEvents;
import org.flexitech.frameword.test.model.DriveDownload;
import org.flexitech.frameword.test.model.DriveItem;
import org.flexitech.frameword.test.model.DrivePage;
import org.flexitech.frameword.test.service.DriveService;
import org.flexitech.frameword.test.ui.component.DriveCard;
import org.flexitech.frameword.test.util.Downloads;
import org.flexitech.frameword.test.util.Notifier;
import org.flexitech.frameword.test.util.Prompt;
import org.flexitech.frameword.test.util.Uploads;
import org.flexitech.frameword.test.util.ZipDownloads;

import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.core.annotations.storage.DataStorage;
import io.github.itech_framework.core.utils.DataStorageUtil;
import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.events.EventBus;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_framework.java_fx.utils.concurrent.BackgroundTaskService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.util.Duration;

@FxController
public class DriveController implements Routable {

	private static final Logger log = LogManager.getLogger(DriveController.class);
	private static final String ROOT = "root";
	private static final double LOAD_THRESHOLD = 0.97;
	private static final double CARD_MIN_WIDTH = 176;
	private static final String KEY_LAST_DIR = "lastDownloadDirectory";
	private static final String SELECTION_ACTIVE = "selection-active";
	private static final long USER_SCROLL_WINDOW_MS = 600;
	private static final String SCROLL_BAR = "scroll-bar";

	@Use
	private DriveService driveService;

	@FXML
	private HBox breadcrumbBar;

	@FXML
	private ProgressIndicator progress;

	@FXML
	private Button refreshButton;

	@FXML
	private Label warningLabel;

	@FXML
	private Label emptyLabel;

	@FXML
	private ScrollPane scrollPane;

	@FXML
	private FlowPane cardGrid;

	@FXML
	private Button loadMoreButton;

	@FXML
	private ToggleGroup sectionGroup;

	@FXML
	private ToggleButton myDriveToggle;

	@FXML
	private ToggleButton sharedToggle;

	@FXML
	private ToggleButton trashToggle;

	@FXML
	private TextField searchField;

	@FXML
	private Button emptyTrashButton;

	@FXML
	private HBox selectionBar;

	@FXML
	private Label selectionLabel;

	@FXML
	private Button downloadSelectedButton;

	@FXML
	private Button moveSelectedButton;

	@FXML
	private Button restoreSelectedButton;

	@FXML
	private Button deleteSelectedButton;

	private final List<DriveCard> cards = new ArrayList<DriveCard>();
	private final Map<String, DriveItem> selected = new LinkedHashMap<String, DriveItem>();
	private String anchorId;

	private final BackgroundTaskService background = BackgroundTaskService.getInstance();
	private final PauseTransition releasePin = new PauseTransition(Duration.millis(500));

	private String folderId = ROOT;
	private String nextPageToken;
	private int loadSequence;
	private boolean loading;
	private volatile boolean disposed;
	private boolean trashView;
	private String searchText;
	private long userScrollAt;
	private double pinnedOffset = -1;
	private boolean correcting;

	@DataStorage(key = KEY_LAST_DIR)
	private String lastDownloadDirectory;

	private int moving;

	private interface ItemAction {
		void run(DriveItem item) throws Exception;
	}

	private record Crumb(String label, String folderId) {
	}

	private record DriveData(DrivePage page, List<DriveItem> path, String rootId) {
	}

	private record MoveResult(Set<String> moved, String error) {
	}

	@OnInit
	public void initialized() {
		myDriveToggle.setSelected(true);
		sectionGroup.selectedToggleProperty().addListener((observable, previous, current) -> {
			if (current == null && previous != null) {
				previous.setSelected(true);
			}
		});
		loadMoreButton.setFocusTraversable(false);
		scrollPane.setFitToWidth(true);
		releasePin.setOnFinished(event -> pinnedOffset = -1);
		scrollPane.addEventFilter(ScrollEvent.ANY, event -> markUserScroll());
		scrollPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> markUserScroll());
		scrollPane.addEventFilter(MouseEvent.MOUSE_PRESSED, this::markScrollBarDrag);
		scrollPane.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::markScrollBarDrag);
		scrollPane.vvalueProperty().addListener((observable, previous, current) -> onScrolled(current.doubleValue()));
		scrollPane.viewportBoundsProperty().addListener((observable, previous, current) -> reapplyPin());
		if (scrollPane.getContent() != null) {
			scrollPane.getContent().layoutBoundsProperty()
					.addListener((observable, previous, current) -> reapplyPin());
		}
		cardGrid.widthProperty().addListener((observable, previous, current) -> relayoutCards());
	}

	@Override
	public void onNavigate(Object arguments) {
		resetMode();
		if (arguments instanceof String id && !id.isBlank()) {
			folderId = id;
		}
		load(true);
	}

	@Override
	public void onResume() {
		disposed = false;
	}

	@Override
	public void dispose() {
		disposed = true;
	}

	@FXML
	private void onMyDrive() {
		resetMode();
		folderId = ROOT;
		load(true);
	}

	@FXML
	private void onShared() {
		resetMode();
		folderId = null;
		load(true);
	}

	@FXML
	private void onTrash() {
		resetMode();
		trashView = true;
		load(true);
	}

	@FXML
	private void onSearch() {
		String text = searchField.getText() == null ? "" : searchField.getText().trim();
		trashView = false;
		searchText = text.isEmpty() ? null : text;
		load(true);
	}

	private void resetMode() {
		trashView = false;
		searchText = null;
		searchField.clear();
	}

	private void updateModeUi() {
		setVisible(emptyTrashButton, trashView);
		emptyLabel.setText(trashView ? "Trash is empty" : searchText != null ? "No results found" : "This folder is empty");
	}

	@FXML
	private void onRefresh() {
		load(true);
	}

	@FXML
	private void onUpload() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Upload to Drive");
		if (lastDownloadDirectory != null && !lastDownloadDirectory.isBlank()) {
			File dir = new File(lastDownloadDirectory);
			if (dir.isDirectory()) {
				chooser.setInitialDirectory(canonical(dir));
			}
		}

		List<File> files = chooser.showOpenMultipleDialog(ApiTestMain.primaryStage());
		if (files == null || files.isEmpty()) {
			return;
		}

		rememberDirectory(files.get(0).toPath().getParent());

		if (folderId == null || trashView || searchText != null) {
			Notifier.info("Upload", "Pick a folder in My Drive to upload into");
			return;
		}
		String parent = ROOT.equals(folderId) ? null : folderId;

		for (File file : files) {
			Uploads.start(driveService, file.toPath(), parent, item -> addUploadedCard(item, parent));
		}
	}

	private void addUploadedCard(DriveItem item, String parent) {
		if (disposed || trashView || searchText != null || !isSameFolder(folderId, parent) || indexOf(item.id()) >= 0) {
			return;
		}
		runWithoutAutoLoad(() -> {
			List<DriveCard> created = DriveCard.append(cardGrid, Collections.singletonList(item), handlers());
			if (!created.isEmpty()) {
				cards.add(0, created.get(0));
				Node node = created.get(0).getRoot();
				cardGrid.getChildren().remove(node);
				cardGrid.getChildren().add(0, node);
			}
			setVisible(emptyLabel, cardGrid.getChildren().isEmpty());
			relayoutCards();
		});
		EventBus.publish(AppEvents.STATUS, cardGrid.getChildren().size() + " items in view");
	}

	private boolean isSameFolder(String current, String target) {
		if (ROOT.equals(current)) {
			return target == null || ROOT.equals(target);
		}
		return current != null && current.equals(target);
	}

	@FXML
	private void onLoadMore() {
		loadMore();
	}

	private void loadMore() {
		if (nextPageToken != null && !loading && !disposed) {
			load(false);
		}
	}

	private void load(boolean reset) {
		String folder = folderId;
		String query = searchText;
		boolean trash = trashView;
		String pageToken = reset ? null : nextPageToken;
		int sequence = ++loadSequence;
		loading = true;

		updateBusy();
		refreshButton.setDisable(true);
		loadMoreButton.setDisable(true);
		if (reset) {
			pinnedOffset = -1;
			releasePin.stop();
			nextPageToken = null;
			cardGrid.getChildren().clear();
			cards.clear();
			selected.clear();
			anchorId = null;
			updateModeUi();
			updateSelectionUi();
			setVisible(emptyLabel, false);
			setVisible(loadMoreButton, false);
		}
		log.debug("Loading Drive folder {} page {}", folder, pageToken);

		background.executeTask(() -> fetch(folder, query, trash, pageToken, reset), data -> {
			if (disposed || sequence != loadSequence) {
				return;
			}
			finishLoading();
			render(data, reset);
		}, error -> {
			if (disposed || sequence != loadSequence) {
				return;
			}
			finishLoading();
			log.error("Drive failed to load", error);
			Notifier.error("Could not load Google Drive", error.getMessage());
			EventBus.publish(AppEvents.STATUS, "Drive failed to load");
		});
	}

	private DriveData fetch(String folder, String query, boolean trash, String pageToken, boolean reset)
			throws Exception {
		DrivePage page;
		if (trash) {
			page = driveService.listTrash(pageToken);
		} else if (query != null) {
			page = driveService.search(query, pageToken);
		} else {
			page = folder == null ? driveService.listSharedWithMe(pageToken)
					: driveService.listFolder(folder, pageToken);
		}
		if (!reset) {
			return new DriveData(page, null, null);
		}
		if (trash || query != null || folder == null || ROOT.equals(folder)) {
			return new DriveData(page, new ArrayList<DriveItem>(), null);
		}
		return new DriveData(page, driveService.getPath(folder), driveService.getRootId());
	}

	private void render(DriveData data, boolean reset) {
		if (reset) {
			renderBreadcrumb(buildCrumbs(data));
		}
		cards.addAll(DriveCard.append(cardGrid, data.page().items(), handlers()));
		nextPageToken = data.page().nextPageToken();
		setVisible(loadMoreButton, nextPageToken != null);
		setVisible(emptyLabel, cardGrid.getChildren().isEmpty());
		EventBus.publish(AppEvents.STATUS, cardGrid.getChildren().size() + " items in view");
		Platform.runLater(this::relayoutCards);
	}

	private void relayoutCards() {
		Insets insets = cardGrid.getInsets();
		double available = cardGrid.getWidth() - insets.getLeft() - insets.getRight();
		if (available <= 0) {
			return;
		}
		double hgap = cardGrid.getHgap();
		int columns = Math.max(1, (int) Math.floor((available + hgap) / (CARD_MIN_WIDTH + hgap)));
		double cardWidth = Math.floor((available - (columns - 1) * hgap) / columns);

		for (Node node : cardGrid.getChildren()) {
			if (node instanceof Region region && Math.abs(region.getPrefWidth() - cardWidth) > 0.5) {
				region.setMinWidth(cardWidth);
				region.setPrefWidth(cardWidth);
				region.setMaxWidth(cardWidth);
			}
		}
	}

	private void download(DriveItem item) {
		if (item.folder()) {
			downloadZip(Collections.singletonList(item));
			return;
		}

		FileChooser chooser = new FileChooser();
		chooser.setTitle("Save file");
		chooser.setInitialFileName(driveService.downloadName(item));
		chooser.setInitialDirectory(initialDownloadDirectory());

		File file = chooser.showSaveDialog(ApiTestMain.primaryStage());
		if (file == null) {
			return;
		}

		rememberDirectory(file.toPath().getParent());
		Downloads.start(driveService, item, file.toPath());
	}

	private DriveCard.Handlers handlers() {
	    if (trashView) {
	        return new DriveCard.Handlers(this::open, null, null, this::download, null, null, this::delete,
	                this::restore, this::onSelect);
	    }
	    return new DriveCard.Handlers(this::open, this::openDefault, this::openWith, this::download, this::move,
	            this::rename, this::delete, null, this::onSelect);
	}

	private List<DriveItem> groupOf(DriveItem item) {
		boolean group = selected.containsKey(item.id()) && selected.size() > 1;
		return group ? new ArrayList<DriveItem>(selected.values()) : Collections.singletonList(item);
	}

	private List<DriveItem> editable(List<DriveItem> items) {
		List<DriveItem> result = items.stream().filter(item -> !item.shortcut()).toList();
		if (result.size() < items.size()) {
			Notifier.info("Shortcuts", "Shortcuts cannot be changed here");
		}
		return result;
	}

	private void move(DriveItem item) {
		moveItems(groupOf(item));
	}

	private void delete(DriveItem item) {
		deleteItems(groupOf(item));
	}

	private void restore(DriveItem item) {
		restoreItems(groupOf(item));
	}

	@FXML
	private void onMoveSelected() {
		if (!selected.isEmpty()) {
			moveItems(new ArrayList<DriveItem>(selected.values()));
		}
	}

	@FXML
	private void onDeleteSelected() {
		if (!selected.isEmpty()) {
			deleteItems(new ArrayList<DriveItem>(selected.values()));
		}
	}

	@FXML
	private void onRestoreSelected() {
		if (!selected.isEmpty()) {
			restoreItems(new ArrayList<DriveItem>(selected.values()));
		}
	}

	private void moveItems(List<DriveItem> items) {
		List<DriveItem> movable = items.stream().filter(item -> !item.shortcut()).toList();
		if (movable.size() < items.size()) {
			Notifier.info("Move", "Shortcuts cannot be moved");
		}
		if (movable.isEmpty()) {
			return;
		}
		double offset = currentOffset();
		Set<String> excluded = movable.stream().filter(DriveItem::folder).map(DriveItem::id)
				.collect(Collectors.toSet());
		FolderPicker.show(driveService, excluded, folderId, target -> runMove(movable, target, offset));
	}

	private void deleteItems(List<DriveItem> items) {
		List<DriveItem> targets = editable(items);
		if (targets.isEmpty()) {
			return;
		}
		double offset = currentOffset();
		boolean forever = trashView;
		String noun = targets.size() == 1 ? "\"" + targets.get(0).name() + "\"" : targets.size() + " items";
		boolean confirmed = forever
				? Prompt.confirm("Delete forever", noun + " will be permanently deleted. This cannot be undone.",
						"Delete forever")
				: Prompt.confirm("Move to Trash", "Move " + noun + " to Trash?", "Move to Trash");
		if (!confirmed) {
			return;
		}
		holdScroll(offset);
		if (forever) {
			runBatch(targets, "Deleting", "Deleted forever", item -> driveService.deleteForever(item.id()));
		} else {
			runBatch(targets, "Moving to Trash", "Moved to Trash", item -> driveService.trashItem(item.id()));
		}
	}

	private void restoreItems(List<DriveItem> items) {
		List<DriveItem> targets = editable(items);
		if (!targets.isEmpty()) {
			runBatch(targets, "Restoring", "Restored", item -> driveService.restoreItem(item.id()));
		}
	}

	private void runBatch(List<DriveItem> items, String verb, String title, ItemAction action) {
		int viewSequence = loadSequence;
		Set<String> ids = items.stream().map(DriveItem::id).collect(Collectors.toSet());
		startBusy(ids, verb + " " + items.size() + (items.size() == 1 ? " item\u2026" : " items\u2026"));
		background.executeTask(() -> {
			Set<String> succeeded = new HashSet<String>();
			String error = null;
			for (DriveItem item : items) {
				try {
					action.run(item);
					succeeded.add(item.id());
				} catch (Exception e) {
					log.error("{} failed for {}", verb, item.name(), e);
					error = e.getMessage();
				}
			}
			return new MoveResult(succeeded, error);
		}, result -> {
			finishMoving(ids);
			if (!result.moved().isEmpty() && !disposed && loadSequence == viewSequence) {
				removeCards(result.moved());
			}
			report(title, items.size(), result);
		}, error -> {
			finishMoving(ids);
			log.error("{} failed", verb, error);
			Notifier.error(verb + " failed", error.getMessage());
		});
	}

	private void report(String title, int total, MoveResult result) {
		int count = result.moved().size();
		if (count == total) {
			Notifier.success(title, count + (count == 1 ? " item" : " items"));
		} else if (count > 0) {
			Notifier.warning(title, count + " of " + total + " done. " + result.error());
		} else {
			Notifier.error(title + " failed", result.error());
		}
	}

	@FXML
	private void onEmptyTrash() {
		if (!Prompt.confirm("Empty Trash", "All items in Trash will be permanently deleted. This cannot be undone.",
				"Empty Trash")) {
			return;
		}
		Set<String> none = Collections.emptySet();
		startBusy(none, "Emptying Trash\u2026");
		background.executeTask(() -> {
			driveService.emptyTrash();
			return Boolean.TRUE;
		}, done -> {
			finishMoving(none);
			Notifier.success("Trash emptied", "All items were permanently deleted");
			if (trashView && !disposed) {
				load(true);
			}
		}, error -> {
			finishMoving(none);
			log.error("Empty Trash failed", error);
			Notifier.error("Could not empty Trash", error.getMessage());
		});
	}

	@FXML
	private void onNewFolder() {
		if (folderId == null || trashView || searchText != null) {
			Notifier.info("New folder", "Open a folder in My Drive first");
			return;
		}
		Optional<String> name = Prompt.text("New folder", "Name", "Untitled folder");
		if (name.isEmpty()) {
			return;
		}
		String parent = ROOT.equals(folderId) ? null : folderId;
		Set<String> none = Collections.emptySet();
		startBusy(none, "Creating folder\u2026");
		background.executeTask(() -> driveService.createFolder(name.get(), parent), created -> {
			finishMoving(none);
			addUploadedCard(created, parent);
			Notifier.success("Folder created", created.name());
		}, error -> {
			finishMoving(none);
			log.error("Could not create folder", error);
			Notifier.error("Could not create folder", error.getMessage());
		});
	}

	private void rename(DriveItem item) {
		if (item.shortcut()) {
			Notifier.info("Shortcuts", "Shortcuts cannot be changed here");
			return;
		}
		Optional<String> name = Prompt.text("Rename", "Name", item.name());
		if (name.isEmpty() || name.get().equals(item.name())) {
			return;
		}
		Set<String> ids = Collections.singleton(item.id());
		startBusy(ids, "Renaming\u2026");
		background.executeTask(() -> driveService.renameItem(item.id(), name.get()), updated -> {
			finishMoving(ids);
			if (!disposed) {
				replaceCard(item.id(), updated);
			}
			Notifier.success("Renamed", updated.name());
		}, error -> {
			finishMoving(ids);
			log.error("Could not rename {}", item.name(), error);
			Notifier.error("Could not rename", error.getMessage());
		});
	}

	private void replaceCard(String id, DriveItem updated) {
		int index = indexOf(id);
		if (index < 0) {
			return;
		}
		DriveCard old = cards.get(index);
		int position = cardGrid.getChildren().indexOf(old.getRoot());
		DriveCard replacement = DriveCard.append(cardGrid, Collections.singletonList(updated), handlers()).get(0);
		Node node = replacement.getRoot();
		cardGrid.getChildren().remove(node);
		cardGrid.getChildren().set(position, node);
		cards.set(index, replacement);
		if (selected.containsKey(id)) {
			selected.put(id, updated);
		}
		refreshSelection();
		relayoutCards();
	}

	private void runMove(List<DriveItem> items, FolderPicker.Choice target, double offset) {
		holdScroll(offset);
		String source = folderId;
		Set<String> ids = items.stream().map(DriveItem::id).collect(Collectors.toSet());
		startBusy(ids, "Moving " + items.size() + (items.size() == 1 ? " item\u2026" : " items\u2026"));
		background.executeTask(() -> {
			Set<String> moved = new HashSet<String>();
			String error = null;
			for (DriveItem item : items) {
				try {
					driveService.moveItem(item.id(), target.id());
					moved.add(item.id());
				} catch (Exception e) {
					log.error("Could not move {}", item.name(), e);
					error = e.getMessage();
				}
			}
			return new MoveResult(moved, error);
		}, result -> {
			finishMoving(ids);
			applyMove(items, result.moved(), source, target);
			int moved = result.moved().size();
			if (moved == items.size()) {
				Notifier.success("Moved", moved + (moved == 1 ? " item" : " items") + " moved to " + target.name());
			} else if (moved > 0) {
				Notifier.warning("Partly moved",
						moved + " of " + items.size() + " moved to " + target.name() + ". " + result.error());
			} else {
				Notifier.error("Move failed", result.error());
			}
		}, error -> {
			finishMoving(ids);
			log.error("Move failed", error);
			Notifier.error("Move failed", error.getMessage());
		});
	}

	private void applyMove(List<DriveItem> items, Set<String> moved, String source, FolderPicker.Choice target) {
		if (moved.isEmpty() || disposed) {
			return;
		}
		if (Objects.equals(folderId, source)) {
			removeCards(moved);
			return;
		}
		for (DriveItem item : items) {
			if (moved.contains(item.id())) {
				addUploadedCard(item, target.id());
			}
		}
	}

	private void removeCards(Set<String> ids) {
		runWithoutAutoLoad(() -> {
			cards.removeIf(card -> {
				String id = card.getItem().id();
				if (!ids.contains(id)) {
					return false;
				}
				cardGrid.getChildren().remove(card.getRoot());
				selected.remove(id);
				return true;
			});
			refreshSelection();
			setVisible(emptyLabel, cardGrid.getChildren().isEmpty());
			relayoutCards();
		});
		EventBus.publish(AppEvents.STATUS, cardGrid.getChildren().size() + " items in view");
	}

	private void runWithoutAutoLoad(Runnable mutation) {
		double offset = pinnedOffset >= 0 ? pinnedOffset : currentOffset();
		mutation.run();
		Parent parent = scrollPane.getParent();
		if (parent != null) {
			parent.applyCss();
			parent.layout();
		}
		holdScroll(offset);
	}

	private void onScrolled(double value) {
		if (correcting) {
			return;
		}
		if (pinnedOffset >= 0 && !userScrolling()) {
			reapplyPin();
			return;
		}
		if (userScrolling() && value >= LOAD_THRESHOLD && scrollableHeight() > 0) {
			loadMore();
		}
	}

	private void markUserScroll() {
		userScrollAt = System.currentTimeMillis();
		pinnedOffset = -1;
		releasePin.stop();
	}

	private boolean userScrolling() {
		return System.currentTimeMillis() - userScrollAt < USER_SCROLL_WINDOW_MS;
	}

	private void markScrollBarDrag(MouseEvent event) {
		if (!(event.getTarget() instanceof Node target)) {
			return;
		}
		for (Node current = target; current != null && current != scrollPane; current = current.getParent()) {
			if (current.getStyleClass().contains(SCROLL_BAR)) {
				markUserScroll();
				return;
			}
		}
	}

	private void holdScroll(double offset) {
		pinnedOffset = offset;
		releasePin.playFromStart();
		reapplyPin();
	}

	private void reapplyPin() {
		if (pinnedOffset < 0 || correcting || userScrolling()) {
			return;
		}
		correcting = true;
		try {
			restoreOffset(pinnedOffset);
		} finally {
			correcting = false;
		}
	}

	private double currentOffset() {
		return scrollPane.getVvalue() * scrollableHeight();
	}

	private double scrollableHeight() {
		Node content = scrollPane.getContent();
		if (content == null) {
			return 0;
		}
		return Math.max(0, content.getLayoutBounds().getHeight() - scrollPane.getViewportBounds().getHeight());
	}

	private void restoreOffset(double offset) {
		double scrollable = scrollableHeight();
		scrollPane.setVvalue(scrollable <= 0 ? 0 : Math.max(0, Math.min(1, offset / scrollable)));
	}

	private void onSelect(DriveItem item, DriveCard.Select mode) {
		switch (mode) {
		case ONLY -> {
			selected.clear();
			selected.put(item.id(), item);
			anchorId = item.id();
		}
		case TOGGLE -> {
			if (selected.remove(item.id()) == null) {
				selected.put(item.id(), item);
			}
			anchorId = item.id();
		}
		case RANGE -> selectRange(item);
		}
		refreshSelection();
	}

	private void selectRange(DriveItem item) {
		int from = indexOf(anchorId == null ? item.id() : anchorId);
		int to = indexOf(item.id());
		selected.clear();
		if (from < 0 || to < 0) {
			selected.put(item.id(), item);
			anchorId = item.id();
			return;
		}
		for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
			DriveItem current = cards.get(i).getItem();
			selected.put(current.id(), current);
		}
	}

	private int indexOf(String id) {
		for (int i = 0; i < cards.size(); i++) {
			if (cards.get(i).getItem().id().equals(id)) {
				return i;
			}
		}
		return -1;
	}

	private void refreshSelection() {
		for (DriveCard card : cards) {
			card.setSelected(selected.containsKey(card.getItem().id()));
		}
		updateSelectionUi();
	}

	private void updateSelectionUi() {
		boolean active = !selected.isEmpty();
		setVisible(selectionBar, active);
		selectionLabel.setText(selected.size() + (selected.size() == 1 ? " item selected" : " items selected"));
		boolean zip = selected.size() > 1 || selected.values().stream().anyMatch(DriveItem::folder);
		downloadSelectedButton.setText(zip ? "Download as zip" : "Download");
		setVisible(restoreSelectedButton, trashView);
		setVisible(moveSelectedButton, !trashView);
		deleteSelectedButton.setText(trashView ? "Delete forever" : "Move to Trash");
		if (active) {
			if (!cardGrid.getStyleClass().contains(SELECTION_ACTIVE)) {
				cardGrid.getStyleClass().add(SELECTION_ACTIVE);
			}
		} else {
			cardGrid.getStyleClass().remove(SELECTION_ACTIVE);
		}
	}

	@FXML
	private void onClearSelection() {
		selected.clear();
		anchorId = null;
		refreshSelection();
	}

	@FXML
	private void onDownloadSelected() {
		List<DriveItem> items = new ArrayList<DriveItem>(selected.values());
		if (items.isEmpty()) {
			return;
		}
		if (items.size() == 1 && !items.get(0).folder()) {
			download(items.get(0));
			return;
		}
		downloadZip(items);
	}

	private void downloadZip(List<DriveItem> items) {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Save zip");
		chooser.setInitialFileName(zipName(items));
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zip archive", "*.zip"));
		chooser.setInitialDirectory(initialDownloadDirectory());

		File file = chooser.showSaveDialog(ApiTestMain.primaryStage());
		if (file == null) {
			return;
		}
		Path target = file.toPath();
		if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
			target = target.resolveSibling(target.getFileName() + ".zip");
		}
		rememberDirectory(target.getParent());
		ZipDownloads.start(driveService, items, target);
	}

	private String zipName(List<DriveItem> items) {
		if (items.size() == 1) {
			return items.get(0).name().replaceAll("[\\\\/:*?\"<>|]", "_") + ".zip";
		}
		return "Drive " + LocalDate.now() + ".zip";
	}

	private File initialDownloadDirectory() {
		if (lastDownloadDirectory != null && !lastDownloadDirectory.isBlank()) {
			File dir = new File(lastDownloadDirectory);
			if (dir.isDirectory()) {
				return canonical(dir);
			}
		}
		return systemDownloadsFolder();
	}

	private void rememberDirectory(Path dir) {
		if (dir == null) {
			return;
		}
		DataStorageUtil.save(KEY_LAST_DIR, dir.toAbsolutePath().toString());
	}

	private static File systemDownloadsFolder() {
		if (isWindows()) {
			File f = readWindowsDownloadsFolder();
			if (f != null && f.isDirectory()) {
				return canonical(f);
			}
		}
		File f = new File(System.getProperty("user.home"), "Downloads");
		if (f.isDirectory()) {
			return canonical(f);
		}
		return new File(System.getProperty("user.home"));
	}

	private static File canonical(File f) {
		try {
			return f.getCanonicalFile();
		} catch (IOException e) {
			return f.getAbsoluteFile();
		}
	}

	private static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
	}

	private static File readWindowsDownloadsFolder() {
		try {
			Process p = new ProcessBuilder(
					"powershell", "-NoProfile", "-Command",
					"(New-Object -ComObject Shell.Application).NameSpace('shell:Downloads').Self.Path")
				.redirectErrorStream(true)
				.start();

			try (BufferedReader r = new BufferedReader(
					new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String path = r.readLine();
				if (path != null && !path.isBlank()) {
					return new File(path.trim());
				}
			}
			p.waitFor();
		} catch (Exception e) {
			log.debug("Could not resolve Windows Downloads folder", e);
		}
		return null;
	}

	private List<Crumb> buildCrumbs(DriveData data) {
		if (trashView) {
			trashToggle.setSelected(true);
			return new ArrayList<Crumb>(List.of(new Crumb("Trash", null)));
		}
		if (searchText != null) {
			return new ArrayList<Crumb>(List.of(new Crumb("Results for \"" + searchText + "\"", null)));
		}
		List<Crumb> crumbs = new ArrayList<Crumb>();
		if (folderId == null) {
			crumbs.add(new Crumb("Shared with me", null));
		} else if (ROOT.equals(folderId)) {
			crumbs.add(new Crumb("My Drive", ROOT));
		} else {
			List<DriveItem> path = data.path();
			boolean inMyDrive = !path.isEmpty() && path.get(0).id().equals(data.rootId());
			crumbs.add(inMyDrive ? new Crumb("My Drive", ROOT) : new Crumb("Shared with me", null));
			for (int i = inMyDrive ? 1 : 0; i < path.size(); i++) {
				crumbs.add(new Crumb(path.get(i).name(), path.get(i).id()));
			}
		}
		if (crumbs.get(0).folderId() == null) {
			sharedToggle.setSelected(true);
		} else {
			myDriveToggle.setSelected(true);
		}
		return crumbs;
	}

	private void renderBreadcrumb(List<Crumb> crumbs) {
		breadcrumbBar.getChildren().clear();
		for (int i = 0; i < crumbs.size(); i++) {
			Crumb crumb = crumbs.get(i);
			if (i > 0) {
				Label separator = new Label("\u203a");
				separator.getStyleClass().add("crumb-sep");
				breadcrumbBar.getChildren().add(separator);
			}
			if (i == crumbs.size() - 1) {
				Label current = new Label(crumb.label());
				current.getStyleClass().add("crumb-current");
				breadcrumbBar.getChildren().add(current);
			} else {
				Button link = new Button(crumb.label());
				link.getStyleClass().add("crumb");
				link.setOnAction(event -> {
					resetMode();
					folderId = crumb.folderId();
					load(true);
				});
				breadcrumbBar.getChildren().add(link);
			}
		}
	}

	private void open(DriveItem item) {
		if (item.folder()) {
			if (trashView) {
				Notifier.info("Trash", "Restore the folder to open it");
				return;
			}
			resetMode();
			folderId = item.id();
			load(true);
			return;
		}
		if (item.link() == null || item.link().isBlank() || ApiTestMain.hostServices() == null) {
			Notifier.warning("Cannot open file", item.name());
			return;
		}
		ApiTestMain.hostServices().showDocument(item.link());
	}

	private void finishLoading() {
		loading = false;
		refreshButton.setDisable(false);
		loadMoreButton.setDisable(false);
		updateBusy();
	}

	private void updateBusy() {
		progress.setVisible(loading || moving > 0);
		selectionBar.setDisable(moving > 0);
	}

	private void setVisible(Node node, boolean visible) {
		node.setVisible(visible);
		node.setManaged(visible);
	}

	private void startBusy(Set<String> ids, String message) {
		moving++;
		setBusy(ids, true);
		updateBusy();
		EventBus.publish(AppEvents.STATUS, message);
	}

	private void finishMoving(Set<String> ids) {
		moving--;
		setBusy(ids, false);
		updateBusy();
	}

	private void setBusy(Set<String> ids, boolean busy) {
		for (DriveCard card : cards) {
			if (ids.contains(card.getItem().id())) {
				card.setBusy(busy);
			}
		}
	}
	
	private void openDefault(DriveItem item) {
	    openLocal(item, false);
	}

	private void openWith(DriveItem item) {
	    openLocal(item, true);
	}

	private void openLocal(DriveItem item, boolean choose) {
	    if (item.folder()) {
	        open(item);
	        return;
	    }
	    String safeName = driveService.downloadName(item).replaceAll("[\\\\/:*?\"<>|]", "_");
	    Set<String> ids = Collections.singleton(item.id());
	    startBusy(ids, "Downloading " + item.name() + "\u2026");
	    background.executeTask(() -> {
	        Path folder = Files.createTempDirectory("drive-open-");
	        Path target = folder.resolve(safeName);
	        DriveDownload download = driveService.openDownload(item);
	        try (InputStream in = download.stream()) {
	            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
	        }
	        if (!choose) {
	            openWithSystem(target);
	        }
	        return target;
	    }, path -> {
	        finishMoving(ids);
	        if (choose) {
	            chooseAppAndOpen(path);
	        }
	    }, error -> {
	        finishMoving(ids);
	        log.error("Could not open {}", item.name(), error);
	        Notifier.error("Could not open file", error.getMessage());
	    });
	}

	private void openWithSystem(Path path) throws IOException {
	    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
	        Desktop.getDesktop().open(path.toFile());
	        return;
	    }
	    if (isWindows()) {
	        new ProcessBuilder("cmd", "/c", "start", "", path.toAbsolutePath().toString()).start();
	    } else if (isMac()) {
	        new ProcessBuilder("open", path.toAbsolutePath().toString()).start();
	    } else {
	        new ProcessBuilder("xdg-open", path.toAbsolutePath().toString()).start();
	    }
	}

	private void chooseAppAndOpen(Path path) {
	    String file = path.toAbsolutePath().toString();
	    try {
	        if (isWindows()) {
	            String quoted = file.replace("'", "''");
	            new ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command",
	                    "Start-Process -FilePath '" + quoted + "' -Verb OpenAs")
	                .redirectErrorStream(true)
	                .start();
	            return;
	        }
	        FileChooser chooser = new FileChooser();
	        chooser.setTitle("Choose an application");
	        File start = new File(isMac() ? "/Applications" : "/usr/bin");
	        if (start.isDirectory()) {
	            chooser.setInitialDirectory(start);
	        }
	        File app = chooser.showOpenDialog(ApiTestMain.primaryStage());
	        if (app == null) {
	            return;
	        }
	        if (isMac()) {
	            new ProcessBuilder("open", "-a", app.getAbsolutePath(), file).start();
	        } else {
	            new ProcessBuilder(app.getAbsolutePath(), file).start();
	        }
	    } catch (Exception e) {
	        log.error("Could not launch application", e);
	        Notifier.error("Could not open file", String.valueOf(e.getMessage()));
	    }
	}

	private static boolean isMac() {
	    return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("mac");
	}
}