package org.flexitech.framework.products.daily_planner.service;

import java.nio.file.Path;
import java.util.List;

import org.flexitech.framework.products.daily_planner.model.DriveDownload;
import org.flexitech.framework.products.daily_planner.model.DriveItem;
import org.flexitech.framework.products.daily_planner.model.DrivePage;
import org.flexitech.framework.products.daily_planner.model.DriveStorage;

public interface DriveService {

	DrivePage listFolder(String folderId, String pageToken) throws Exception;

	DrivePage listSharedWithMe(String pageToken) throws Exception;

	List<DriveItem> getPath(String folderId) throws Exception;

	String getRootId() throws Exception;
	
	String downloadName(DriveItem item);

	DriveDownload openDownload(DriveItem item) throws Exception;
	
	DriveDownload openDownload(DriveItem item, long from, long to) throws Exception;
	
	DriveItem upload(Path source, String name, String parentId, UploadListener listener) throws Exception;
	
	void moveItem(String itemId, String targetFolderId) throws Exception;
	
	DriveItem createFolder(String name, String parentId) throws Exception;

	DriveItem renameItem(String itemId, String name) throws Exception;

	void trashItem(String itemId) throws Exception;

	void restoreItem(String itemId) throws Exception;

	void deleteForever(String itemId) throws Exception;

	void emptyTrash() throws Exception;

	DrivePage search(String text, String pageToken) throws Exception;

	DrivePage listTrash(String pageToken) throws Exception;
	
	List<DriveItem> listRecent(int limit) throws Exception;

	DriveStorage getStorage() throws Exception;
}