package org.flexitech.framework.products.daily_planner.service;

import org.flexitech.framework.products.daily_planner.model.PlannerSnapshot;

public interface PlannerService {

	PlannerSnapshot loadToday() throws Exception;
}