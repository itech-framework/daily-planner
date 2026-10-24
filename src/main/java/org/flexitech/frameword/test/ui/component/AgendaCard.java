package org.flexitech.frameword.test.ui.component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

import org.flexitech.frameword.test.ApiTestMain;
import org.flexitech.frameword.test.model.AgendaItem;

import io.github.itech_framework.core.annotations.constructor.DefaultConstructor;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.components.FxComponent;
import io.github.itech_framework.java_fx.annotations.components.Root;
import io.github.itech_framework.java_fx.loader.FxComponentLoader;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

@FxComponent("/views/components/agenda-card.fxml")
public class AgendaCard {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, d MMM");

	@FXML
	@Root
	private HBox root;

	@FXML
	private CheckBox doneBox;

	@FXML
	private Label titleLabel;

	@FXML
	private Label metaLabel;

	@FXML
	private Label detailLabel;

	@FXML
	private Hyperlink openLink;

	private final AgendaItem item;
	private final Consumer<AgendaItem> onToggle;

	@DefaultConstructor
	public AgendaCard() {
		this(null, null);
	}

	public AgendaCard(AgendaItem item, Consumer<AgendaItem> onToggle) {
		this.item = item;
		this.onToggle = onToggle;
	}

	public HBox getRoot() {
		return root;
	}

	public static void fill(VBox box, List<AgendaItem> items, Consumer<AgendaItem> onToggle, String emptyText) {
		box.getChildren().clear();
		if (items.isEmpty()) {
			if (!emptyText.isEmpty()) {
				Label empty = new Label(emptyText);
				empty.getStyleClass().add("empty-note");
				box.getChildren().add(empty);
			}
			return;
		}
		for (AgendaItem item : items) {
			box.getChildren().add(FxComponentLoader.load(AgendaCard.class, item, onToggle).getRoot());
		}
	}

	@OnInit
	private void bind() {
		if (item == null) {
			return;
		}
		boolean task = item.type() == AgendaItem.Type.TASK;
		boolean overdue = task && !item.done() && item.due() != null && item.due().isBefore(LocalDate.now());

		titleLabel.setText(item.title());
		metaLabel.setText(describe(task));
		if (overdue) {
			metaLabel.getStyleClass().add("overdue-text");
			root.getStyleClass().add("overdue");
		}
		if (task) {
			root.getStyleClass().add("task");
			doneBox.setVisible(onToggle != null);
			doneBox.setManaged(onToggle != null);
			doneBox.setSelected(item.done());
			if (item.done()) {
				titleLabel.getStyleClass().add("done");
			}
		}
		if (item.detail() != null && !item.detail().isBlank()) {
			detailLabel.setText(item.detail());
			detailLabel.setVisible(true);
			detailLabel.setManaged(true);
		}
		if (item.link() != null && !item.link().isBlank()) {
			openLink.setVisible(true);
			openLink.setManaged(true);
		}
	}

	@FXML
	private void onToggle() {
		doneBox.setDisable(true);
		onToggle.accept(item.withDone(doneBox.isSelected()));
	}

	@FXML
	private void onOpen() {
		if (ApiTestMain.hostServices() != null) {
			ApiTestMain.hostServices().showDocument(item.link());
		}
	}

	private String describe(boolean task) {
		if (task) {
			String list = item.listTitle() == null ? "" : item.listTitle() + "  ·  ";
			return list + (item.due() == null ? "No due date" : "Due " + DAY.format(item.due()));
		}
		if (item.allDay()) {
			return "All day";
		}
		if (item.start() == null) {
			return "";
		}
		String end = item.end() == null ? "" : " – " + TIME.format(item.end());
		return TIME.format(item.start()) + end;
	}
}