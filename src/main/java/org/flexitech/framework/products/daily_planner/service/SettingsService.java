package org.flexitech.framework.products.daily_planner.service;

import java.util.Map;

import org.flexitech.framework.products.daily_planner.model.Setting;

public interface SettingsService {

	String getString(Setting setting);

	int getInt(Setting setting);

	String getDefault(Setting setting);

	boolean isOverridden(Setting setting);

	void save(Map<Setting, String> values) throws Exception;

	void reset(Setting setting) throws Exception;

	void resetAll() throws Exception;
}