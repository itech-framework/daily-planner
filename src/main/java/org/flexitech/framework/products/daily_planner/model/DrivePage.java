package org.flexitech.framework.products.daily_planner.model;

import java.util.List;

public record DrivePage(List<DriveItem> items, String nextPageToken) {
}