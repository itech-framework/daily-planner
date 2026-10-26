package org.flexitech.framework.products.daily_planner.api.response;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTasksResponse {

	@JsonProperty("nextPageToken")
	private String nextPageToken;

	@JsonProperty("items")
	private List<GoogleTask> items = new ArrayList<GoogleTask>();

	public String getNextPageToken() {
		return nextPageToken;
	}

	public void setNextPageToken(String nextPageToken) {
		this.nextPageToken = nextPageToken;
	}

	public List<GoogleTask> getItems() {
		return items;
	}

	public void setItems(List<GoogleTask> items) {
		this.items = items == null ? new ArrayList<GoogleTask>() : items;
	}
}