package org.flexitech.framework.products.daily_planner.util;

import java.net.NetworkInterface;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.framework.products.daily_planner.model.GeoLocation;

public final class VpnCheck {

	private static final Logger log = LogManager.getLogger(VpnCheck.class);
	private static final List<String> ADAPTER_HINTS = List.of("vpn", "tap-windows", "wintun", "wireguard", "nordlynx",
			"anyconnect", "globalprotect", "fortinet");

	private VpnCheck() {
	}

	public static boolean suspected(GeoLocation location) {
		return adapterActive() || timeZoneDiffers(location == null ? null : location.timeZone());
	}

	static boolean adapterActive() {
		try {
			for (NetworkInterface adapter : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (!adapter.isUp() || adapter.isLoopback()) {
					continue;
				}
				String label = (adapter.getName() + " " + adapter.getDisplayName()).toLowerCase();
				for (String hint : ADAPTER_HINTS) {
					if (label.contains(hint)) {
						log.info("VPN adapter detected: {}", adapter.getDisplayName());
						return true;
					}
				}
			}
		} catch (Exception e) {
			log.debug("Network adapters could not be checked: {}", e.getMessage());
		}
		return false;
	}

	static boolean timeZoneDiffers(String detectedZone) {
		if (detectedZone == null || detectedZone.isBlank()) {
			return false;
		}
		try {
			ZonedDateTime detected = ZonedDateTime.now(ZoneId.of(detectedZone));
			ZonedDateTime local = ZonedDateTime.now(ZoneId.systemDefault());
			boolean differs = !detected.getOffset().equals(local.getOffset());
			if (differs) {
				log.info("Detected time zone {} differs from the system time zone", detectedZone);
			}
			return differs;
		} catch (Exception e) {
			log.debug("Time zone {} could not be compared: {}", detectedZone, e.getMessage());
			return false;
		}
	}
}