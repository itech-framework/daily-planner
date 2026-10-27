package org.flexitech.framework.products.daily_planner.ui.component;

import java.time.format.DateTimeFormatter;

import org.flexitech.framework.products.daily_planner.model.WeatherCurrent;
import org.flexitech.framework.products.daily_planner.model.WeatherDay;
import org.flexitech.framework.products.daily_planner.model.WeatherSnapshot;
import org.flexitech.framework.products.daily_planner.util.WeatherCodes;

import io.github.itech_framework.core.annotations.constructor.DefaultConstructor;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.components.FxComponent;
import io.github.itech_framework.java_fx.annotations.components.Root;
import io.github.itech_framework.java_fx.loader.FxComponentLoader;
import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

@FxComponent("/views/components/weather-card.fxml")
public class WeatherCard {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final String DEGREE = "\u00B0";

	@FXML
	@Root
	private VBox root;

	@FXML
	private Label locationLabel;

	@FXML
	private Label updatedLabel;

	@FXML
	private Label warningLabel;

	@FXML
	private Label temperatureLabel;

	@FXML
	private Label descriptionLabel;

	@FXML
	private Label feelsLabel;

	@FXML
	private Label iconLabel;

	@FXML
	private Label humidityLabel;

	@FXML
	private Label windLabel;

	@FXML
	private Label rainLabel;

	@FXML
	private TitledPane forecastPane;

	@FXML
	private VBox forecastBox;

	@FXML
	private Hyperlink attributionLink;

	private final WeatherSnapshot snapshot;
	private final Runnable onAttribution;

	@DefaultConstructor
	public WeatherCard() {
		this(null, null);
	}

	public WeatherCard(WeatherSnapshot snapshot, Runnable onAttribution) {
		this.snapshot = snapshot;
		this.onAttribution = onAttribution;
	}

	public static Region create(WeatherSnapshot snapshot, Runnable onAttribution) {
		return FxComponentLoader.load(WeatherCard.class, snapshot, onAttribution).root;
	}

	@OnInit
	private void bind() {
		attributionLink.setOnAction(event -> {
			if (onAttribution != null) {
				onAttribution.run();
			}
		});
		forecastPane.setExpanded(false);
		if (snapshot == null) {
			return;
		}

		warningLabel.setVisible(snapshot.locationUncertain());
		warningLabel.setManaged(snapshot.locationUncertain());

		WeatherCurrent current = snapshot.current();
		WeatherCodes.Kind kind = WeatherCodes.kind(current.code(), current.day());

		locationLabel.setText(snapshot.location());
		updatedLabel.setText("Updated " + TIME.format(snapshot.loadedAt()));
		temperatureLabel.setText(degrees(current.temperature()));
		descriptionLabel.setText(WeatherCodes.describe(current.code()));
		feelsLabel.setText("Feels like " + degrees(current.feelsLike()));
		iconLabel.setText(kind.glyph());
		iconLabel.getStyleClass().add(kind.styleClass());
		humidityLabel.setText(current.humidity() + "%");
		windLabel.setText(Math.round(current.windSpeed()) + " km/h");
		rainLabel.setText(snapshot.days().isEmpty() ? "--" : snapshot.days().get(0).rainChance() + "%");

		for (WeatherDay day : snapshot.days()) {
			forecastBox.getChildren().add(WeatherDayRow.create(day));
		}
		forecastPane.setText(snapshot.days().size() + "-day forecast");
	}

	private static String degrees(double value) {
		return Math.round(value) + DEGREE;
	}
}