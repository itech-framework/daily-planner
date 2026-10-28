package org.flexitech.framework.products.daily_planner.api;

import java.util.Map;

import org.flexitech.framework.products.daily_planner.api.request.DriveFileRequest;
import org.flexitech.framework.products.daily_planner.api.response.DriveFileListResponse;
import org.flexitech.framework.products.daily_planner.api.response.DriveFileResponse;
import org.flexitech.framework.products.daily_planner.api.response.DriveGeneratedIdsResponse;

import io.github.itech_framework.api_client.annotations.ApiClient;
import io.github.itech_framework.api_client.annotations.methods.DELETE;
import io.github.itech_framework.api_client.annotations.methods.GET;
import io.github.itech_framework.api_client.annotations.methods.PATCH;
import io.github.itech_framework.api_client.annotations.methods.POST;
import io.github.itech_framework.api_client.annotations.parameters.Body;
import io.github.itech_framework.api_client.annotations.parameters.Headers;
import io.github.itech_framework.api_client.annotations.parameters.Path;
import io.github.itech_framework.api_client.annotations.parameters.Query;
import io.github.itech_framework.api_client.utils.body.BodyType;

@ApiClient(baseUrl = "https://www.googleapis.com")
public interface GoogleDriveAPI {

	@GET("/drive/v3/files")
	DriveFileListResponse listFiles(@Headers Map<String, String> headers,
			@Query("q") String q,
			@Query("pageSize") String pageSize,
			@Query("pageToken") String pageToken,
			@Query("orderBy") String orderBy,
			@Query("spaces") String spaces,
			@Query("fields") String fields,
			@Query("supportsAllDrives") String supportsAllDrives,
			@Query("includeItemsFromAllDrives") String includeItemsFromAllDrives);

	@GET("/drive/v3/files/{fileId}")
	DriveFileResponse getFile(@Headers Map<String, String> headers,
			@Path("fileId") String fileId,
			@Query("fields") String fields,
			@Query("supportsAllDrives") String supportsAllDrives);

	@POST("/drive/v3/files")
	DriveFileResponse createFile(@Headers Map<String, String> headers,
			@Query("fields") String fields,
			@Query("supportsAllDrives") String supportsAllDrives,
			@Body DriveFileRequest body);

	@PATCH("/drive/v3/files/{fileId}")
	DriveFileResponse updateFile(@Headers Map<String, String> headers,
			@Path("fileId") String fileId,
			@Query("addParents") String addParents,
			@Query("removeParents") String removeParents,
			@Query("fields") String fields,
			@Query("supportsAllDrives") String supportsAllDrives,
			@Body DriveFileRequest body);

	@POST("/drive/v3/files/{fileId}/copy")
	DriveFileResponse copyFile(@Headers Map<String, String> headers,
			@Path("fileId") String fileId,
			@Query("fields") String fields,
			@Query("supportsAllDrives") String supportsAllDrives,
			@Body DriveFileRequest body);

	@DELETE("/drive/v3/files/{fileId}")
	void deleteFile(@Headers Map<String, String> headers,
			@Path("fileId") String fileId,
			@Query("supportsAllDrives") String supportsAllDrives);

	@DELETE("/drive/v3/files/trash")
	void emptyTrash(@Headers Map<String, String> headers);

	@GET("/drive/v3/files/generateIds")
	DriveGeneratedIdsResponse generateIds(@Headers Map<String, String> headers,
			@Query("count") String count,
			@Query("space") String space);

	@GET("/drive/v3/files/{fileId}?alt=media")
	byte[] downloadFile(@Headers Map<String, String> headers,
			@Path("fileId") String fileId,
			@Query("supportsAllDrives") String supportsAllDrives);

	@GET("/drive/v3/files/{fileId}/export")
	byte[] exportFile(@Headers Map<String, String> headers,
			@Path("fileId") String fileId,
			@Query("mimeType") String mimeType);

	@POST("/upload/drive/v3/files")
	DriveFileResponse uploadMultipart(
	        @Headers Map<String, String> headers,
	        @Query("uploadType") String uploadType,
	        @Query("fields") String fields,
	        @Query("supportsAllDrives") String supportsAllDrives,
	        @Body(type = BodyType.BINARY) byte[] body);

	@PATCH("/upload/drive/v3/files/{fileId}")
	DriveFileResponse updateMultipart(
	        @Headers Map<String, String> headers,
	        @Path("fileId") String fileId,
	        @Query("uploadType") String uploadType,
	        @Query("fields") String fields,
	        @Query("supportsAllDrives") String supportsAllDrives,
	        @Body(type = BodyType.BINARY) byte[] body);

	@POST("/upload/drive/v3/files?uploadType=resumable")
	DriveFileResponse startResumableUpload(@Headers Map<String, String> headers,
			@Query("supportsAllDrives") String supportsAllDrives,
			@Body DriveFileRequest body);
}