package org.flexitech.framework.products.daily_planner.ui.component;

import org.flexitech.framework.products.daily_planner.util.Pulse;

import io.github.itech_framework.core.annotations.constructor.DefaultConstructor;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.components.FxComponent;
import io.github.itech_framework.java_fx.annotations.components.Root;
import io.github.itech_framework.java_fx.loader.FxComponentLoader;
import javafx.fxml.FXML;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

@FxComponent("/views/components/agenda-skeleton.fxml")
public class AgendaSkeleton {

	@FXML
	@Root
	private HBox root;

	@DefaultConstructor
	public AgendaSkeleton() {
	}

	public static Region create() {
		return FxComponentLoader.load(AgendaSkeleton.class).root;
	}

	public static void fill(VBox box, int rows) {
		box.getChildren().clear();
		for (int i = 0; i < rows; i++) {
			box.getChildren().add(create());
		}
	}

	@OnInit
	private void bind() {
		Pulse.start(root);
	}
}