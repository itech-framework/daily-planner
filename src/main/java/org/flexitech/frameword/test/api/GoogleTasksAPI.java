package org.flexitech.frameword.test.api;

import java.util.Map;

import org.flexitech.frameword.test.api.response.GoogleTask;
import org.flexitech.frameword.test.api.response.GoogleTaskListsResponse;
import org.flexitech.frameword.test.api.response.GoogleTasksResponse;

import io.github.itech_framework.api_client.annotations.ApiClient;
import io.github.itech_framework.api_client.annotations.methods.GET;
import io.github.itech_framework.api_client.annotations.methods.POST;
import io.github.itech_framework.api_client.annotations.methods.PUT;
import io.github.itech_framework.api_client.annotations.parameters.Body;
import io.github.itech_framework.api_client.annotations.parameters.Headers;
import io.github.itech_framework.api_client.annotations.parameters.Path;
import io.github.itech_framework.api_client.annotations.parameters.Query;

@ApiClient(baseUrl = "https://tasks.googleapis.com")
public interface GoogleTasksAPI {

	@GET("/tasks/v1/users/@me/lists")
	GoogleTaskListsResponse listTaskLists(@Headers Map<String, String> headers,
			@Query("maxResults") String maxResults);

	@GET("/tasks/v1/lists/{taskList}/tasks")
	GoogleTasksResponse listTasks(@Headers Map<String, String> headers,
			@Path("taskList") String taskList,
			@Query("showCompleted") String showCompleted,
			@Query("showHidden") String showHidden,
			@Query("maxResults") String maxResults);

	@POST("/tasks/v1/lists/{taskList}/tasks")
	GoogleTask createTask(@Headers Map<String, String> headers,
			@Path("taskList") String taskList,
			@Body GoogleTask task);

	@PUT("/tasks/v1/lists/{taskList}/tasks/{task}")
	GoogleTask updateTask(@Headers Map<String, String> headers,
			@Path("taskList") String taskList,
			@Path("task") String task,
			@Body GoogleTask body);
}