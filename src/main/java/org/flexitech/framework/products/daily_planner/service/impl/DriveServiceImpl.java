package org.flexitech.framework.products.daily_planner.service.impl;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.framework.products.daily_planner.api.GoogleDriveAPI;
import org.flexitech.framework.products.daily_planner.api.response.DriveFileListResponse;
import org.flexitech.framework.products.daily_planner.api.response.DriveFileResponse;
import org.flexitech.framework.products.daily_planner.common.constant.DriveConstants;
import org.flexitech.framework.products.daily_planner.model.DriveDownload;
import org.flexitech.framework.products.daily_planner.model.DriveItem;
import org.flexitech.framework.products.daily_planner.model.DrivePage;
import org.flexitech.framework.products.daily_planner.model.DriveStorage;
import org.flexitech.framework.products.daily_planner.service.DriveService;
import org.flexitech.framework.products.daily_planner.service.GoogleAuthService;
import org.flexitech.framework.products.daily_planner.service.UploadListener;
import org.flexitech.framework.products.daily_planner.util.GoogleApiSupport;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_framework.core.annotations.dependency.Use;

@Service
public class DriveServiceImpl implements DriveService {

	private static final Logger log = LogManager.getLogger(DriveServiceImpl.class);
	private static final String PAGE_SIZE = "40";
	private static final int MAX_DEPTH = 25;
	private static final String RESUMABLE_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable";

	private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
			.followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
	private static final int UPLOAD_BUFFER_SIZE = 256 * 1024;

	private static final String FILES_URL = "https://www.googleapis.com/drive/v3/files/";
	private static final String NATIVE_PREFIX = "application/vnd.google-apps";
	
	private static final String COLLECTION_URL = "https://www.googleapis.com/drive/v3/files";
	private static final String ITEM_FIELDS = "id,name,mimeType,modifiedTime,size,webViewLink,owners(displayName),shortcutDetails(targetId,targetMimeType)";

	private static final Gson GSON = new Gson();

	@Use
	private GoogleAuthService authService;

	@Use
	private GoogleDriveAPI driveAPI;

	private volatile String rootId;

	@Override
	public DrivePage listFolder(String folderId, String pageToken) throws Exception {
		return list(DriveConstants.childrenOf(folderId), pageToken);
	}

	@Override
	public DrivePage listSharedWithMe(String pageToken) throws Exception {
		return list(DriveConstants.sharedWithMe(), pageToken);
	}

	@Override
	public List<DriveItem> getPath(String folderId) throws Exception {
		String token = authService.getAccessToken();
		List<DriveItem> path = new ArrayList<DriveItem>();
		String current = folderId;
		int depth = 0;
		while (current != null && depth++ < MAX_DEPTH) {
			String target = current;
			DriveFileResponse file;
			try {
				file = GoogleApiSupport.call("Google Drive", () -> driveAPI.getFile(GoogleApiSupport.bearer(token),
						target, DriveConstants.BREADCRUMB_FIELDS, "true"));
			} catch (Exception e) {
				if (path.isEmpty()) {
					throw e;
				}
				break;
			}
			if (file == null) {
				break;
			}
			path.add(0, new DriveItem(file.getId(), file.getName(), DriveConstants.FOLDER_MIME, true, 0, null, null,
					null, false));
			current = file.getParents() == null || file.getParents().isEmpty() ? null : file.getParents().get(0);
		}
		return path;
	}

	@Override
	public String getRootId() throws Exception {
		if (rootId == null) {
			String token = authService.getAccessToken();
			DriveFileResponse root = GoogleApiSupport.call("Google Drive",
					() -> driveAPI.getFile(GoogleApiSupport.bearer(token), DriveConstants.ROOT_ID, "id,name", "true"));
			rootId = root == null ? null : root.getId();
		}
		return rootId;
	}

	@Override
	public String downloadName(DriveItem item) {
		String extension = exportExtension(item.mimeType());
		return extension == null ? item.name() : item.name() + extension;
	}

	@Override
	public DriveDownload openDownload(DriveItem item) throws Exception {
		return open(item, -1, -1);
	}

	@Override
	public DriveDownload openDownload(DriveItem item, long from, long to) throws Exception {
		return open(item, from, to);
	}

	private DriveDownload open(DriveItem item, long from, long to) throws Exception {
		String mime = item.mimeType();
		String url;
		if (mime != null && mime.startsWith(NATIVE_PREFIX)) {
			String exportMime = exportMime(mime);
			if (exportMime == null) {
				throw new IOException("This type of Google file cannot be downloaded");
			}
			url = FILES_URL + item.id() + "/export?mimeType=" + URLEncoder.encode(exportMime, StandardCharsets.UTF_8);
		} else {
			url = FILES_URL + item.id() + "?alt=media&supportsAllDrives=true";
		}
		boolean ranged = from >= 0;
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).header("Authorization",
				GoogleApiSupport.bearer(authService.getAccessToken(), true));
		if (ranged) {
			builder.header("Range", "bytes=" + from + "-" + to);
		}
		HttpResponse<InputStream> response = HTTP.send(builder.GET().build(),
				HttpResponse.BodyHandlers.ofInputStream());
		InputStream stream = response.body();
		int status = response.statusCode();
		if (ranged ? status != 206 : status != 200) {
			stream.close();
			throw new IOException("Google Drive returned HTTP " + status);
		}
		long size = ranged ? to - from + 1 : response.headers().firstValueAsLong("Content-Length").orElse(item.size());
		return new DriveDownload(stream, size, stream);
	}

	@Override
	public DriveItem upload(Path source, String name, String parentId, UploadListener listener) throws Exception {
		long size = Files.size(source);
		String mime = detectMimeType(source);
		String token = authService.getAccessToken();
		String metadata = buildUploadMetadata(name, parentId, mime);

		HttpRequest init = HttpRequest.newBuilder(URI.create(RESUMABLE_URL))
				.header("Authorization", GoogleApiSupport.bearer(token, true))
				.header("Content-Type", "application/json; charset=UTF-8")
				.header("X-Upload-Content-Length", String.valueOf(size)).header("X-Upload-Content-Type", mime)
				.POST(HttpRequest.BodyPublishers.ofString(metadata, StandardCharsets.UTF_8)).build();

		HttpResponse<Void> initResponse = HTTP.send(init, HttpResponse.BodyHandlers.discarding());
		if (initResponse.statusCode() != 200) {
			throw new IOException("Could not start upload: HTTP " + initResponse.statusCode());
		}
		String sessionUri = initResponse.headers().firstValue("Location")
				.orElseThrow(() -> new IOException("Upload session URI was not returned"));

		HttpRequest.BodyPublisher body = progressBody(source, size, listener);
		HttpRequest put = HttpRequest.newBuilder(URI.create(sessionUri)).header("Content-Type", mime).PUT(body).build();

		HttpResponse<String> response = HTTP.send(put, HttpResponse.BodyHandlers.ofString());
		int status = response.statusCode();
		if (status != 200 && status != 201) {
			throw new IOException("Upload failed: HTTP " + status);
		}
		log.info("Uploaded {} ({} bytes) as {}", name, size, mime);

		DriveFileResponse file = null;
		try {
			file = GSON.fromJson(response.body(), DriveFileResponse.class);
		} catch (Exception e) {
			log.warn("Could not parse upload response for {}", name, e);
		}
		if (file == null || file.getId() == null || file.getId().isBlank()) {
			throw new IOException("Upload succeeded but no file id was returned");
		}
		return new DriveItem(file.getId(), name, mime, false, size, java.time.LocalDateTime.now(), null,
				file.getWebViewLink(), false);
	}
	
	@Override
	public void moveItem(String itemId, String targetFolderId) throws Exception {
		String token = authService.getAccessToken();
		DriveFileResponse file = GoogleApiSupport.call("Google Drive",
				() -> driveAPI.getFile(GoogleApiSupport.bearer(token), itemId, "id,parents", "true"));
		String target = targetFolderId == null || DriveConstants.ROOT_ID.equals(targetFolderId) ? getRootId()
				: targetFolderId;
		List<String> parents = file == null || file.getParents() == null ? new ArrayList<String>() : file.getParents();
		if (parents.size() == 1 && parents.get(0).equals(target)) {
			return;
		}
		StringBuilder url = new StringBuilder(FILES_URL).append(itemId).append("?supportsAllDrives=true&fields=id")
				.append("&addParents=").append(URLEncoder.encode(target, StandardCharsets.UTF_8));
		List<String> remove = new ArrayList<String>(parents);
		remove.remove(target);
		if (!remove.isEmpty()) {
			url.append("&removeParents=").append(URLEncoder.encode(String.join(",", remove), StandardCharsets.UTF_8));
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
				.header("Authorization", GoogleApiSupport.bearer(token, true))
				.header("Content-Type", "application/json")
				.method("PATCH", HttpRequest.BodyPublishers.ofString("{}"))
				.build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IOException("Could not move: HTTP " + response.statusCode());
		}
	}
	
	@Override
	public DriveItem createFolder(String name, String parentId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("name", name);
		body.put("mimeType", DriveConstants.FOLDER_MIME);
		if (parentId != null && !parentId.isBlank()) {
			body.put("parents", List.of(parentId));
		}
		String url = COLLECTION_URL + "?supportsAllDrives=true&fields=" + encode(ITEM_FIELDS);
		return parseItem(ok(send("POST", url, GSON.toJson(body)), "Create folder"));
	}

	@Override
	public DriveItem renameItem(String itemId, String name) throws Exception {
		String url = FILES_URL + itemId + "?supportsAllDrives=true&fields=" + encode(ITEM_FIELDS);
		return parseItem(ok(send("PATCH", url, GSON.toJson(Map.of("name", name))), "Rename"));
	}

	@Override
	public void trashItem(String itemId) throws Exception {
		setTrashed(itemId, true);
	}

	@Override
	public void restoreItem(String itemId) throws Exception {
		setTrashed(itemId, false);
	}

	@Override
	public void deleteForever(String itemId) throws Exception {
		ok(send("DELETE", FILES_URL + itemId + "?supportsAllDrives=true", null), "Delete");
	}

	@Override
	public void emptyTrash() throws Exception {
		ok(send("DELETE", COLLECTION_URL + "/trash", null), "Empty Trash");
	}

	@Override
	public DrivePage search(String text, String pageToken) throws Exception {
		return list("name contains '" + escapeQuery(text) + "' and trashed = false", pageToken);
	}

	@Override
	public DrivePage listTrash(String pageToken) throws Exception {
		return list("trashed = true", pageToken);
	}
	
	@Override
	public List<DriveItem> listRecent(int limit) throws Exception {
		String token = authService.getAccessToken();
		String query = "trashed = false and mimeType != '" + DriveConstants.FOLDER_MIME + "'";
		DriveFileListResponse result = GoogleApiSupport.call("Google Drive",
				() -> driveAPI.listFiles(GoogleApiSupport.bearer(token), query, String.valueOf(limit), null,
						"modifiedTime desc", null, DriveConstants.LIST_FIELDS, "true", "true"));
		List<DriveItem> items = new ArrayList<DriveItem>();
		if (result != null && result.getFiles() != null) {
			for (DriveFileResponse file : result.getFiles()) {
				items.add(toItem(file));
			}
		}
		return items;
	}

	@Override
	public DriveStorage getStorage() throws Exception {
		String json = ok(send("GET", "https://www.googleapis.com/drive/v3/about?fields=storageQuota", null),
				"Storage");
		JsonObject root = GSON.fromJson(json, JsonObject.class);
		JsonObject quota = root == null || !root.has("storageQuota") ? null : root.getAsJsonObject("storageQuota");
		long used = quota != null && quota.has("usage") ? Long.parseLong(quota.get("usage").getAsString()) : 0;
		long limit = quota != null && quota.has("limit") ? Long.parseLong(quota.get("limit").getAsString()) : 0;
		return new DriveStorage(used, limit);
	}

	private void setTrashed(String itemId, boolean trashed) throws Exception {
		String url = FILES_URL + itemId + "?supportsAllDrives=true&fields=id";
		ok(send("PATCH", url, GSON.toJson(Map.of("trashed", trashed))), trashed ? "Move to Trash" : "Restore");
	}

	private HttpResponse<String> send(String method, String url, String json) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
				.header("Authorization", GoogleApiSupport.bearer(authService.getAccessToken(), true));
		HttpRequest request = json == null
				? builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
				: builder.header("Content-Type", "application/json; charset=UTF-8")
						.method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
		return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private String ok(HttpResponse<String> response, String action) throws IOException {
		int status = response.statusCode();
		if (status >= 200 && status < 300) {
			return response.body();
		}
		throw new IOException(action + " failed: HTTP " + status);
	}

	private DriveItem parseItem(String json) throws IOException {
		DriveFileResponse file = GSON.fromJson(json, DriveFileResponse.class);
		if (file == null || file.getId() == null) {
			throw new IOException("Google Drive returned no file");
		}
		return toItem(file);
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String escapeQuery(String value) {
		return value.replace("\\", "\\\\").replace("'", "\\'");
	}

	private static String detectMimeType(Path path) {
		try {
			String probed = Files.probeContentType(path);
			if (probed != null && !probed.isBlank()) {
				return probed;
			}
		} catch (IOException ignored) {
		}
		return "application/octet-stream";
	}

	private static String buildUploadMetadata(String name, String parentId, String mime) {
		StringBuilder sb = new StringBuilder("{\"name\":\"").append(escapeJson(name)).append("\"");
		if (parentId != null && !parentId.isBlank()) {
			sb.append(",\"parents\":[\"").append(escapeJson(parentId)).append("\"]");
		}
		sb.append(",\"mimeType\":\"").append(escapeJson(mime)).append("\"}");
		return sb.toString();
	}

	private static String escapeJson(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private HttpRequest.BodyPublisher progressBody(Path source, long size, UploadListener listener) {
		Flow.Publisher<ByteBuffer> publisher = subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
			private final AtomicBoolean done = new AtomicBoolean();
			private long requested;
			private long uploaded;
			private InputStream in;
			private boolean draining;

			@Override
			public void request(long n) {
				if (n <= 0) {
					if (done.compareAndSet(false, true)) {
						subscriber.onError(new IllegalArgumentException("non-positive request"));
					}
					return;
				}
				synchronized (this) {
					requested += n;
				}
				drain();
			}

			@Override
			public void cancel() {
				if (done.compareAndSet(false, true)) {
					closeQuietly();
				}
			}

			private void drain() {
				synchronized (this) {
					if (draining) {
						return;
					}
					draining = true;
				}
				try {
					if (in == null) {
						in = new BufferedInputStream(Files.newInputStream(source), UPLOAD_BUFFER_SIZE);
					}
					while (!done.get()) {
						long available;
						synchronized (this) {
							available = requested;
						}
						if (available <= 0) {
							return;
						}
						if (listener.isCancelled()) {
							if (done.compareAndSet(false, true)) {
								subscriber.onError(new IOException("Upload cancelled"));
							}
							return;
						}
						byte[] buf = new byte[UPLOAD_BUFFER_SIZE];
						int read = in.read(buf);
						if (read == -1) {
							if (done.compareAndSet(false, true)) {
								subscriber.onComplete();
							}
							return;
						}
						synchronized (this) {
							requested -= 1;
							uploaded += read;
						}
						subscriber.onNext(ByteBuffer.wrap(buf, 0, read));
						listener.onProgress(uploaded, size);
					}
				} catch (Throwable e) {
					if (done.compareAndSet(false, true)) {
						subscriber.onError(e);
					}
				} finally {
					synchronized (this) {
						draining = false;
					}
					if (done.get()) {
						closeQuietly();
					}
				}
			}

			private void closeQuietly() {
				try {
					if (in != null) {
						in.close();
					}
				} catch (IOException ignored) {
				}
			}
		});
		return HttpRequest.BodyPublishers.fromPublisher(publisher, size);
	}

	private String exportExtension(String mime) {
		return switch (exportMime(mime) == null ? "" : mime) {
		case "application/vnd.google-apps.document" -> ".docx";
		case "application/vnd.google-apps.spreadsheet" -> ".xlsx";
		case "application/vnd.google-apps.presentation" -> ".pptx";
		case "application/vnd.google-apps.drawing" -> ".pdf";
		default -> null;
		};
	}

	private String exportMime(String mime) {
		if (mime == null) {
			return null;
		}
		return switch (mime) {
		case "application/vnd.google-apps.document" ->
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document";
		case "application/vnd.google-apps.spreadsheet" ->
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
		case "application/vnd.google-apps.presentation" ->
			"application/vnd.openxmlformats-officedocument.presentationml.presentation";
		case "application/vnd.google-apps.drawing" -> "application/pdf";
		default -> null;
		};
	}

	private DrivePage list(String query, String pageToken) throws Exception {
		log.info("Listing Drive files: {}", query);
		String token = authService.getAccessToken();
		DriveFileListResponse result = GoogleApiSupport.call("Google Drive",
				() -> driveAPI.listFiles(GoogleApiSupport.bearer(token), query, PAGE_SIZE, pageToken,
						DriveConstants.ORDER_FOLDERS_FIRST, null, DriveConstants.LIST_FIELDS, "true", "true"));

		List<DriveItem> items = new ArrayList<DriveItem>();
		if (result != null && result.getFiles() != null) {
			for (DriveFileResponse file : result.getFiles()) {
				items.add(toItem(file));
			}
		}
		log.info("Loaded {} Drive items", items.size());
		return new DrivePage(items, result == null ? null : result.getNextPageToken());
	}

	private DriveItem toItem(DriveFileResponse file) {
		boolean shortcut = DriveConstants.SHORTCUT_MIME.equals(file.getMimeType());
		boolean resolved = shortcut && file.getShortcutDetails() != null
				&& file.getShortcutDetails().getTargetId() != null;
		String id = resolved ? file.getShortcutDetails().getTargetId() : file.getId();
		String mimeType = resolved ? file.getShortcutDetails().getTargetMimeType() : file.getMimeType();
		boolean folder = DriveConstants.FOLDER_MIME.equals(mimeType);
		String name = file.getName() == null || file.getName().isBlank() ? "(No name)" : file.getName();

		String owner = null;
		if (file.getOwners() != null && !file.getOwners().isEmpty()) {
			owner = file.getOwners().get(0).getDisplayName();
		} else if (file.getSharingUser() != null) {
			owner = file.getSharingUser().getDisplayName();
		}

		return new DriveItem(id, name, mimeType, folder, file.getSize() == null ? 0 : file.getSize(),
				parse(file.getModifiedTime()), owner, file.getWebViewLink(), shortcut);
	}

	private java.time.LocalDateTime parse(String value) {
		if (value == null) {
			return null;
		}
		return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
	}
}