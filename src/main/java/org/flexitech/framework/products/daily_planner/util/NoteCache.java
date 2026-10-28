package org.flexitech.framework.products.daily_planner.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class NoteCache {

	private static final Logger log = LogManager.getLogger(NoteCache.class);
	private static final Path DIRECTORY = Paths.get(System.getProperty("user.home"), ".daily-planner", "notes");

	private NoteCache() {
	}

	public static Optional<String> read(String noteId, String modified) {
		if (noteId == null || modified == null) {
			return Optional.empty();
		}
		try {
			Path stamp = file(noteId, "stamp");
			Path html = file(noteId, "html");
			if (!Files.exists(stamp) || !Files.exists(html)) {
				return Optional.empty();
			}
			String stored = Files.readString(stamp, StandardCharsets.UTF_8).trim();
			if (!modified.equals(stored)) {
				return Optional.empty();
			}
			return Optional.of(Files.readString(html, StandardCharsets.UTF_8));
		} catch (IOException e) {
			log.warn("Could not read the local copy of note {}", noteId, e);
			return Optional.empty();
		}
	}

	public static String stamp(String noteId) {
		if (noteId == null) {
			return null;
		}
		try {
			Path stamp = file(noteId, "stamp");
			return Files.exists(stamp) ? Files.readString(stamp, StandardCharsets.UTF_8).trim() : null;
		} catch (IOException e) {
			log.warn("Could not read the stamp of note {}", noteId, e);
			return null;
		}
	}

	public static void write(String noteId, String modified, String html) {
		if (noteId == null || modified == null) {
			return;
		}
		try {
			Files.createDirectories(DIRECTORY);
			store(file(noteId, "html"), html == null ? "" : html);
			store(file(noteId, "stamp"), modified);
		} catch (IOException e) {
			log.warn("Could not write the local copy of note {}", noteId, e);
		}
	}

	public static void delete(String noteId) {
		if (noteId == null) {
			return;
		}
		try {
			Files.deleteIfExists(file(noteId, "stamp"));
			Files.deleteIfExists(file(noteId, "html"));
		} catch (IOException e) {
			log.warn("Could not delete the local copy of note {}", noteId, e);
		}
	}

	private static void store(Path target, String content) throws IOException {
		Path temp = target.resolveSibling(target.getFileName() + ".tmp");
		Files.writeString(temp, content, StandardCharsets.UTF_8);
		Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	private static Path file(String noteId, String extension) {
		return DIRECTORY.resolve(noteId.replaceAll("[^A-Za-z0-9_-]", "_") + "." + extension);
	}
}