package org.flexitech.framework.products.daily_planner.util;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.itech_framework.java_fx.ui.toast.Toast;

public final class Transfer {

	public enum Direction {
		DOWNLOAD("\u2193"), UPLOAD("\u2191");

		private final String symbol;

		Direction(String symbol) {
			this.symbol = symbol;
		}
	}

	private static final String[] UNITS = { "B", "KB", "MB", "GB", "TB" };

	private final Direction direction;
	private final Toast toast;
	private final AtomicBoolean closed = new AtomicBoolean();
	private volatile boolean cancelled;

	Transfer(String fileName, Direction direction) {
		this.direction = direction;
		this.toast = DownloadPanel.toastBuilder()
				.title(fileName)
				.message(direction.symbol + " Starting\u2026")
				.onDismiss(() -> {
					cancelled = true;
					closed.set(true);
				})
				.build();
		toast.show();
	}

	public boolean isCancelled() {
		return cancelled;
	}

	public void update(long done, long total, double speed, long eta) {
		if (closed.get()) {
			return;
		}
		toast.updateProgress(total > 0 ? Math.min(1.0, (double) done / total) : -1);
		toast.updateMessage(describe(done, total, speed, eta));
	}

	public void close() {
		if (closed.compareAndSet(false, true)) {
			toast.hide();
		}
	}

	private String describe(long done, long total, double speed, long eta) {
		StringBuilder text = new StringBuilder(direction.symbol).append(' ');
		text.append(total > 0 ? size(done) + " of " + size(total) : size(done));
		if (speed > 0) {
			text.append(" \u00b7 ").append(size((long) speed)).append("/s");
		}
		if (eta > 0) {
			text.append(" \u00b7 ").append(duration(eta)).append(" left");
		}
		return text.toString();
	}

	private static String size(long bytes) {
		double value = Math.max(0, bytes);
		int unit = 0;
		while (value >= 1024 && unit < UNITS.length - 1) {
			value /= 1024;
			unit++;
		}
		return String.format(Locale.ROOT, unit == 0 ? "%.0f %s" : "%.1f %s", value, UNITS[unit]);
	}

	private static String duration(long seconds) {
		if (seconds < 60) {
			return seconds + " s";
		}
		long minutes = (seconds + 59) / 60;
		if (minutes < 60) {
			return minutes + " min";
		}
		return (minutes / 60) + " h " + (minutes % 60) + " min";
	}
}