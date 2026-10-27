package org.flexitech.framework.products.daily_planner.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.framework.products.daily_planner.api.IpLocationAPI;
import org.flexitech.framework.products.daily_planner.api.response.IpLocationResponse;
import org.flexitech.framework.products.daily_planner.model.GeoLocation;
import org.flexitech.framework.products.daily_planner.service.LocationService;

import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_framework.core.annotations.dependency.Use;

@Service
public class LocationServiceImpl implements LocationService {

	private static final Logger log = LogManager.getLogger(LocationServiceImpl.class);
	private static final String FIELDS = "success,message,city,region,country,latitude,longitude,timezone.id";
	
	@Use
	private IpLocationAPI locationAPI;

	private volatile GeoLocation cached;

	@Override
	public GeoLocation getLocation(boolean refresh) throws Exception {
		GeoLocation current = cached;
		if (!refresh && current != null) {
			return current;
		}
		try {
			GeoLocation found = lookup();
			cached = found;
			return found;
		} catch (Exception e) {
			if (current != null) {
				log.warn("Location lookup failed, keeping the previous location: {}", e.getMessage());
				return current;
			}
			throw e;
		}
	}

	private GeoLocation lookup() throws Exception {
		log.info("Detecting location from the public IP address");
		IpLocationResponse response;
		try {
			response = locationAPI.locate(FIELDS);
		} catch (Exception e) {
			throw new Exception("Your location could not be detected: " + e.getMessage(), e);
		}
		if (response == null || !Boolean.TRUE.equals(response.getSuccess()) || response.getLatitude() == null
				|| response.getLongitude() == null) {
			String reason = response == null || isBlank(response.getMessage()) ? "no data returned"
					: response.getMessage();
			throw new Exception("Your location could not be detected: " + reason);
		}
		String zone = response.getTimezone() == null ? null : response.getTimezone().getId();
		GeoLocation location = new GeoLocation(label(response), response.getLatitude(), response.getLongitude(), zone);
		log.info("Detected location: {}", location.name());
		return location;
	}

	private static String label(IpLocationResponse response) {
		List<String> parts = new ArrayList<String>();
		String place = isBlank(response.getCity()) ? response.getRegion() : response.getCity();
		if (!isBlank(place)) {
			parts.add(place.trim());
		}
		if (!isBlank(response.getCountry())) {
			parts.add(response.getCountry().trim());
		}
		return parts.isEmpty() ? "Current location" : String.join(", ", parts);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}