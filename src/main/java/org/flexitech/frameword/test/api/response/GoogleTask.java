package org.flexitech.frameword.test.api.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTask {

	public static final String STATUS_OPEN = "needsAction";
	public static final String STATUS_DONE = "completed";

	@JsonProperty("id")
	private String id;

	@JsonProperty("title")
	private String title;

	@JsonProperty("notes")
	private String notes;

	@JsonProperty("status")
	private String status;

	@JsonProperty("due")
	private String due;

	@JsonProperty("completed")
	private String completed;

	@JsonProperty("updated")
	private String updated;

	@JsonProperty("parent")
	private String parent;

	@JsonProperty("webViewLink")
	private String webViewLink;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getDue() {
		return due;
	}

	public void setDue(String due) {
		this.due = due;
	}

	public String getCompleted() {
		return completed;
	}

	public void setCompleted(String completed) {
		this.completed = completed;
	}

	public String getUpdated() {
		return updated;
	}

	public void setUpdated(String updated) {
		this.updated = updated;
	}

	public String getParent() {
		return parent;
	}

	public void setParent(String parent) {
		this.parent = parent;
	}

	public String getWebViewLink() {
		return webViewLink;
	}

	public void setWebViewLink(String webViewLink) {
		this.webViewLink = webViewLink;
	}

	public boolean isDone() {
		return STATUS_DONE.equals(status);
	}
}