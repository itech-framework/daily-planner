package org.flexitech.frameword.test.service;

import org.flexitech.frameword.test.model.PlannerSnapshot;

public interface PlannerService {

	PlannerSnapshot loadToday() throws Exception;
}