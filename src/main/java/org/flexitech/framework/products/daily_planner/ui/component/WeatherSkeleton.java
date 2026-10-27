package org.flexitech.framework.products.daily_planner.ui.component;

import org.flexitech.framework.products.daily_planner.util.Pulse;

import io.github.itech_framework.core.annotations.constructor.DefaultConstructor;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.components.FxComponent;
import io.github.itech_framework.java_fx.annotations.components.Root;
import io.github.itech_framework.java_fx.loader.FxComponentLoader;
import javafx.fxml.FXML;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

@FxComponent("/views/components/weather-skeleton.fxml")
public class WeatherSkeleton {

	@FXML
	@Root
	private VBox root;

	@DefaultConstructor
	public WeatherSkeleton() {
	}

	public static Region create() {
		return FxComponentLoader.load(WeatherSkeleton.class).root;
	}

	public static void fill(VBox box) {
		box.getChildren().setAll(create());
	}

	@OnInit
	private void bind() {
		Pulse.start(root);
	}
}