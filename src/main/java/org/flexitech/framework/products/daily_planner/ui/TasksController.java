package org.flexitech.framework.products.daily_planner.ui;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.flexitech.framework.products.daily_planner.DailyPlannerAppMain;
import org.flexitech.framework.products.daily_planner.app.AppEvents;
import org.flexitech.framework.products.daily_planner.model.AgendaItem;
import org.flexitech.framework.products.daily_planner.service.TaskService;
import org.flexitech.framework.products.daily_planner.ui.component.AgendaCard;
import org.flexitech.framework.products.daily_planner.util.Notifier;

import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.FxController;
import io.github.itech_framework.java_fx.events.EventBus;
import io.github.itech_framework.java_fx.input.validations.FormValidator;
import io.github.itech_framework.java_fx.input.validations.validator.ValidationResult;
import io.github.itech_framework.java_fx.input.validations.validator.Validator;
import io.github.itech_framework.java_fx.router.core.Routable;
import io.github.itech_framework.java_fx.ui.dialog.ModalDialog;
import io.github.itech_framework.java_fx.utils.concurrent.TaskScope;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

@FxController
public class TasksController implements Routable {

	@Use
	private TaskService taskService;

	@FXML
	private ProgressIndicator progress;

	@FXML
	private TextField filterField;

	@FXML
	private CheckBox showCompletedBox;

	@FXML
	private Button refreshButton;

	@FXML
	private VBox listBox;

	private final TaskScope background = new TaskScope();
	
	private volatile boolean disposed;
	private List<AgendaItem> allTasks = new ArrayList<AgendaItem>();

	@OnInit
	public void initialized() {
		filterField.textProperty().addListener((observable, oldValue, newValue) -> applyFilter());
		showCompletedBox.setOnAction(event -> load());
	}

	@Override
	public void onNavigate(Object arguments) {
		load();
	}

	@Override
	public void onResume() {
		disposed = false;
		load();
	}

	@Override
	public void dispose() {
		disposed = true;
		background.cancelAll();
	}

	@FXML
	private void onRefresh() {
		load();
	}

	@FXML
	private void onNewTask() {
		TextField title = new TextField();
		title.setPromptText("Title");
		TextArea notes = new TextArea();
		notes.setPromptText("Notes (optional)");
		notes.setPrefRowCount(3);
		DatePicker due = new DatePicker();
		due.setPromptText("Due date (optional)");
		Label heading = new Label("New task");
		heading.getStyleClass().add("alert-title");

		VBox form = new VBox(10, heading, title, notes, due);
		form.setPrefWidth(380);

		Validator<String> maxLength = value -> new ValidationResult(value.trim().length() <= 200,
				"Title can be 200 characters at most");
		FormValidator validator = new FormValidator().addRequiredField(title)
				.addRule(title, maxLength, "Title can be 200 characters at most")
				.addCustomRule(() -> due.getValue() == null || !due.getValue().isBefore(LocalDate.now())
						? ValidationResult.valid()
						: ValidationResult.invalid("The due date is in the past"), due);

		ModalDialog[] holder = new ModalDialog[1];
		holder[0] = ModalDialog.builder().addOwner(DailyPlannerAppMain.primaryStage()).addContent(form).addCloseButton("Cancel")
				.addActionButton("Create", () -> {
					if (!validator.validate()) {
						return;
					}
					holder[0].close();
					createTask(title.getText(), notes.getText(), due.getValue());
				}).build();
		holder[0].show();
	}

	private void createTask(String title, String notes, LocalDate due) {
		progress.setVisible(true);
		background.run(() -> taskService.createTask(title, notes, due), created -> {
			Notifier.success("Task created", created.title());
			load();
		}, error -> {
			progress.setVisible(false);
			Notifier.error("Could not create the task", error.getMessage());
		});
	}

	private void load() {
		progress.setVisible(true);
		refreshButton.setDisable(true);
		background.run(() -> taskService.getTasks(showCompletedBox.isSelected()), tasks -> {
			finishLoading();
			if (disposed) {
				return;
			}
			allTasks = tasks;
			applyFilter();
			EventBus.publish(AppEvents.STATUS, tasks.size() + " tasks loaded");
		}, error -> {
			finishLoading();
			Notifier.error("Could not load tasks", error.getMessage());
			EventBus.publish(AppEvents.STATUS, "Tasks failed to load");
		});
	}

	private void toggle(AgendaItem item) {
		background.run(() -> taskService.setDone(item, item.done()), updated -> {
			Notifier.success(item.done() ? "Task completed" : "Task reopened", item.title());
			load();
		}, error -> {
			Notifier.error("Could not update the task", error.getMessage());
			load();
		});
	}

	private void applyFilter() {
		String needle = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase(Locale.ROOT);
		Map<String, List<AgendaItem>> groups = new LinkedHashMap<String, List<AgendaItem>>();
		for (AgendaItem task : allTasks) {
			if (!needle.isEmpty() && !matches(task, needle)) {
				continue;
			}
			String list = task.listTitle() == null ? "Tasks" : task.listTitle();
			groups.computeIfAbsent(list, key -> new ArrayList<AgendaItem>()).add(task);
		}

		listBox.getChildren().clear();
		if (groups.isEmpty()) {
			Label empty = new Label(allTasks.isEmpty() ? "No tasks yet." : "No task matches the filter.");
			empty.getStyleClass().add("empty-note");
			listBox.getChildren().add(empty);
			return;
		}
		groups.forEach((list, tasks) -> {
			Label header = new Label(list);
			header.getStyleClass().add("section-title");
			VBox cards = new VBox(8);
			AgendaCard.fill(cards, tasks, this::toggle, "");
			listBox.getChildren().addAll(header, cards);
		});
	}

	private boolean matches(AgendaItem task, String needle) {
		return task.title().toLowerCase(Locale.ROOT).contains(needle)
				|| (task.detail() != null && task.detail().toLowerCase(Locale.ROOT).contains(needle));
	}

	private void finishLoading() {
		progress.setVisible(false);
		refreshButton.setDisable(false);
	}
}