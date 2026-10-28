package org.flexitech.framework.products.daily_planner.model;

import java.time.LocalDateTime;

public record Note(String id, String title, String folderId, LocalDateTime modified, String link) {
}