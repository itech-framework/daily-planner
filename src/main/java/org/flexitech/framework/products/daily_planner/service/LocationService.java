package org.flexitech.framework.products.daily_planner.service;

import org.flexitech.framework.products.daily_planner.model.GeoLocation;

public interface LocationService {

	GeoLocation getLocation(boolean refresh) throws Exception;
}