package org.flexitech.frameword.test.model;

import java.time.LocalDateTime;

public record EventDraft(String title, String location, String description, boolean allDay, LocalDateTime start,
		LocalDateTime end) {
}