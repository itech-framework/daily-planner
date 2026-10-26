package org.flexitech.framework.products.daily_planner.api.request;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DriveFileRequest {

	private String name;
	private String mimeType;
	private String description;
	private List<String> parents;
	private Boolean starred;
	private Boolean trashed;
	private String folderColorRgb;
	private String createdTime;
	private String modifiedTime;
	private Boolean writersCanShare;
	private Boolean viewersCanCopyContent;
	private Map<String, String> properties;
	private Map<String, String> appProperties;
}