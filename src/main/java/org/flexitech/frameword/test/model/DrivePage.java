package org.flexitech.frameword.test.model;

import java.util.List;

public record DrivePage(List<DriveItem> items, String nextPageToken) {
}