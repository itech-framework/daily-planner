package org.flexitech.frameword.test.util;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.frameword.test.model.DriveDownload;
import org.flexitech.frameword.test.model.DriveItem;
import org.flexitech.frameword.test.model.DrivePage;
import org.flexitech.frameword.test.service.DriveService;
import org.flexitech.frameword.test.ui.component.DownloadEntry;

import javafx.application.Platform;

public final class ZipDownloads {

	private static final Logger log = LogManager.getLogger(ZipDownloads.class);
	private static final int BUFFER_SIZE = 64 * 1024;
	private static final long UI_INTERVAL_NANOS = 200_000_000L;
	private static final double SMOOTHING = 0.3;
	private static final String NATIVE_PREFIX = "application/vnd.google-apps";
	private static final ExecutorService POOL = Executors.newCachedThreadPool(task -> {
		Thread thread = new Thread(task, "drive-zip");
		thread.setDaemon(true);
		return thread;
	});

	private record Source(DriveItem item, String path) {
	}

	private record Result(boolean cancelled, int skipped) {
	}

	private static final class Meter {

		private final DownloadEntry entry;
		private final long total;
		private long done;
		private long lastDone;
		private long lastTick = System.nanoTime();
		private double speed;

		Meter(DownloadEntry entry, long total) {
			this.entry = entry;
			this.total = total;
		}

		void add(long bytes) {
			done += bytes;
			long now = System.nanoTime();
			if (now - lastTick < UI_INTERVAL_NANOS) {
				return;
			}
			double instant = (done - lastDone) / ((now - lastTick) / 1_000_000_000.0);
			speed = speed == 0 ? instant : speed * (1 - SMOOTHING) + instant * SMOOTHING;
			long eta = total > 0 && speed > 0 ? (long) Math.ceil(Math.max(0, total - done) / speed) : -1;
			publish(done, total, speed, eta);
			lastTick = now;
			lastDone = done;
		}

		void finish() {
			publish(done, total, speed, 0);
		}

		private void publish(long doneBytes, long totalBytes, double bytesPerSecond, long eta) {
			Platform.runLater(() -> entry.update(doneBytes, totalBytes, bytesPerSecond, eta));
		}
	}

	private ZipDownloads() {
	}

	public static void start(DriveService service, List<DriveItem> items, Path target) {
		Platform.runLater(() -> {
			DownloadEntry entry = DownloadPanel.add(target.getFileName().toString(), DownloadEntry.Direction.DOWNLOAD);
			POOL.execute(() -> run(service, items, target, entry));
		});
	}

	private static void run(DriveService service, List<DriveItem> items, Path target, DownloadEntry entry) {
		String name = target.getFileName().toString();
		Path partial = target.resolveSibling(name + ".part");
		try {
			List<Source> sources = collect(service, items, entry);
			if (entry.isCancelled()) {
				cancelled(partial, entry, name);
				return;
			}
			if (sources.isEmpty()) {
				Platform.runLater(entry::close);
				Notifier.warning("Download", "There are no files to download");
				return;
			}
			Result result = write(service, sources, partial, entry);
			if (result.cancelled()) {
				cancelled(partial, entry, name);
				return;
			}
			Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
			Platform.runLater(entry::close);
			if (result.skipped() > 0) {
				Notifier.warning("Download complete", result.skipped() + " file(s) could not be added to " + name);
			} else {
				Notifier.success("Download complete", name);
			}
		} catch (Exception e) {
			log.error("Zip download failed for {}", name, e);
			discard(partial);
			Platform.runLater(entry::close);
			Notifier.error("Download failed", e.getMessage());
		}
	}

	private static void cancelled(Path partial, DownloadEntry entry, String name) {
		discard(partial);
		Platform.runLater(entry::close);
		Notifier.info("Download cancelled", name);
	}

	private static List<Source> collect(DriveService service, List<DriveItem> items, DownloadEntry entry)
			throws Exception {
		List<Source> sources = new ArrayList<Source>();
		Set<String> visited = new HashSet<String>();
		Set<String> used = new HashSet<String>();
		for (DriveItem item : items) {
			if (entry.isCancelled()) {
				return sources;
			}
			walk(service, item, "", sources, visited, used, entry);
		}
		return sources;
	}

	private static void walk(DriveService service, DriveItem item, String prefix, List<Source> sources,
			Set<String> visited, Set<String> used, DownloadEntry entry) throws Exception {
		if (!item.folder()) {
			sources.add(new Source(item, unique(prefix, safe(service.downloadName(item)), used)));
			return;
		}
		if (!visited.add(item.id())) {
			return;
		}
		String directory = prefix + safe(item.name()) + "/";
		String token = null;
		do {
			if (entry.isCancelled()) {
				return;
			}
			DrivePage page = service.listFolder(item.id(), token);
			for (DriveItem child : page.items()) {
				walk(service, child, directory, sources, visited, used, entry);
			}
			token = page.nextPageToken();
		} while (token != null);
	}

	private static Result write(DriveService service, List<Source> sources, Path partial, DownloadEntry entry)
			throws Exception {
		Meter meter = new Meter(entry, totalSize(sources));
		byte[] buffer = new byte[BUFFER_SIZE];
		int skipped = 0;
		try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(partial)))) {
			for (Source source : sources) {
				if (entry.isCancelled()) {
					return new Result(true, skipped);
				}
				DriveDownload download;
				try {
					download = service.openDownload(source.item());
				} catch (Exception e) {
					log.warn("Skipping {}", source.path(), e);
					skipped++;
					continue;
				}
				try (DriveDownload open = download; InputStream in = open.stream()) {
					zip.putNextEntry(new ZipEntry(source.path()));
					int read;
					while ((read = in.read(buffer)) != -1) {
						if (entry.isCancelled()) {
							return new Result(true, skipped);
						}
						zip.write(buffer, 0, read);
						meter.add(read);
					}
					zip.closeEntry();
				}
			}
		}
		meter.finish();
		return new Result(false, skipped);
	}

	private static long totalSize(List<Source> sources) {
		long total = 0;
		for (Source source : sources) {
			String mime = source.item().mimeType();
			if (mime != null && mime.startsWith(NATIVE_PREFIX)) {
				return 0;
			}
			total += source.item().size();
		}
		return total;
	}

	private static String safe(String name) {
		String cleaned = name == null ? "" : name.replace('/', '_').replace('\\', '_').trim();
		return cleaned.isEmpty() ? "file" : cleaned;
	}

	private static String unique(String prefix, String fileName, Set<String> used) {
		int dot = fileName.lastIndexOf('.');
		String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
		String extension = dot > 0 ? fileName.substring(dot) : "";
		String candidate = prefix + fileName;
		int counter = 2;
		while (!used.add(candidate)) {
			candidate = prefix + stem + " (" + counter++ + ")" + extension;
		}
		return candidate;
	}

	private static void discard(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			log.warn("Could not delete {}", path, e);
		}
	}
}