package org.flexitech.framework.products.daily_planner.util;

public class DriveFileUtil {
	public static String badge(String kind) {
		return switch (kind) {
		case "folder" -> "FOLDER";
		case "doc" -> "DOC";
		case "sheet" -> "SHEET";
		case "slides" -> "SLIDES";
		case "pdf" -> "PDF";
		case "image" -> "IMAGE";
		case "video" -> "VIDEO";
		case "audio" -> "AUDIO";
		case "archive" -> "ZIP";
		default -> "FILE";
		};
	}
}
