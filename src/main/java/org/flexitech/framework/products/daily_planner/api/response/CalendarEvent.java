package org.flexitech.framework.products.daily_planner.api.response;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CalendarEvent {

	@JsonProperty("id")
	private String id;

	@JsonProperty("status")
	private String status;

	@JsonProperty("summary")
	private String summary;

	@JsonProperty("description")
	private String description;

	@JsonProperty("location")
	private String location;

	@JsonProperty("htmlLink")
	private String htmlLink;

	@JsonProperty("hangoutLink")
	private String hangoutLink;

	@JsonProperty("colorId")
	private String colorId;

	@JsonProperty("start")
	private EventDateTime start;

	@JsonProperty("end")
	private EventDateTime end;
	
	@JsonProperty("etag")
	private String etag;

	@JsonProperty("recurringEventId")
	private String recurringEventId;

	private final Map<String, Object> extras = new LinkedHashMap<String, Object>();


	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getHtmlLink() {
		return htmlLink;
	}

	public void setHtmlLink(String htmlLink) {
		this.htmlLink = htmlLink;
	}

	public String getHangoutLink() {
		return hangoutLink;
	}

	public void setHangoutLink(String hangoutLink) {
		this.hangoutLink = hangoutLink;
	}

	public String getColorId() {
		return colorId;
	}

	public void setColorId(String colorId) {
		this.colorId = colorId;
	}

	public EventDateTime getStart() {
		return start;
	}

	public void setStart(EventDateTime start) {
		this.start = start;
	}

	public EventDateTime getEnd() {
		return end;
	}

	public void setEnd(EventDateTime end) {
		this.end = end;
	}
	
	public String getEtag() {
		return etag;
	}

	public void setEtag(String etag) {
		this.etag = etag;
	}

	public String getRecurringEventId() {
		return recurringEventId;
	}

	public void setRecurringEventId(String recurringEventId) {
		this.recurringEventId = recurringEventId;
	}

	@JsonAnyGetter
	public Map<String, Object> getExtras() {
		return extras;
	}

	@JsonAnySetter
	public void putExtra(String key, Object value) {
		extras.put(key, value);
	}
}