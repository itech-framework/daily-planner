package org.flexitech.frameword.test.service;

import org.flexitech.frameword.test.api.response.GoogleUserInfo;

public interface GoogleAuthService {

	String CANCELLED_MESSAGE = "Login was cancelled.";

	boolean isConfigured();

	boolean hasSavedSession();

	boolean restoreSession();

	String beginLogin() throws Exception;

	void awaitLogin() throws Exception;

	void cancelLogin();

	GoogleUserInfo getUserInfo() throws Exception;

	String getAccessToken() throws Exception;

	void logout(boolean revokeRemote);

	static boolean isCancelled(Exception error) {
		return error != null && CANCELLED_MESSAGE.equals(error.getMessage());
	}
}