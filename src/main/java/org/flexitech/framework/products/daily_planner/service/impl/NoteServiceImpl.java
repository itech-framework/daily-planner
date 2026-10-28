package org.flexitech.framework.products.daily_planner.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.framework.products.daily_planner.api.GoogleDriveAPI;
import org.flexitech.framework.products.daily_planner.api.response.DriveFileListResponse;
import org.flexitech.framework.products.daily_planner.api.response.DriveFileResponse;
import org.flexitech.framework.products.daily_planner.common.constant.DriveConstants;
import org.flexitech.framework.products.daily_planner.exception.NoteConflictException;
import org.flexitech.framework.products.daily_planner.model.DriveItem;
import org.flexitech.framework.products.daily_planner.model.Note;
import org.flexitech.framework.products.daily_planner.model.NoteDetail;
import org.flexitech.framework.products.daily_planner.model.NoteDraft;
import org.flexitech.framework.products.daily_planner.model.NoteFolder;
import org.flexitech.framework.products.daily_planner.service.DriveService;
import org.flexitech.framework.products.daily_planner.service.GoogleAuthService;
import org.flexitech.framework.products.daily_planner.service.NoteService;
import org.flexitech.framework.products.daily_planner.util.GoogleApiSupport;
import org.flexitech.framework.products.daily_planner.util.Multipart;
import org.flexitech.framework.products.daily_planner.util.NoteCache;
import org.flexitech.framework.products.daily_planner.util.NoteHtml;

import com.google.gson.Gson;

import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_framework.core.annotations.dependency.Use;

@Service
public class NoteServiceImpl implements NoteService {

	private static final Logger log = LogManager.getLogger(NoteServiceImpl.class);
	private static final Gson GSON = new Gson();
	private static final String API_NAME = "Google Drive";
	private static final String ROOT_NAME = "Daily Planner Notes";
	private static final String GENERAL = "General";
	private static final String UNTITLED = "Untitled note";
	private static final String DOC_MIME = "application/vnd.google-apps.document";
	private static final String HTML_MIME = "text/html";
	private static final String NOTE_FIELDS = "id,name,modifiedTime,webViewLink,parents";
	private static final String LIST_FIELDS = "nextPageToken,files(id,name,modifiedTime,webViewLink,parents)";
	private static final String PAGE_SIZE = "100";
	private static final String TRUE = "true";
	private static final String MULTIPART = "multipart";

	@Use
	private GoogleAuthService authService;

	@Use
	private GoogleDriveAPI driveAPI;

	@Use
	private DriveService driveService;

	private volatile String rootId;

	@Override
	public List<NoteFolder> getFolders() throws Exception {
		String root = rootFolderId();
		String token = authService.getAccessToken();
		String query = "'" + root + "' in parents and mimeType = '" + DriveConstants.FOLDER_MIME
				+ "' and trashed = false";
		DriveFileListResponse result = GoogleApiSupport.call(API_NAME, () -> driveAPI
				.listFiles(GoogleApiSupport.bearer(token), query, PAGE_SIZE, null, "name", null, LIST_FIELDS, TRUE, TRUE));

		List<NoteFolder> folders = new ArrayList<NoteFolder>();
		folders.add(new NoteFolder(root, GENERAL));
		if (result != null && result.getFiles() != null) {
			for (DriveFileResponse file : result.getFiles()) {
				folders.add(new NoteFolder(file.getId(), file.getName()));
			}
		}
		log.info("Loaded {} note folders", folders.size());
		return folders;
	}

	@Override
	public List<Note> getNotes(String folderId) throws Exception {
		String folder = folderId == null || folderId.isBlank() ? rootFolderId() : folderId;
		String token = authService.getAccessToken();
		String query = "'" + folder + "' in parents and mimeType = '" + DOC_MIME + "' and trashed = false";
		DriveFileListResponse result = GoogleApiSupport.call(API_NAME, () -> driveAPI.listFiles(
				GoogleApiSupport.bearer(token), query, PAGE_SIZE, null, "modifiedTime desc", null, LIST_FIELDS, TRUE,
				TRUE));

		List<Note> notes = new ArrayList<Note>();
		if (result != null && result.getFiles() != null) {
			for (DriveFileResponse file : result.getFiles()) {
				notes.add(toNote(file, folder));
			}
		}
		log.info("Loaded {} notes from folder {}", notes.size(), folder);
		return notes;
	}

	@Override
	public NoteDetail getNote(String noteId) throws Exception {
		String token = authService.getAccessToken();
		DriveFileResponse file = GoogleApiSupport.call(API_NAME,
				() -> driveAPI.getFile(GoogleApiSupport.bearer(token), noteId, NOTE_FIELDS, TRUE));
		if (file == null || file.getId() == null) {
			throw new Exception("This note could not be found.");
		}
		String content = NoteCache.read(noteId, file.getModifiedTime()).orElse(null);
		if (content == null) {
			byte[] html = GoogleApiSupport.call(API_NAME,
					() -> driveAPI.exportFile(GoogleApiSupport.bearer(token), noteId, HTML_MIME));
			content = html == null ? "" : new String(html, StandardCharsets.UTF_8);
			NoteCache.write(noteId, file.getModifiedTime(), content);
			log.info("Opened note {} from Google Docs ({} characters)", noteId, content.length());
		} else {
			log.info("Opened note {} from the local copy", noteId);
		}
		return new NoteDetail(toNote(file, parentOf(file)), content);
	}

	@Override
	public Note saveNote(NoteDraft draft) throws Exception {
		return saveNote(draft, false);
	}

	@Override
	public Note saveNote(NoteDraft draft, boolean overwrite) throws Exception {
		String title = draft.title() == null || draft.title().isBlank() ? UNTITLED : draft.title().trim();
		String original = draft.html() == null ? "" : draft.html();
		String html = NoteHtml.cleanForDoc(original);
		String target = draft.folderId() == null || draft.folderId().isBlank() ? rootFolderId() : draft.folderId();
		String token = authService.getAccessToken();
		if (!draft.isNew() && !overwrite) {
			ensureUnchanged(draft.id(), token);
		}
		String boundary = "planner-" + UUID.randomUUID();
		Map<String, String> headers = GoogleApiSupport.bearer(token);
		headers.put("Content-Type", Multipart.contentType(boundary));

		Map<String, Object> metadata = new LinkedHashMap<String, Object>();
		metadata.put("name", title);

		DriveFileResponse saved;
		if (draft.isNew()) {
			metadata.put("mimeType", DOC_MIME);
			metadata.put("parents", List.of(target));
			byte[] body = Multipart.related(boundary, GSON.toJson(metadata), HTML_MIME, html);
			log.info("Creating note '{}' in folder {}", title, target);
			saved = GoogleApiSupport.call(API_NAME,
					() -> driveAPI.uploadMultipart(headers, MULTIPART, NOTE_FIELDS, TRUE, body));
		} else {
			byte[] body = Multipart.related(boundary, GSON.toJson(metadata), HTML_MIME, html);
			log.info("Updating note {}", draft.id());
			saved = GoogleApiSupport.call(API_NAME,
					() -> driveAPI.updateMultipart(headers, draft.id(), MULTIPART, NOTE_FIELDS, TRUE, body));
			if (saved != null && (saved.getParents() == null || !saved.getParents().contains(target))) {
				driveService.moveItem(saved.getId(), target);
				saved = GoogleApiSupport.call(API_NAME,
						() -> driveAPI.getFile(GoogleApiSupport.bearer(token), draft.id(), NOTE_FIELDS, TRUE));
			}
		}
		if (saved == null || saved.getId() == null || saved.getId().isBlank()) {
			throw new Exception("Google Drive did not return the saved note.");
		}
		NoteCache.write(saved.getId(), saved.getModifiedTime(), original);
		return toNote(saved, target);
	}

	private void ensureUnchanged(String noteId, String token) throws Exception {
		String known = NoteCache.stamp(noteId);
		if (known == null) {
			return;
		}
		DriveFileResponse current = GoogleApiSupport.call(API_NAME,
				() -> driveAPI.getFile(GoogleApiSupport.bearer(token), noteId, NOTE_FIELDS, TRUE));
		if (current != null && current.getModifiedTime() != null && !known.equals(current.getModifiedTime())) {
			throw new NoteConflictException("This note was changed in Google Docs after you opened it.");
		}
	}

	@Override
	public void moveNote(String noteId, String folderId) throws Exception {
		String target = folderId == null || folderId.isBlank() ? rootFolderId() : folderId;
		driveService.moveItem(noteId, target);
	}

	@Override
	public void deleteNote(String noteId) throws Exception {
		driveService.trashItem(noteId);
		NoteCache.delete(noteId);
		log.info("Moved note {} to the Drive trash", noteId);
	}

	@Override
	public NoteFolder createFolder(String name) throws Exception {
		if (name == null || name.isBlank()) {
			throw new Exception("A folder needs a name.");
		}
		DriveItem created = driveService.createFolder(name.trim(), rootFolderId());
		return new NoteFolder(created.id(), created.name());
	}

	@Override
	public NoteFolder renameFolder(String folderId, String name) throws Exception {
		if (name == null || name.isBlank()) {
			throw new Exception("A folder needs a name.");
		}
		if (folderId.equals(rootFolderId())) {
			throw new Exception("The General folder cannot be renamed.");
		}
		DriveItem renamed = driveService.renameItem(folderId, name.trim());
		return new NoteFolder(renamed.id(), renamed.name());
	}

	@Override
	public void deleteFolder(String folderId) throws Exception {
		if (folderId.equals(rootFolderId())) {
			throw new Exception("The General folder cannot be deleted.");
		}
		driveService.trashItem(folderId);
		log.info("Moved note folder {} to the Drive trash", folderId);
	}

	private synchronized String rootFolderId() throws Exception {
		if (rootId != null) {
			return rootId;
		}
		String token = authService.getAccessToken();
		String query = "name = '" + ROOT_NAME + "' and mimeType = '" + DriveConstants.FOLDER_MIME
				+ "' and 'root' in parents and trashed = false";
		DriveFileListResponse found = GoogleApiSupport.call(API_NAME, () -> driveAPI.listFiles(
				GoogleApiSupport.bearer(token), query, "1", null, null, null, "files(id,name)", TRUE, TRUE));
		if (found != null && found.getFiles() != null && !found.getFiles().isEmpty()) {
			rootId = found.getFiles().get(0).getId();
			log.info("Using the existing notes folder {}", rootId);
		} else {
			rootId = driveService.createFolder(ROOT_NAME, null).id();
			log.info("Created the notes folder {}", rootId);
		}
		return rootId;
	}

	private static String parentOf(DriveFileResponse file) {
		return file.getParents() == null || file.getParents().isEmpty() ? null : file.getParents().get(0);
	}

	private static Note toNote(DriveFileResponse file, String folderId) {
		String title = file.getName() == null || file.getName().isBlank() ? UNTITLED : file.getName();
		return new Note(file.getId(), title, folderId, parse(file.getModifiedTime()), file.getWebViewLink());
	}

	private static LocalDateTime parse(String value) {
		if (value == null) {
			return null;
		}
		return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
	}
}