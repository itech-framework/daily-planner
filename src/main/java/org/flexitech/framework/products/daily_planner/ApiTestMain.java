package org.flexitech.framework.products.daily_planner;

import java.util.Objects;

import org.flexitech.framework.products.daily_planner.service.GoogleAuthService;
import org.flexitech.framework.products.daily_planner.ui.CalendarController;
import org.flexitech.framework.products.daily_planner.ui.DriveController;
import org.flexitech.framework.products.daily_planner.ui.LoginController;
import org.flexitech.framework.products.daily_planner.ui.PlannerController;
import org.flexitech.framework.products.daily_planner.ui.TasksController;
import org.flexitech.framework.products.daily_planner.util.Notifier;

import io.github.itech_framework.core.annotations.ComponentScan;
import io.github.itech_framework.core.annotations.api_client.EnableApiClient;
import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.jfx.EnableJavaFx;
import io.github.itech_framework.core.annotations.properties.Property;
import io.github.itech_framework.java_fx.ITechJavaFxApplication;
import io.github.itech_framework.java_fx.router.config.RouterConfig;
import io.github.itech_framework.java_fx.router.core.MiddlewareResult;
import io.github.itech_framework.java_fx.ui.layout.LayoutConfig;
import io.github.itech_framework.java_fx.ui.toast.Toast;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.HostServices;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.util.Duration;

@EnableJavaFx
@EnableApiClient
@ComponentScan(basePackage = "org.flexitech.framework.products.daily_planner")
public class ApiTestMain extends ITechJavaFxApplication {

	private static HostServices hostServices;
	private static Stage primaryStage;

	@Use
	private GoogleAuthService authService;
	
	@Property(key = "app.name", defaultValue = "Planner")
	private String appName;

	public static HostServices hostServices() {
		return hostServices;
	}

	public static Stage primaryStage() {
		return primaryStage;
	}

	@Override
	protected void onInit() throws Exception {
		RouterConfig config = router.getConfig();
		config.addStyleSheets(
				Objects.requireNonNull(ApiTestMain.class.getResource("/static/css/app.css")).toExternalForm());

		config.addTransition("fade", root -> {
			root.setOpacity(0);
			FadeTransition fade = new FadeTransition(Duration.millis(220), root);
			fade.setToValue(1);
			fade.play();
		});
		config.addTransition("rise", root -> {
		    root.setOpacity(0);
		    root.setTranslateX(14);
		    FadeTransition fade = new FadeTransition(Duration.millis(220), root);
		    fade.setToValue(1);
		    TranslateTransition move = new TranslateTransition(Duration.millis(220), root);
		    move.setToX(0);
		    
		    fade.setInterpolator(Interpolator.EASE_OUT);
		    move.setInterpolator(Interpolator.EASE_OUT);
		    
		    new ParallelTransition(fade, move).play();
		});

		config.addLayoutConfig("main", new LayoutConfig("/views/layout/main-layout.fxml"));

		config.addRouteMiddleware((current, next, arguments) -> {
			if ("login".equals(next.name()) || authService.hasSavedSession()) {
				return MiddlewareResult.proceed();
			}
			return MiddlewareResult.redirect("login", "Please sign in first.");
		});

		config.setErrorHandler((route, arguments, error) -> Notifier.error("Navigation failed", error.getMessage()));

		router.registerRoute("login", "/views/login-view.fxml", LoginController.class, "fade");
		router.registerRoute("planner", "/views/planner-view.fxml", PlannerController.class, "rise", "main");
		router.registerRoute("tasks", "/views/tasks-view.fxml", TasksController.class, "rise", "main");
		router.registerRoute("calendar", "/views/calendar-view.fxml", CalendarController.class, "rise", "main");
		router.registerRoute("drive", "/views/drive.fxml", DriveController.class, "rise", "main");
	}

	@Override
	public void start(Stage stage) throws Exception {
		hostServices = getHostServices();
		primaryStage = stage;
		stage.setTitle(appName);
		stage.setMinWidth(900);
		stage.setMinHeight(600);
		router.initialize(ApiTestMain.class, stage);
		router.to("login");
		stage.setMaximized(true);
		stage.getIcons().add(new Image(ApiTestMain.class.getResourceAsStream(Objects.requireNonNull("/static/images/logo.png"))));
		Toast.warmUp(stage);
		stage.show();
	}

	public static void main(String[] args) throws Exception {
		ITechJavaFxApplication.run(ApiTestMain.class, args);
	}
}