package org.flexitech.framework.products.daily_planner.api.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DriveCapabilities {

	private Boolean canEdit;
	private Boolean canDelete;
	private Boolean canTrash;
	private Boolean canRename;
	private Boolean canAddChildren;
	private Boolean canDownload;
	private Boolean canCopy;
}