package org.flexitech.framework.products.daily_planner.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.framework.products.daily_planner.model.DriveDownload;
import org.flexitech.framework.products.daily_planner.model.DriveItem;
import org.flexitech.framework.products.daily_planner.service.DriveService;

import javafx.application.Platform;

public final class Downloads {

	private static final Logger log = LogManager.getLogger(Downloads.class);
	private static final int BUFFER_SIZE = 256 * 1024;
	private static final long UI_INTERVAL_NANOS = 200_000_000L;
	private static final double SMOOTHING = 0.3;
	private static final long PARALLEL_THRESHOLD = 8L * 1024 * 1024;
	private static final long CHUNK_SIZE = 8L * 1024 * 1024;
	private static final int MAX_ATTEMPTS = 3;
	private static final String NATIVE_PREFIX = "application/vnd.google-apps";
	private static final ExecutorService POOL = Executors.newCachedThreadPool(task -> {
		Thread thread = new Thread(task, "drive-download");
		thread.setDaemon(true);
		return thread;
	});

	private Downloads() {
	}

	public static void start(DriveService service, DriveItem item, Path target, TransferOptions options) {
		String key = ActiveTransfers.key("download", target, null);
		if (!ActiveTransfers.acquire(key)) {
			Notifier.info("Download", target.getFileName() + " is already downloading");
			return;
		}
		Platform.runLater(() -> {
			try {
				Transfer entry = DownloadPanel.add(target.getFileName().toString(), Transfer.Direction.DOWNLOAD);
				POOL.execute(() -> {
					try {
						run(service, item, target, entry, options.connections());
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

	private static void run(DriveService service, DriveItem item, Path target, Transfer entry, int connections) {
		String name = target.getFileName().toString();
		Path partial = target.resolveSibling(name + ".part");
		try {
			boolean completed;
			if (isParallel(item)) {
				completed = copyParallel(service, item, partial, entry, connections);
			} else {
				try (DriveDownload download = service.openDownload(item)) {
					completed = copy(download, partial, entry);
				}
			}
			if (completed) {
				Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
				Platform.runLater(entry::close);
				Notifier.success("Download complete", name);
			} else {
				discard(partial);
				Platform.runLater(entry::close);
				Notifier.info("Download cancelled", name);
			}
		} catch (Exception e) {
			log.error("Download failed for {}", name, e);
			discard(partial);
			Platform.runLater(entry::close);
			Notifier.error("Download failed", e.getMessage());
		}
	}

	private static boolean isParallel(DriveItem item) {
		String mime = item.mimeType();
		boolean nativeFile = mime != null && mime.startsWith(NATIVE_PREFIX);
		return !item.folder() && !nativeFile && item.size() >= PARALLEL_THRESHOLD;
	}

	private static boolean copy(DriveDownload download, Path partial, Transfer entry) throws IOException {
		long total = download.size();
		byte[] buffer = new byte[BUFFER_SIZE];
		long done = 0;
		long lastDone = 0;
		long lastTick = System.nanoTime();
		double speed = 0;
		try (InputStream in = download.stream(); OutputStream out = Files.newOutputStream(partial)) {
			int read;
			while ((read = in.read(buffer)) != -1) {
				if (entry.isCancelled()) {
					return false;
				}
				out.write(buffer, 0, read);
				done += read;
				long now = System.nanoTime();
				if (now - lastTick >= UI_INTERVAL_NANOS) {
					double instant = (done - lastDone) / ((now - lastTick) / 1_000_000_000.0);
					speed = speed == 0 ? instant : speed * (1 - SMOOTHING) + instant * SMOOTHING;
					long eta = total > 0 && speed > 0 ? (long) Math.ceil((total - done) / speed) : -1;
					publish(entry, done, total, speed, eta);
					lastTick = now;
					lastDone = done;
				}
			}
		}
		publish(entry, done, total, speed, 0);
		return true;
	}

	private static boolean copyParallel(DriveService service, DriveItem item, Path partial, Transfer entry,
			int connections) throws Exception {
		long total = item.size();
		int chunks = (int) ((total + CHUNK_SIZE - 1) / CHUNK_SIZE);
		int workers = Math.min(connections, chunks);
		AtomicInteger next = new AtomicInteger();
		AtomicLong done = new AtomicLong();
		AtomicBoolean stop = new AtomicBoolean();
		AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		CountDownLatch finished = new CountDownLatch(workers);
		long lastDone = 0;
		long lastTick = System.nanoTime();
		double speed = 0;

		try (FileChannel channel = FileChannel.open(partial, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
				StandardOpenOption.TRUNCATE_EXISTING)) {
			for (int i = 0; i < workers; i++) {
				POOL.execute(() -> {
					try {
						worker(service, item, channel, total, chunks, next, done, stop, entry);
					} catch (Throwable e) {
						failure.compareAndSet(null, e);
						stop.set(true);
					} finally {
						finished.countDown();
					}
				});
			}
			while (!finished.await(UI_INTERVAL_NANOS, TimeUnit.NANOSECONDS)) {
				long now = System.nanoTime();
				long current = done.get();
				double instant = (current - lastDone) / ((now - lastTick) / 1_000_000_000.0);
				speed = speed == 0 ? instant : speed * (1 - SMOOTHING) + instant * SMOOTHING;
				long eta = speed > 0 ? (long) Math.ceil(Math.max(0, total - current) / speed) : -1;
				publish(entry, current, total, speed, eta);
				lastTick = now;
				lastDone = current;
			}
		}

		Throwable error = failure.get();
		if (error != null) {
			if (error instanceof Exception exception) {
				throw exception;
			}
			throw new IOException(error);
		}
		if (entry.isCancelled()) {
			return false;
		}
		publish(entry, total, total, speed, 0);
		return true;
	}

	private static void worker(DriveService service, DriveItem item, FileChannel channel, long total, int chunks,
			AtomicInteger next, AtomicLong done, AtomicBoolean stop, Transfer entry) throws Exception {
		byte[] buffer = new byte[BUFFER_SIZE];
		int index;
		while (!stop.get() && !entry.isCancelled() && (index = next.getAndIncrement()) < chunks) {
			long from = index * CHUNK_SIZE;
			long to = Math.min(from + CHUNK_SIZE, total) - 1;
			for (int attempt = 1;; attempt++) {
				try {
					fetchChunk(service, item, channel, from, to, done, stop, entry, buffer);
					break;
				} catch (IOException e) {
					if (attempt >= MAX_ATTEMPTS || stop.get() || entry.isCancelled()) {
						throw e;
					}
					log.warn("Retrying bytes {}-{} of {} (attempt {})", from, to, item.name(), attempt, e);
				}
			}
		}
	}

	private static void fetchChunk(DriveService service, DriveItem item, FileChannel channel, long from, long to,
			AtomicLong done, AtomicBoolean stop, Transfer entry, byte[] buffer) throws Exception {
		long written = 0;
		try (DriveDownload part = service.openDownload(item, from, to); InputStream in = part.stream()) {
			long position = from;
			int read;
			while ((read = in.read(buffer)) != -1) {
				if (stop.get() || entry.isCancelled()) {
					return;
				}
				ByteBuffer data = ByteBuffer.wrap(buffer, 0, read);
				while (data.hasRemaining()) {
					position += channel.write(data, position);
				}
				written += read;
				done.addAndGet(read);
			}
			if (position != to + 1) {
				throw new IOException("Incomplete range " + from + "-" + to);
			}
		} catch (Exception e) {
			done.addAndGet(-written);
			throw e;
		}
	}

	private static void publish(Transfer entry, long done, long total, double speed, long eta) {
		Platform.runLater(() -> entry.update(done, total, speed, eta));
	}

	private static void discard(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			log.warn("Could not delete {}", path, e);
		}
	}
}