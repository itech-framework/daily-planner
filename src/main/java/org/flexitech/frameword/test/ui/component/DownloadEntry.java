package org.flexitech.frameword.test.ui.component;

import java.util.Locale;
import java.util.function.Consumer;

import io.github.itech_framework.core.annotations.constructor.DefaultConstructor;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.components.FxComponent;
import io.github.itech_framework.java_fx.annotations.components.Root;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

@FxComponent("/views/components/download-entry.fxml")
public class DownloadEntry {

	private static final String SEPARATOR = " \u00b7 ";

	public enum Direction {
		DOWNLOAD("\u2193", "downloaded"), UPLOAD("\u2191", "uploaded");

		private final String arrow;
		private final String verb;

		Direction(String arrow, String verb) {
			this.arrow = arrow;
			this.verb = verb;
		}

		public String arrow() {
			return arrow;
		}

		public String verb() {
			return verb;
		}
	}

	@FXML
	@Root
	private VBox root;

	@FXML
	private Label directionLabel;

	@FXML
	private Label nameLabel;

	@FXML
	private Label statusLabel;

	@FXML
	private Label timeLabel;

	@FXML
	private ProgressBar bar;

	@FXML
	private Button cancelButton;

	private final String fileName;
	private final Direction direction;
	private final Consumer<DownloadEntry> onClose;
	private volatile boolean cancelled;

	@DefaultConstructor
	public DownloadEntry() {
		this(null, Direction.DOWNLOAD, null);
	}

	public DownloadEntry(String fileName, Direction direction, Consumer<DownloadEntry> onClose) {
		this.fileName = fileName;
		this.direction = direction;
		this.onClose = onClose;
	}

	public VBox getRoot() {
		return root;
	}

	public boolean isCancelled() {
		return cancelled;
	}

	public void close() {
		if (onClose != null) {
			onClose.accept(this);
		}
	}

	public void update(long done, long total, double speed, long etaSeconds) {
		if (cancelled) {
			return;
		}
		if (total > 0) {
			double fraction = Math.min(1.0, (double) done / total);
			bar.setProgress(fraction);
			statusLabel.setText(formatSize(done) + " of " + formatSize(total) + SEPARATOR + Math.round(fraction * 100) + "%");
		} else {
			bar.setProgress(-1);
			statusLabel.setText(formatSize(done) + " " + direction.verb());
		}
		if (speed <= 0) {
			timeLabel.setText("Calculating\u2026");
		} else if (etaSeconds >= 0) {
			timeLabel.setText(formatSize((long) speed) + "/s" + SEPARATOR + formatDuration(etaSeconds) + " left");
		} else {
			timeLabel.setText(formatSize((long) speed) + "/s");
		}
	}

	@OnInit
	private void bind() {
		if (fileName == null) {
			return;
		}
		directionLabel.setText(direction.arrow());
		nameLabel.setText(fileName);
		Tooltip.install(nameLabel, new Tooltip(fileName));
	}

	@FXML
	private void onCancel() {
		cancelled = true;
		cancelButton.setDisable(true);
		statusLabel.setText("Cancelling\u2026");
		timeLabel.setText("");
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

	private String formatDuration(long seconds) {
		if (seconds < 60) {
			return seconds + " s";
		}
		if (seconds < 3600) {
			return (seconds / 60) + " min " + (seconds % 60) + " s";
		}
		return (seconds / 3600) + " h " + ((seconds % 3600) / 60) + " min";
	}
}