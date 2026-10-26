package org.flexitech.framework.products.daily_planner.ui.component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.flexitech.framework.products.daily_planner.model.DriveItem;
import org.flexitech.framework.products.daily_planner.util.DriveFileUtil;

import io.github.itech_framework.core.annotations.constructor.DefaultConstructor;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.components.FxComponent;
import io.github.itech_framework.java_fx.annotations.components.Root;
import io.github.itech_framework.java_fx.loader.FxComponentLoader;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;

@FxComponent("/views/components/drive-card.fxml")
public class DriveCard {

	private static final String SELECTED = "drive-card-selected";
	private static final String SEPARATOR = " \u00b7 ";
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

	public enum Select {
		ONLY, TOGGLE, RANGE
	}

	public record Handlers(Consumer<DriveItem> open, Consumer<DriveItem> openDefault, Consumer<DriveItem> openWith,
	        Consumer<DriveItem> download, Consumer<DriveItem> move, Consumer<DriveItem> rename,
	        Consumer<DriveItem> delete, Consumer<DriveItem> restore, BiConsumer<DriveItem, Select> select) {
	}

	@FXML
	@Root
	private StackPane root;

	@FXML
	private Label badgeLabel;

	@FXML
	private Label nameLabel;

	@FXML
	private Label metaLabel;

	@FXML
	private Button menuButton;

	@FXML
	private CheckBox selectBox;
	
	@FXML
	private StackPane busyPane;

	private final DriveItem item;
	private final Handlers handlers;

	private ContextMenu menu;
	private boolean rangeClick;

	@DefaultConstructor
	public DriveCard() {
		this(null, null);
	}

	public DriveCard(DriveItem item, Handlers handlers) {
		this.item = item;
		this.handlers = handlers == null ? new Handlers(null, null, null, null, null, null, null, null, null) : handlers;
	}

	public StackPane getRoot() {
		return root;
	}

	public DriveItem getItem() {
		return item;
	}

	public static List<DriveCard> append(FlowPane grid, List<DriveItem> items, Handlers handlers) {
		List<DriveCard> created = new ArrayList<DriveCard>(items.size());
		for (DriveItem item : items) {
			DriveCard card = FxComponentLoader.load(DriveCard.class, item, handlers);
			grid.getChildren().add(card.getRoot());
			created.add(card);
		}
		return created;
	}

	public void setSelected(boolean selected) {
		if (selected) {
			if (!root.getStyleClass().contains(SELECTED)) {
				root.getStyleClass().add(SELECTED);
			}
		} else {
			root.getStyleClass().remove(SELECTED);
		}
		selectBox.setSelected(selected);
	}
	
	public void setBusy(boolean busy) {
		busyPane.setVisible(busy);
	}

	@FXML
	private void onBusyClick(MouseEvent event) {
		event.consume();
	}

	@OnInit
	private void bind() {
		if (item == null) {
			return;
		}
		badgeLabel.setText(DriveFileUtil.badge(item.kind()));
		badgeLabel.getStyleClass().add("drive-kind-" + item.kind());
		nameLabel.setText(item.name());
		metaLabel.setText(meta());
		Tooltip.install(root, new Tooltip(item.name()));
		menu = buildMenu();
	}

	@FXML
	private void onClick(MouseEvent event) {
		if (item == null) {
			return;
		}
		if (event.getClickCount() == 2 && handlers.open() != null) {
			handlers.open().accept(item);
		}
	}

	@FXML
	private void onCheckPressed(MouseEvent event) {
		rangeClick = event.isShiftDown();
	}

	@FXML
	private void onCheck() {
		if (handlers.select() == null) {
			return;
		}
		handlers.select().accept(item, rangeClick ? Select.RANGE : Select.TOGGLE);
		rangeClick = false;
	}

	@FXML
	private void onCheckClick(MouseEvent event) {
		event.consume();
	}

	@FXML
	private void onMenu() {
		if (menu == null) {
			return;
		}
		if (menu.isShowing()) {
			menu.hide();
		} else {
			menu.show(menuButton, Side.BOTTOM, 0, 0);
		}
	}

	@FXML
	private void onMenuClick(MouseEvent event) {
		event.consume();
	}

	private ContextMenu buildMenu() {
		boolean trash = handlers.restore() != null;
		ContextMenu contextMenu = new ContextMenu();
		contextMenu.getItems().add(actionItem("Open", handlers.open()));
		contextMenu.getItems().add(actionItem("Open in browser", handlers.open()));
		if (!item.folder() && handlers.openDefault() != null) {
		    contextMenu.getItems().add(actionItem("Open with default app", handlers.openDefault()));
		}
		if (!item.folder() && handlers.openWith() != null) {
		    contextMenu.getItems().add(actionItem("Open with\u2026", handlers.openWith()));
		}
		contextMenu.getItems().add(actionItem("Download", handlers.download()));
		if (trash) {
			contextMenu.getItems().add(actionItem("Restore", handlers.restore()));
		}
		if (handlers.move() != null) {
			contextMenu.getItems().add(actionItem("Move to\u2026", handlers.move()));
		}
		if (handlers.rename() != null) {
			contextMenu.getItems().add(actionItem("Rename\u2026", handlers.rename()));
		}
		contextMenu.getItems().add(actionItem(trash ? "Delete forever" : "Move to Trash", handlers.delete()));
		Label sizeLabel = new Label(sizeText());
		sizeLabel.getStyleClass().add("muted");
		contextMenu.getItems().addAll(new SeparatorMenuItem(), new CustomMenuItem(sizeLabel, false));
		return contextMenu;
	}

	private MenuItem actionItem(String text, Consumer<DriveItem> action) {
		MenuItem menuItem = new MenuItem(text);
		if (action == null) {
			menuItem.setDisable(true);
		} else {
			menuItem.setOnAction(e -> action.accept(item));
		}
		return menuItem;
	}

	private String sizeText() {
		if (item.folder()) {
			return "Folder";
		}
		return item.size() > 0 ? "Size: " + formatSize(item.size()) : "Size: -";
	}

	private String meta() {
		String date = item.modified() == null ? "" : DATE.format(item.modified());
		if (item.folder()) {
			return item.owner() == null ? date : item.owner();
		}
		String size = item.size() > 0 ? formatSize(item.size()) : "";
		if (size.isEmpty()) {
			return date;
		}
		return date.isEmpty() ? size : size + SEPARATOR + date;
	}

	private String formatSize(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		double value = bytes / 1024.0;
		String[] units = { "KB", "MB", "GB", "TB" };
		int unit = 0;
		while (value >= 1024 && unit < units.length - 1) {
			value /= 1024;
			unit++;
		}
		return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
	}
}