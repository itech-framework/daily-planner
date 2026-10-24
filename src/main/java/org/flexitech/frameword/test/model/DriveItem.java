package org.flexitech.frameword.test.model;

import java.time.LocalDateTime;

public record DriveItem(String id, String name, String mimeType, boolean folder, long size,
		LocalDateTime modified, String owner, String link, boolean shortcut) {

	public String kind() {
		if (folder) {
			return "folder";
		}
		String type = mimeType == null ? "" : mimeType;
		if (type.equals("application/vnd.google-apps.document")) {
			return "doc";
		}
		if (type.equals("application/vnd.google-apps.spreadsheet")) {
			return "sheet";
		}
		if (type.equals("application/vnd.google-apps.presentation")) {
			return "slides";
		}
		if (type.equals("application/pdf")) {
			return "pdf";
		}
		if (type.startsWith("image/")) {
			return "image";
		}
		if (type.startsWith("video/")) {
			return "video";
		}
		if (type.startsWith("audio/")) {
			return "audio";
		}
		if (type.contains("zip") || type.contains("compressed")) {
			return "archive";
		}
		return "file";
	}
}