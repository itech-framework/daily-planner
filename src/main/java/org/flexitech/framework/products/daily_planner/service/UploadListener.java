package org.flexitech.framework.products.daily_planner.service;

public interface UploadListener {
	void onProgress(long done, long total);
	boolean isCancelled();
}