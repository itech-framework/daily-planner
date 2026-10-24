package org.flexitech.frameword.test.api.response;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DriveFileResponse {

	private String kind;
	private String id;
	private String name;
	private String mimeType;
	private String description;
	private Boolean starred;
	private Boolean trashed;
	private String trashedTime;
	private List<String> parents;
	private Map<String, String> properties;
	private Map<String, String> appProperties;
	private List<String> spaces;
	private Long version;
	private String webContentLink;
	private String webViewLink;
	private String iconLink;
	private String thumbnailLink;
	private String createdTime;
	private String modifiedTime;
	private DriveUser lastModifyingUser;
	private List<DriveUser> owners;
	private Boolean shared;
	private Boolean ownedByMe;
	private String originalFilename;
	private String fileExtension;
	private String md5Checksum;
	private Long size;
	private String driveId;
	private String resourceKey;
	private DriveCapabilities capabilities;
	private DriveShortcutDetails shortcutDetails;
	private String sharedWithMeTime;
	private DriveUser sharingUser;
}