package org.flexitech.framework.products.daily_planner.util;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.util.Duration;

public final class Pulse {

	private static final String COUNT = "skeleton-count";
	private static final String ANIMATION_KEY = "pulse.animation";
	private static final double FROM = 1.0;
	private static final double TO = 0.4;

	private Pulse() {
	}

	public static void start(Node node) {
		stopAnimation(node);
		FadeTransition fade = new FadeTransition(Duration.millis(750), node);
		fade.setFromValue(FROM);
		fade.setToValue(TO);
		fade.setAutoReverse(true);
		fade.setCycleCount(Animation.INDEFINITE);
		node.getProperties().put(ANIMATION_KEY, fade);
		node.sceneProperty().addListener((observable, oldScene, newScene) -> {
			if (newScene == null) {
				fade.stop();
			}
		});
		fade.play();
	}

	public static void stop(Node node) {
		stopAnimation(node);
		node.setOpacity(FROM);
	}

	public static void mark(Label label, String placeholder) {
		label.setText(placeholder);
		if (!label.getStyleClass().contains(COUNT)) {
			label.getStyleClass().add(COUNT);
		}
		start(label);
	}

	public static void clear(Label label) {
		label.getStyleClass().remove(COUNT);
		stop(label);
	}

	private static void stopAnimation(Node node) {
		Object existing = node.getProperties().remove(ANIMATION_KEY);
		if (existing instanceof Animation running) {
			running.stop();
		}
	}
}