package org.flexitech.framework.products.daily_planner.api.response;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTaskListsResponse {

	@JsonProperty("nextPageToken")
	private String nextPageToken;

	@JsonProperty("items")
	private List<GoogleTaskList> items = new ArrayList<GoogleTaskList>();

	public String getNextPageToken() {
		return nextPageToken;
	}

	public void setNextPageToken(String nextPageToken) {
		this.nextPageToken = nextPageToken;
	}

	public List<GoogleTaskList> getItems() {
		return items;
	}

	public void setItems(List<GoogleTaskList> items) {
		this.items = items == null ? new ArrayList<GoogleTaskList>() : items;
	}
}