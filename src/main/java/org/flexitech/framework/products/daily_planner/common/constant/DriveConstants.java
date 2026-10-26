package org.flexitech.framework.products.daily_planner.common.constant;

public final class DriveConstants {

	public static final String FOLDER_MIME = "application/vnd.google-apps.folder";
	public static final String SHORTCUT_MIME = "application/vnd.google-apps.shortcut";
	public static final String ROOT_ID = "root";

	public static final String ORDER_FOLDERS_FIRST = "folder,name";

	public static final String FILE_FIELDS = "id,name,mimeType,parents,size,starred,trashed,shared,ownedByMe,createdTime,modifiedTime,iconLink,thumbnailLink,webViewLink,webContentLink,owners(displayName,emailAddress,photoLink),capabilities(canEdit,canDelete,canTrash,canRename,canAddChildren,canDownload,canCopy),shortcutDetails(targetId,targetMimeType),sharedWithMeTime,sharingUser(displayName,emailAddress)";

	public static final String LIST_FIELDS = "nextPageToken,incompleteSearch,files(" + FILE_FIELDS + ")";

	public static final String BREADCRUMB_FIELDS = "id,name,parents";

	private DriveConstants() {
	}

	public static String childrenOf(String folderId) {
		return "'" + folderId + "' in parents and trashed=false";
	}

	public static String foldersOf(String folderId) {
		return "'" + folderId + "' in parents and trashed=false and mimeType='" + FOLDER_MIME + "'";
	}

	public static String sharedWithMe() {
		return "sharedWithMe=true and trashed=false";
	}
}