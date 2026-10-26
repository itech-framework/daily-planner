package org.flexitech.framework.products.daily_planner.ui.component;

import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import org.flexitech.framework.products.daily_planner.model.DriveItem;
import org.flexitech.framework.products.daily_planner.util.DriveFileUtil;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public final class DriveRow {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM");

	private DriveRow() {
	}

	public static HBox create(DriveItem item, Consumer<DriveItem> onOpen) {
		Label badge = new Label(DriveFileUtil.badge(item.kind()));
		badge.getStyleClass().addAll("drive-badge", "drive-kind-" + item.kind());
		badge.setMinWidth(60);
		badge.setAlignment(Pos.CENTER);

		Label name = new Label(item.name());
		name.setMinWidth(0);
		name.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(name, Priority.ALWAYS);

		Label when = new Label(item.modified() == null ? "" : DATE.format(item.modified()));
		when.getStyleClass().add("muted");

		HBox row = new HBox(10, badge, name, when);
		row.setAlignment(Pos.CENTER_LEFT);
		row.getStyleClass().add("drive-row");
		row.setOnMouseClicked(event -> onOpen.accept(item));
		Tooltip.install(row, new Tooltip(item.name()));
		return row;
	}
}