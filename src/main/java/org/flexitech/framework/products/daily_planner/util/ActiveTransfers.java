package org.flexitech.framework.products.daily_planner.util;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ActiveTransfers {

	private static final Set<String> ACTIVE = ConcurrentHashMap.newKeySet();

	private ActiveTransfers() {
	}

	static String key(String kind, Path path, String scope) {
		return kind + "|" + path.toAbsolutePath().normalize() + "|" + (scope == null ? "" : scope);
	}

	static boolean acquire(String key) {
		return ACTIVE.add(key);
	}

	static void release(String key) {
		ACTIVE.remove(key);
	}
}