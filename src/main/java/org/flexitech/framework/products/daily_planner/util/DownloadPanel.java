package org.flexitech.framework.products.daily_planner.util;

import org.flexitech.framework.products.daily_planner.DailyPlannerAppMain;

import io.github.itech_framework.java_fx.ui.toast.Toast;
import io.github.itech_framework.java_fx.ui.toast.ToastPosition;
import io.github.itech_framework.java_fx.ui.toast.ToastType;

public final class DownloadPanel {

	private static final double WIDTH = 344;
	private static final double MARGIN_RIGHT = 4;
	private static final double MARGIN_BOTTOM = 24;
	private static final String GROUP = "transfers";
	private static final String STYLE = "transfer-toast";

	private DownloadPanel() {
	}

	public static Transfer add(String fileName, Transfer.Direction direction) {
		return new Transfer(fileName, direction);
	}

	static Toast.ToastBuilder toastBuilder() {
		return Toast.builder()
				.owner(DailyPlannerAppMain.primaryStage())
				.type(ToastType.CUSTOM)
				.group(GROUP)
				.position(ToastPosition.BOTTOM_RIGHT)
				.offsetX(MARGIN_RIGHT)
				.offsetY(MARGIN_BOTTOM)
				.width(WIDTH)
				.maxWidth(WIDTH)
				.autoHide(false)
				.showProgress(true)
				.singleLineMessage(true)
				.customStyleClass(STYLE);
	}
}