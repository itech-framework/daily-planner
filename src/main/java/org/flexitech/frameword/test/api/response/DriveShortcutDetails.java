package org.flexitech.frameword.test.api.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DriveShortcutDetails {

	private String targetId;
	private String targetMimeType;
}