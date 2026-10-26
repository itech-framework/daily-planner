package org.flexitech.framework.products.daily_planner.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.itech_framework.api_client.exceptions.ApiException;
import io.github.itech_framework.api_client.exceptions.ForbiddenException;

public final class GoogleApiSupport {

	private static final Logger log = LogManager.getLogger(GoogleApiSupport.class);

	private GoogleApiSupport() {
	}

	public static Map<String, String> bearer(String accessToken) {
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("Authorization", "Bearer " + accessToken);
		return headers;
	}
	
	public static String bearer(String accessToken, boolean rawValue) {
	    return "Bearer " + accessToken;
	}

	public static <T> T call(String apiName, Callable<T> call) throws Exception {
		try {
			return call.call();
		} catch (ApiException e) {
			log.error("{} call failed: status={} code={} body={}", apiName, e.getStatusCode(), e.getErrorCode(),
					e.getResponseBody());
			if (e instanceof ForbiddenException) {
				throw new Exception(apiName + " access was denied. Check that the API is enabled in Google Cloud "
						+ "and that the permission was granted when you signed in.", e);
			}
			throw e;
		}
	}
}