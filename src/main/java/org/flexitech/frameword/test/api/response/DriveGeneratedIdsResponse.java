package org.flexitech.frameword.test.api.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DriveGeneratedIdsResponse {

	private String kind;
	private String space;
	private List<String> ids;
}