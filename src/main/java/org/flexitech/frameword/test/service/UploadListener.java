package org.flexitech.frameword.test.service;

public interface UploadListener {
	void onProgress(long done, long total);
	boolean isCancelled();
}