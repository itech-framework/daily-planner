package org.flexitech.framework.products.daily_planner.api;

import java.util.Map;

import org.flexitech.framework.products.daily_planner.api.response.GoogleUserInfo;

import io.github.itech_framework.api_client.annotations.ApiClient;
import io.github.itech_framework.api_client.annotations.methods.GET;
import io.github.itech_framework.api_client.annotations.parameters.Headers;

@ApiClient(baseUrl = "https://openidconnect.googleapis.com")
public interface GoogleUserInfoAPI {

	@GET("/v1/userinfo")
	GoogleUserInfo getUserInfo(@Headers Map<String, String> headers);
}