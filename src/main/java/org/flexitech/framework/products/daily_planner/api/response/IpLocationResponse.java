package org.flexitech.framework.products.daily_planner.api.response;

public class IpLocationResponse {

	private Boolean success;
	private String message;
	private String city;
	private String region;
	private String country;
	private Double latitude;
	private Double longitude;
	private IpLocationTimezone timezone;

	public IpLocationTimezone getTimezone() {
		return timezone;
	}

	public void setTimezone(IpLocationTimezone timezone) {
		this.timezone = timezone;
	}

	public Boolean getSuccess() {
		return success;
	}

	public void setSuccess(Boolean success) {
		this.success = success;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public Double getLatitude() {
		return latitude;
	}

	public void setLatitude(Double latitude) {
		this.latitude = latitude;
	}

	public Double getLongitude() {
		return longitude;
	}

	public void setLongitude(Double longitude) {
		this.longitude = longitude;
	}
}