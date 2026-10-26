package org.flexitech.framework.products.daily_planner.session;

import org.flexitech.framework.products.daily_planner.api.response.GoogleUserInfo;

import io.github.itech_framework.core.annotations.components.Component;

@Component
public class UserSession {

	private volatile GoogleUserInfo user;

	public GoogleUserInfo getUser() {
		return user;
	}

	public void setUser(GoogleUserInfo user) {
		this.user = user;
	}

	public void clear() {
		this.user = null;
	}
}