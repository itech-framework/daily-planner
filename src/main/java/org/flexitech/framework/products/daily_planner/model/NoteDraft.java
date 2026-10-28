package org.flexitech.framework.products.daily_planner.model;

public record NoteDraft(String id, String title, String folderId, String html) {

	public boolean isNew() {
		return id == null || id.isBlank();
	}
}