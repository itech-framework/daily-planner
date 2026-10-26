package org.flexitech.framework.products.daily_planner.api.response;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CalendarEventList {

	@JsonProperty("summary")
	private String summary;

	@JsonProperty("timeZone")
	private String timeZone;

	@JsonProperty("nextPageToken")
	private String nextPageToken;

	@JsonProperty("items")
	private List<CalendarEvent> items = new ArrayList<CalendarEvent>();

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public String getTimeZone() {
		return timeZone;
	}

	public void setTimeZone(String timeZone) {
		this.timeZone = timeZone;
	}

	public String getNextPageToken() {
		return nextPageToken;
	}

	public void setNextPageToken(String nextPageToken) {
		this.nextPageToken = nextPageToken;
	}

	public List<CalendarEvent> getItems() {
		return items;
	}

	public void setItems(List<CalendarEvent> items) {
		this.items = items == null ? new ArrayList<CalendarEvent>() : items;
	}
}