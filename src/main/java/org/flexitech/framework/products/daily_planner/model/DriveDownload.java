package org.flexitech.framework.products.daily_planner.model;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

public record DriveDownload(InputStream stream, long size, Closeable resource) implements Closeable {

	@Override
	public void close() throws IOException {
		resource.close();
	}
}