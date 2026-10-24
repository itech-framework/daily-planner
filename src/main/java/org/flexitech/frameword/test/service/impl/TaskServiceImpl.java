package org.flexitech.frameword.test.service.impl;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flexitech.frameword.test.api.GoogleTasksAPI;
import org.flexitech.frameword.test.api.response.GoogleTask;
import org.flexitech.frameword.test.api.response.GoogleTaskList;
import org.flexitech.frameword.test.api.response.GoogleTaskListsResponse;
import org.flexitech.frameword.test.api.response.GoogleTasksResponse;
import org.flexitech.frameword.test.model.AgendaItem;
import org.flexitech.frameword.test.service.GoogleAuthService;
import org.flexitech.frameword.test.service.TaskService;
import org.flexitech.frameword.test.util.GoogleApiSupport;

import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_framework.core.annotations.dependency.Use;

@Service
public class TaskServiceImpl implements TaskService {

	private static final Logger log = LogManager.getLogger(TaskServiceImpl.class);
	private static final String DEFAULT_LIST = "@default";

	@Use
	private GoogleAuthService authService;

	@Use
	private GoogleTasksAPI tasksAPI;

	private final Map<String, GoogleTask> cache = new ConcurrentHashMap<String, GoogleTask>();

	@Override
	public List<AgendaItem> getTasks(boolean includeCompleted) throws Exception {
		log.info("Loading tasks, include completed: {}", includeCompleted);
		String token = authService.getAccessToken();

		GoogleTaskListsResponse lists = GoogleApiSupport.call("Google Tasks",
				() -> tasksAPI.listTaskLists(GoogleApiSupport.bearer(token), "100"));

		List<AgendaItem> items = new ArrayList<AgendaItem>();
		if (lists == null) {
			return items;
		}

		String completedFlag = String.valueOf(includeCompleted);
		for (GoogleTaskList list : lists.getItems()) {
			GoogleTasksResponse response = GoogleApiSupport.call("Google Tasks", () -> tasksAPI
					.listTasks(GoogleApiSupport.bearer(token), list.getId(), completedFlag, completedFlag, "100"));
			if (response == null) {
				continue;
			}
			for (GoogleTask task : response.getItems()) {
				if (task.getTitle() == null || task.getTitle().isBlank()) {
					continue;
				}
				cache.put(task.getId(), task);
				items.add(toItem(task, list.getId(), list.getTitle()));
			}
			log.debug("Task list '{}' returned {} tasks", list.getTitle(), response.getItems().size());
		}
		log.info("Loaded {} tasks from {} lists", items.size(), lists.getItems().size());
		return items;
	}

	@Override
	public AgendaItem createTask(String title, String notes, LocalDate due) throws Exception {
		if (title == null || title.isBlank()) {
			throw new Exception("A task needs a title.");
		}
		log.info("Creating task, due: {}", due);

		GoogleTask body = new GoogleTask();
		body.setTitle(title.trim());
		body.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
		body.setDue(due == null ? null : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(due.atStartOfDay().atOffset(ZoneOffset.UTC)));

		String token = authService.getAccessToken();
		GoogleTask created = GoogleApiSupport.call("Google Tasks",
				() -> tasksAPI.createTask(GoogleApiSupport.bearer(token), DEFAULT_LIST, body));
		cache.put(created.getId(), created);
		return toItem(created, DEFAULT_LIST, null);
	}

	@Override
	public AgendaItem setDone(AgendaItem task, boolean done) throws Exception {
		GoogleTask source = cache.get(task.id());
		if (source == null) {
			throw new Exception("This task is not loaded any more. Refresh and try again.");
		}
		log.info("Marking task {} as {}", task.id(), done ? "done" : "not done");

		GoogleTask body = new GoogleTask();
		body.setId(source.getId());
		body.setTitle(source.getTitle());
		body.setNotes(source.getNotes());
		body.setDue(source.getDue());
		body.setParent(source.getParent());
		body.setStatus(done ? GoogleTask.STATUS_DONE : GoogleTask.STATUS_OPEN);

		String token = authService.getAccessToken();
		GoogleTask updated = GoogleApiSupport.call("Google Tasks",
				() -> tasksAPI.updateTask(GoogleApiSupport.bearer(token), task.listId(), task.id(), body));
		cache.put(updated.getId(), updated);
		return toItem(updated, task.listId(), task.listTitle());
	}

	private AgendaItem toItem(GoogleTask task, String listId, String listTitle) {
		LocalDate due = task.getDue() == null ? null : OffsetDateTime.parse(task.getDue()).toLocalDate();
		return new AgendaItem(task.getId(), AgendaItem.Type.TASK, task.getTitle(), task.getNotes(), null, null,
				false, due, task.isDone(), listId, listTitle, task.getWebViewLink(), null, null);
	}
}