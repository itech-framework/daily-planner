package org.flexitech.frameword.test.api.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DriveUser {

	private String kind;
	private String displayName;
	private String emailAddress;
	private String permissionId;
	private String photoLink;
	private Boolean me;
}