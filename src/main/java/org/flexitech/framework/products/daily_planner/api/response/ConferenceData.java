package org.flexitech.framework.products.daily_planner.api.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConferenceData {

	private CreateRequest createRequest;
	private List<EntryPoint> entryPoints;
	private String conferenceId;

	public CreateRequest getCreateRequest() {
		return createRequest;
	}

	public void setCreateRequest(CreateRequest createRequest) {
		this.createRequest = createRequest;
	}

	public List<EntryPoint> getEntryPoints() {
		return entryPoints;
	}

	public void setEntryPoints(List<EntryPoint> entryPoints) {
		this.entryPoints = entryPoints;
	}

	public String getConferenceId() {
		return conferenceId;
	}

	public void setConferenceId(String conferenceId) {
		this.conferenceId = conferenceId;
	}

	public static ConferenceData newMeet(String requestId) {
		SolutionKey key = new SolutionKey();
		key.setType("hangoutsMeet");
		CreateRequest request = new CreateRequest();
		request.setRequestId(requestId);
		request.setConferenceSolutionKey(key);
		ConferenceData data = new ConferenceData();
		data.setCreateRequest(request);
		return data;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class CreateRequest {

		private String requestId;
		private SolutionKey conferenceSolutionKey;
		private Status status;

		public String getRequestId() {
			return requestId;
		}

		public void setRequestId(String requestId) {
			this.requestId = requestId;
		}

		public SolutionKey getConferenceSolutionKey() {
			return conferenceSolutionKey;
		}

		public void setConferenceSolutionKey(SolutionKey conferenceSolutionKey) {
			this.conferenceSolutionKey = conferenceSolutionKey;
		}

		public Status getStatus() {
			return status;
		}

		public void setStatus(Status status) {
			this.status = status;
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class SolutionKey {

		private String type;

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Status {

		private String statusCode;

		public String getStatusCode() {
			return statusCode;
		}

		public void setStatusCode(String statusCode) {
			this.statusCode = statusCode;
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class EntryPoint {

		private String entryPointType;
		private String uri;

		public String getEntryPointType() {
			return entryPointType;
		}

		public void setEntryPointType(String entryPointType) {
			this.entryPointType = entryPointType;
		}

		public String getUri() {
			return uri;
		}

		public void setUri(String uri) {
			this.uri = uri;
		}
	}
}