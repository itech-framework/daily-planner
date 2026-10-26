package org.flexitech.framework.products.daily_planner.api.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DriveShortcutDetails {

	private String targetId;
	private String targetMimeType;
}