package org.flexitech.framework.products.daily_planner.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.framework.products.daily_planner.model.DriveItem;
import org.flexitech.framework.products.daily_planner.service.DriveService;
import org.flexitech.framework.products.daily_planner.service.UploadListener;

import javafx.application.Platform;

public final class Uploads {

	private static final Logger log = LogManager.getLogger(Uploads.class);
	private static final long UI_INTERVAL_NANOS = 200_000_000L;
	private static final long WAIT_MILLIS = 200;
	private static final double SMOOTHING = 0.3;
	private static final Object GATE = new Object();
	private static final ExecutorService POOL = Executors.newCachedThreadPool(task -> {
		Thread thread = new Thread(task, "drive-upload");
		thread.setDaemon(true);
		return thread;
	});

	private static int running;

	private Uploads() {
	}

	public static void start(DriveService service, Path source, String parentId, Consumer<DriveItem> onSuccess,
			TransferOptions options) {
		String key = ActiveTransfers.key("upload", source, parentId);
		if (!ActiveTransfers.acquire(key)) {
			Notifier.info("Upload", source.getFileName() + " is already uploading");
			return;
		}
		Platform.runLater(() -> {
			try {
				Transfer entry = DownloadPanel.add(source.getFileName().toString(), Transfer.Direction.UPLOAD);
				POOL.execute(() -> {
					try {
						run(service, source, parentId, entry, onSuccess, options.connections());
					} finally {
						ActiveTransfers.release(key);
					}
				});
			} catch (RuntimeException e) {
				ActiveTransfers.release(key);
				throw e;
			}
		});
	}

	private static void run(DriveService service, Path source, String parentId, Transfer entry,
			Consumer<DriveItem> onSuccess, int limit) {
		String name = source.getFileName().toString();
		boolean admitted;
		try {
			admitted = admit(limit, entry);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			Platform.runLater(entry::close);
			return;
		}
		if (!admitted) {
			Platform.runLater(entry::close);
			Notifier.info("Upload cancelled", name);
			return;
		}
		try {
			upload(service, source, parentId, entry, onSuccess, name);
		} finally {
			leave();
		}
	}

	private static boolean admit(int limit, Transfer entry) throws InterruptedException {
		int allowed = Math.max(1, limit);
		synchronized (GATE) {
			while (running >= allowed) {
				if (entry.isCancelled()) {
					return false;
				}
				GATE.wait(WAIT_MILLIS);
			}
			running++;
			return true;
		}
	}

	private static void leave() {
		synchronized (GATE) {
			running--;
			GATE.notifyAll();
		}
	}

	private static void upload(DriveService service, Path source, String parentId, Transfer entry,
			Consumer<DriveItem> onSuccess, String name) {
		long[] lastTick = { System.nanoTime() };
		long[] lastDone = { 0 };
		double[] speed = { 0 };
		DriveItem uploaded = null;

		try {
			long size = Files.size(source);
			uploaded = service.upload(source, name, parentId, new UploadListener() {
				@Override
				public void onProgress(long done, long total) {
					long now = System.nanoTime();
					if (now - lastTick[0] >= UI_INTERVAL_NANOS) {
						double instant = (done - lastDone[0]) / ((now - lastTick[0]) / 1_000_000_000.0);
						speed[0] = speed[0] == 0 ? instant : speed[0] * (1 - SMOOTHING) + instant * SMOOTHING;
						long eta = total > 0 && speed[0] > 0 ? (long) Math.ceil((total - done) / speed[0]) : -1;
						publish(entry, done, total, speed[0], eta);
						lastTick[0] = now;
						lastDone[0] = done;
					}
				}

				@Override
				public boolean isCancelled() {
					return entry.isCancelled();
				}
			});
			publish(entry, size, size, speed[0], 0);
			Platform.runLater(entry::close);
			Notifier.success("Upload complete", name);
		} catch (Exception e) {
			if (entry.isCancelled()) {
				log.info("Upload cancelled for {}", name);
				Platform.runLater(entry::close);
				Notifier.info("Upload cancelled", name);
			} else {
				log.error("Upload failed for {}", name, e);
				Platform.runLater(entry::close);
				Notifier.error("Upload failed", e.getMessage());
			}
			return;
		}

		if (onSuccess != null && uploaded != null) {
			DriveItem item = uploaded;
			Platform.runLater(() -> {
				try {
					onSuccess.accept(item);
				} catch (Exception e) {
					log.error("Upload completion callback failed for {}", name, e);
				}
			});
		}
	}

	private static void publish(Transfer entry, long done, long total, double speed, long eta) {
		Platform.runLater(() -> entry.update(done, total, speed, eta));
	}
}