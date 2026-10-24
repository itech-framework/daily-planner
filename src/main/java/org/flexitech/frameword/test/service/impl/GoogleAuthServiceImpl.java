package org.flexitech.frameword.test.service.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.frameword.test.api.GoogleOAuthAPI;
import org.flexitech.frameword.test.api.GoogleUserInfoAPI;
import org.flexitech.frameword.test.api.response.GoogleTokenResponse;
import org.flexitech.frameword.test.api.response.GoogleUserInfo;
import org.flexitech.frameword.test.service.GoogleAuthService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.itech_framework.api_client.exceptions.UnauthorizedException;
import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.properties.Property;
import io.github.itech_framework.core.annotations.storage.DataStorage;
import io.github.itech_framework.core.utils.DataStorageUtil;

@Service
public class GoogleAuthServiceImpl implements GoogleAuthService {

	private static final Logger log = LogManager.getLogger(GoogleAuthServiceImpl.class);

	private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
	private static final long LOGIN_TIMEOUT_SECONDS = 300;
	private static final String KEY_ACCESS = "googleAccessToken";
	private static final String KEY_REFRESH = "googleRefreshToken";
	private static final String KEY_EXPIRES = "googleExpiresAt";

	@Property(key = "google.oauth.client-id")
	private String clientId;

	@Property(key = "google.oauth.client-secret")
	private String clientSecret;

	@Property(key = "google.oauth.scope", defaultValue = "openid email profile")
	private String scope;

	@Use
	private GoogleOAuthAPI googleOAuthAPI;

	@Use
	private GoogleUserInfoAPI googleUserInfoAPI;

	@DataStorage(key = KEY_ACCESS)
	private String accessToken;

	@DataStorage(key = KEY_REFRESH)
	private String refreshToken;

	@DataStorage(key = KEY_EXPIRES)
	private Long expiresAt;

	private final SecureRandom random = new SecureRandom();

	private volatile HttpServer callbackServer;
	private volatile CompletableFuture<String> codeFuture;
	private volatile String codeVerifier;
	private volatile String redirectUri;

	@Override
	public boolean isConfigured() {
		boolean configured = notBlank(clientId) && notBlank(clientSecret) && !clientId.startsWith("YOUR_")
				&& !clientSecret.startsWith("YOUR_");
		if (!configured) {
			log.warn("Google OAuth client id or secret is missing in application.properties");
		}
		return configured;
	}

	@Override
	public boolean hasSavedSession() {
		return notBlank(accessToken) || notBlank(refreshToken);
	}

	@Override
	public boolean restoreSession() {
		if (notBlank(accessToken) && !isExpired()) {
			log.debug("Saved access token is still valid");
			return true;
		}
		if (notBlank(refreshToken)) {
			log.info("Saved access token is missing or expired, refreshing");
			return refreshAccessToken();
		}
		log.info("No saved Google session to restore");
		return false;
	}

	@Override
	public synchronized String beginLogin() throws Exception {
		stopServer();

		codeVerifier = randomToken(48);
		String state = randomToken(16);
		CompletableFuture<String> future = new CompletableFuture<String>();
		codeFuture = future;

		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
		server.createContext("/callback", exchange -> handleCallback(exchange, state, future));
		server.start();
		callbackServer = server;
		redirectUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/callback";
		log.info("Google login started, waiting for the callback on {}", redirectUri);

		return AUTH_ENDPOINT + "?client_id=" + encode(clientId.trim()) + "&redirect_uri=" + encode(redirectUri)
				+ "&response_type=code&scope=" + encode(scope) + "&state=" + encode(state)
				+ "&code_challenge=" + encode(challengeFor(codeVerifier)) + "&code_challenge_method=S256"
				+ "&access_type=offline&prompt=consent";
	}

	@Override
	public void awaitLogin() throws Exception {
		CompletableFuture<String> future = codeFuture;
		if (future == null) {
			throw new Exception("Login was not started.");
		}

		String code;
		try {
			code = future.get(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			log.warn("Google login timed out after {} seconds", LOGIN_TIMEOUT_SECONDS);
			throw new Exception("The login timed out before it was approved.");
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			log.info("Google login ended without a code: {}", cause == null ? e.getMessage() : cause.getMessage());
			if (cause instanceof Exception) {
				throw (Exception) cause;
			}
			throw e;
		} finally {
			stopServer();
		}

		log.info("Authorization code received, exchanging it for tokens");
		Map<String, String> form = new HashMap<String, String>();
		form.put("client_id", clientId.trim());
		form.put("client_secret", clientSecret.trim());
		form.put("code", code);
		form.put("code_verifier", codeVerifier);
		form.put("redirect_uri", redirectUri);
		form.put("grant_type", "authorization_code");

		storeTokens(googleOAuthAPI.requestToken(form));
		log.info("Google login completed");
	}

	@Override
	public void cancelLogin() {
		CompletableFuture<String> future = codeFuture;
		if (future != null && !future.isDone()) {
			log.info("Google login cancelled by the user");
			future.completeExceptionally(new Exception(CANCELLED_MESSAGE));
		}
	}

	@Override
	public GoogleUserInfo getUserInfo() throws Exception {
		try {
			return googleUserInfoAPI.getUserInfo(bearerHeaders());
		} catch (UnauthorizedException e) {
			log.info("Google rejected the access token, trying a refresh");
			if (!notBlank(refreshToken) || !refreshAccessToken()) {
				throw e;
			}
			return googleUserInfoAPI.getUserInfo(bearerHeaders());
		}
	}

	@Override
	public String getAccessToken() throws Exception {
		if (isExpired() && !(notBlank(refreshToken) && refreshAccessToken())) {
			log.warn("Google session expired and could not be refreshed");
			throw new Exception("The Google session expired. Please sign in again.");
		}
		return accessToken;
	}

	@Override
	public void logout(boolean revokeRemote) {
		log.info("Signing out of Google, revoke remote token: {}", revokeRemote);
		if (revokeRemote) {
			String token = notBlank(refreshToken) ? refreshToken : accessToken;
			if (notBlank(token)) {
				Map<String, String> form = new HashMap<String, String>();
				form.put("token", token);
				googleOAuthAPI.revoke(form);
			}
		}
		clearTokens();
	}

	private void handleCallback(HttpExchange exchange, String expectedState, CompletableFuture<String> future)
			throws IOException {
		Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());

		if (!expectedState.equals(query.get("state"))) {
			log.warn("Callback rejected because the state value did not match");
			respond(exchange, 400, "Invalid request. You can close this tab.");
			return;
		}

		if (query.get("error") != null) {
			log.warn("Google returned an error on the callback: {}", query.get("error"));
			future.completeExceptionally(new Exception("Google sign-in failed: " + query.get("error")));
			respond(exchange, 200, "Sign-in was not completed. You can close this tab.");
			return;
		}

		String code = query.get("code");
		if (code == null) {
			log.warn("Callback arrived without an authorization code");
			respond(exchange, 400, "Missing authorization code. You can close this tab.");
			return;
		}

		future.complete(code);
		respond(exchange, 200, "Signed in. You can close this tab and return to the app.");
	}

	private void respond(HttpExchange exchange, int status, String message) throws IOException {
		byte[] body = ("<html><body style=\"font-family:sans-serif;padding:40px\"><h3>" + message
				+ "</h3></body></html>").getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
		exchange.sendResponseHeaders(status, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

	private void stopServer() {
		HttpServer server = callbackServer;
		callbackServer = null;
		if (server != null) {
			server.stop(1);
			log.debug("Local callback server stopped");
		}
	}

	private boolean refreshAccessToken() {
		Map<String, String> form = new HashMap<String, String>();
		form.put("client_id", clientId.trim());
		form.put("client_secret", clientSecret.trim());
		form.put("grant_type", "refresh_token");
		form.put("refresh_token", refreshToken);

		try {
			storeTokens(googleOAuthAPI.requestToken(form));
			log.info("Google access token refreshed");
			return true;
		} catch (Exception e) {
			log.warn("Token refresh failed, clearing the saved session: {}", e.getMessage());
			clearTokens();
			return false;
		}
	}

	private Map<String, String> bearerHeaders() {
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("Authorization", "Bearer " + accessToken);
		return headers;
	}

	private void storeTokens(GoogleTokenResponse token) throws Exception {
		if (token == null || token.getAccessToken() == null) {
			throw new Exception("Google did not return an access token!");
		}
		accessToken = token.getAccessToken();
		if (token.getRefreshToken() != null) {
			refreshToken = token.getRefreshToken();
		}
		long lifetimeSeconds = token.getExpiresIn() == null ? 3600 : token.getExpiresIn();
		expiresAt = System.currentTimeMillis() / 1000 + lifetimeSeconds;
		persist();
		log.debug("Tokens stored, access token valid for {} seconds", lifetimeSeconds);
	}

	private void clearTokens() {
		accessToken = null;
		refreshToken = null;
		expiresAt = null;
		DataStorageUtil.save(KEY_ACCESS, "");
		DataStorageUtil.save(KEY_REFRESH, "");
		DataStorageUtil.save(KEY_EXPIRES, 0L);
		log.debug("Saved tokens cleared");
	}

	private void persist() {
		if (accessToken != null) {
			DataStorageUtil.save(KEY_ACCESS, accessToken);
		}
		if (refreshToken != null) {
			DataStorageUtil.save(KEY_REFRESH, refreshToken);
		}
		if (expiresAt != null) {
			DataStorageUtil.save(KEY_EXPIRES, expiresAt);
		}
	}

	private boolean isExpired() {
		return expiresAt == null || expiresAt == 0L || System.currentTimeMillis() / 1000 >= expiresAt - 60;
	}

	private String randomToken(int bytes) {
		byte[] data = new byte[bytes];
		random.nextBytes(data);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
	}

	private String challengeFor(String verifier) throws Exception {
		byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
		return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
	}

	private Map<String, String> parseQuery(String rawQuery) {
		Map<String, String> result = new HashMap<String, String>();
		if (rawQuery == null || rawQuery.isEmpty()) {
			return result;
		}
		for (String pair : rawQuery.split("&")) {
			int index = pair.indexOf('=');
			String key = index < 0 ? pair : pair.substring(0, index);
			String value = index < 0 ? "" : pair.substring(index + 1);
			result.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
		}
		return result;
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}
}