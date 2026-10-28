package org.flexitech.framework.products.daily_planner.service;

import java.util.List;

import org.flexitech.framework.products.daily_planner.model.Note;
import org.flexitech.framework.products.daily_planner.model.NoteDetail;
import org.flexitech.framework.products.daily_planner.model.NoteDraft;
import org.flexitech.framework.products.daily_planner.model.NoteFolder;

public interface NoteService {

	List<NoteFolder> getFolders() throws Exception;

	List<Note> getNotes(String folderId) throws Exception;

	NoteDetail getNote(String noteId) throws Exception;

	Note saveNote(NoteDraft draft) throws Exception;
	
	Note saveNote(NoteDraft draft, boolean overwrite) throws Exception;

	void moveNote(String noteId, String folderId) throws Exception;

	void deleteNote(String noteId) throws Exception;

	NoteFolder createFolder(String name) throws Exception;

	NoteFolder renameFolder(String folderId, String name) throws Exception;

	void deleteFolder(String folderId) throws Exception;
}