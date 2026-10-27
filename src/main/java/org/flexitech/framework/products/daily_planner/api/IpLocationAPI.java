package org.flexitech.framework.products.daily_planner.api;

import org.flexitech.framework.products.daily_planner.api.response.IpLocationResponse;

import io.github.itech_framework.api_client.annotations.ApiClient;
import io.github.itech_framework.api_client.annotations.methods.GET;
import io.github.itech_framework.api_client.annotations.parameters.Query;

@ApiClient(baseUrl = "https://ipwho.is")
public interface IpLocationAPI {

	@GET("/")
	IpLocationResponse locate(@Query("fields") String fields);
}