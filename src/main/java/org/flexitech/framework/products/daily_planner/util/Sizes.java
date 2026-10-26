package org.flexitech.framework.products.daily_planner.util;

import java.util.Locale;

public final class Sizes {

	private Sizes() {
	}

	public static String format(long bytes) {
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