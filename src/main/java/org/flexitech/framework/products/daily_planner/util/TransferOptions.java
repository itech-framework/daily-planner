package org.flexitech.framework.products.daily_planner.util;

public record TransferOptions(int connections) {

	public TransferOptions {
		connections = Math.max(1, connections);
	}
}