package org.flexitech.framework.products.daily_planner.ui.component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

import org.flexitech.framework.products.daily_planner.model.WeatherDay;
import org.flexitech.framework.products.daily_planner.util.WeatherCodes;

import io.github.itech_framework.core.annotations.constructor.DefaultConstructor;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.components.FxComponent;
import io.github.itech_framework.java_fx.annotations.components.Root;
import io.github.itech_framework.java_fx.loader.FxComponentLoader;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

@FxComponent("/views/components/weather-day.fxml")
public class WeatherDayRow {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, d MMM");
	private static final String DEGREE = "\u00B0";

	@FXML
	@Root
	private HBox root;

	@FXML
	private Label nameLabel;

	@FXML
	private Label iconLabel;

	@FXML
	private Label rainLabel;

	@FXML
	private Label minLabel;

	@FXML
	private Label maxLabel;

	private final WeatherDay day;

	@DefaultConstructor
	public WeatherDayRow() {
		this(null);
	}

	public WeatherDayRow(WeatherDay day) {
		this.day = day;
	}

	public static Region create(WeatherDay day) {
		return FxComponentLoader.load(WeatherDayRow.class, day).root;
	}

	@OnInit
	private void bind() {
		if (day == null) {
			return;
		}
		WeatherCodes.Kind kind = WeatherCodes.kind(day.code(), true);
		nameLabel.setText(name());
		iconLabel.setText(kind.glyph());
		iconLabel.getStyleClass().add(kind.styleClass());
		rainLabel.setText(day.rainChance() + "%");
		minLabel.setText(Math.round(day.min()) + DEGREE);
		maxLabel.setText(Math.round(day.max()) + DEGREE);
		Tooltip.install(root, new Tooltip(DAY.format(day.date()) + "\n" + WeatherCodes.describe(day.code()) + ", "
				+ day.rainChance() + "% chance of rain"));
	}

	private String name() {
		LocalDate today = LocalDate.now();
		if (day.date().equals(today)) {
			return "Today";
		}
		if (day.date().equals(today.plusDays(1))) {
			return "Tomorrow";
		}
		return day.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
	}
}