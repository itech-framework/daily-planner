package org.flexitech.frameword.test.api.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DriveFileListResponse {

	private String kind;
	private String nextPageToken;
	private Boolean incompleteSearch;
	private List<DriveFileResponse> files;
}