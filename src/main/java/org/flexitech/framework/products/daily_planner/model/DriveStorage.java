package org.flexitech.framework.products.daily_planner.model;

public record DriveStorage(long used, long limit) {

	public boolean limited() {
		return limit > 0;
	}

	public double fraction() {
		return limited() ? Math.min(1.0, (double) used / limit) : 0;
	}
}