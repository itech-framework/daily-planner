package org.flexitech.frameword.test.api.request;

import org.flexitech.frameword.test.api.response.EventDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CalendarEventRequest {
	private String summary;
	private String description;
	private String location;
	private EventDateTime start;
	private EventDateTime end;
}