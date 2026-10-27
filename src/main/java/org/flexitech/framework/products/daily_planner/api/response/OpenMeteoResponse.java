package org.flexitech.framework.products.daily_planner.api.response;

public class OpenMeteoResponse {

	private OpenMeteoCurrent current;
	private OpenMeteoDaily daily;

	public OpenMeteoCurrent getCurrent() {
		return current;
	}

	public void setCurrent(OpenMeteoCurrent current) {
		this.current = current;
	}

	public OpenMeteoDaily getDaily() {
		return daily;
	}

	public void setDaily(OpenMeteoDaily daily) {
		this.daily = daily;
	}
}