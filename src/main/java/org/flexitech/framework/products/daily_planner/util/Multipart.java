package org.flexitech.framework.products.daily_planner.util;

import java.nio.charset.StandardCharsets;

public final class Multipart {

	private static final String CRLF = "\r\n";

	private Multipart() {
	}

	public static String contentType(String boundary) {
		return "multipart/related; boundary=" + boundary;
	}

	public static byte[] related(String boundary, String metadataJson, String mediaType, String content) {
		String head = "--" + boundary + CRLF + "Content-Type: application/json; charset=UTF-8" + CRLF + CRLF
				+ metadataJson + CRLF + "--" + boundary + CRLF + "Content-Type: " + mediaType + "; charset=UTF-8"
				+ CRLF + CRLF;
		String tail = CRLF + "--" + boundary + "--" + CRLF;
		return (head + content + tail).getBytes(StandardCharsets.UTF_8);
	}
}