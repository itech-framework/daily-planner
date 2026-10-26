package org.flexitech.framework.products.daily_planner.api;

import java.util.Map;

import org.flexitech.framework.products.daily_planner.api.response.GoogleTokenResponse;

import io.github.itech_framework.api_client.annotations.ApiClient;
import io.github.itech_framework.api_client.annotations.methods.Header;
import io.github.itech_framework.api_client.annotations.methods.POST;
import io.github.itech_framework.api_client.annotations.parameters.Body;

@ApiClient(baseUrl = "https://oauth2.googleapis.com")
public interface GoogleOAuthAPI {

	@POST("/token")
	@Header(name = "Content-Type", value = "application/x-www-form-urlencoded")
	GoogleTokenResponse requestToken(@Body Map<String, String> form);

	@POST("/revoke")
	@Header(name = "Content-Type", value = "application/x-www-form-urlencoded")
	void revoke(@Body Map<String, String> form);
}