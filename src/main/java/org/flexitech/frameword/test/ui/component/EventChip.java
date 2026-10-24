package org.flexitech.frameword.test.ui.component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import org.flexitech.frameword.test.model.AgendaItem;
import org.flexitech.frameword.test.ui.calendar.CalendarMath;

import io.github.itech_framework.core.annotations.constructor.DefaultConstructor;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.components.FxComponent;
import io.github.itech_framework.java_fx.annotations.components.Root;
import io.github.itech_framework.java_fx.loader.FxComponentLoader;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

@FxComponent("/views/components/event-chip.fxml")
public class EventChip {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, d MMM");
	private static final String BOX_OPEN = "\u2610 ";
	private static final String BOX_DONE = "\u2611 ";
	private static final String DASH = " \u2013 ";

	@FXML
	@Root
	private VBox root;

	@FXML
	private Label titleLabel;

	@FXML
	private Label timeLabel;

	private final AgendaItem item;
	private final boolean showTime;
	private final Consumer<AgendaItem> onOpen;

	@DefaultConstructor
	public EventChip() {
		this(null, Boolean.FALSE, null);
	}

	public EventChip(AgendaItem item, Boolean showTime, Consumer<AgendaItem> onOpen) {
		this.item = item;
		this.showTime = Boolean.TRUE.equals(showTime);
		this.onOpen = onOpen;
	}

	public static Region create(AgendaItem item, boolean showTime, Consumer<AgendaItem> onOpen) {
		return FxComponentLoader.load(EventChip.class, item, showTime, onOpen).root;
	}

	@OnInit
	private void bind() {
		if (item == null) {
			return;
		}
		boolean task = item.type() == AgendaItem.Type.TASK;
		boolean overdue = task && !item.done() && item.due() != null && item.due().isBefore(LocalDate.now());

		root.getStyleClass().add(task ? "chip-task" : item.allDay() ? "chip-allday" : "chip-event");
		if (overdue) {
			root.getStyleClass().add("chip-overdue");
		}
		if (task && item.done()) {
			root.getStyleClass().add("chip-done");
		}

		String prefix = task ? (item.done() ? BOX_DONE : BOX_OPEN) : "";
		boolean timed = !task && !item.allDay() && item.start() != null;

		if (showTime && timed) {
			titleLabel.setText(item.title());
			timeLabel.setText(range());
			timeLabel.setVisible(true);
			timeLabel.setManaged(true);
		} else if (timed) {
			titleLabel.setText(TIME.format(item.start()) + "  " + item.title());
		} else {
			titleLabel.setText(prefix + item.title());
		}

		Tooltip.install(root, new Tooltip(tooltipText(task)));
		root.setOnMouseClicked(event -> {
			if (onOpen != null) {
				onOpen.accept(item);
			}
			event.consume();
		});
	}

	private String range() {
		if (item.end() == null) {
			return TIME.format(item.start());
		}
		return TIME.format(item.start()) + DASH + TIME.format(CalendarMath.effectiveEnd(item));
	}

	private String tooltipText(boolean task) {
		if (task) {
			return item.title() + "\n" + (item.due() == null ? "No due date" : "Due " + DAY.format(item.due()));
		}
		if (item.allDay() || item.start() == null) {
			return item.title() + "\nAll day";
		}
		return item.title() + "\n" + range();
	}
}