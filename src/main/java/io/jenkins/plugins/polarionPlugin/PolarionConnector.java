package io.jenkins.plugins.polarionPlugin;

import hudson.util.ListBoxModel;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PolarionConnector {

    private final HttpClient client;
    private String polarionRestBaseUrl;
    private String authorization;

    public PolarionConnector(String url, String token) {
        this.client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(600)).build();
        this.polarionRestBaseUrl = url.endsWith("/") ? url + "rest/v1" : url + "/rest/v1";
        this.authorization = "Bearer " + token;
    }

    public void connect() throws IOException, HttpException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.polarionRestBaseUrl + "/projects"))
                .header("Content-Type", "application/octet-stream")
                .header("Accept", "application/json")
                .header("Authorization", this.authorization)
                .GET()
                .build();

        long start = System.nanoTime();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        if (response.statusCode() != 200) {
            throw new HttpException(response, time, response.body().toString());
        }
    }

    public void checkProject(String projectID) throws IOException, HttpException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.polarionRestBaseUrl + "/projects/" + projectID))
                .header("Content-Type", "application/octet-stream")
                .header("Accept", "application/json")
                .header("Authorization", this.authorization)
                .GET()
                .build();

        long start = System.nanoTime();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        if (response.statusCode() != 200) {
            throw new HttpException(response, time, response.body().toString());
        }
    }

    public void checkWorkItem(String projectID, String workItemID)
            throws IOException, HttpException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.polarionRestBaseUrl + "/projects/" + projectID + "/workitems/" + workItemID))
                .header("Content-Type", "application/octet-stream")
                .header("Accept", "application/json")
                .header("Authorization", this.authorization)
                .GET()
                .build();

        long start = System.nanoTime();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        if (response.statusCode() != 200) {
            throw new HttpException(response, time, response.body().toString());
        }
    }

    public void getProjectsList(String url, String token, ListBoxModel items, String selectedId)
            throws IOException, JSONException, HttpException, InterruptedException {

        this.polarionRestBaseUrl = url;
        this.authorization = "Bearer " + token;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(polarionRestBaseUrl + "/projects"))
                .header("Content-Type", "application/octet-stream")
                .header("Accept", "application/json")
                .header("Authorization", authorization)
                .GET()
                .build();

        long start = System.nanoTime();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        if (response.statusCode() != 200) {
            throw new HttpException(response, time, response.body().toString());
        }

        JSONObject obj = new JSONObject(response.body().toString());
        JSONArray projectNodes = obj.getJSONArray("data");
        for (int i = 0; i < projectNodes.length(); i++) {
            String name = projectNodes.getJSONObject(i).getString("id");
            String idStr = projectNodes.getJSONObject(i).getString("id");

            items.add(new ListBoxModel.Option(name, idStr, idStr.equals(selectedId)));
        }
    }

    public String publishResults(File file, String projectId, String testRunId)
            throws IOException, HttpException, InterruptedException {

        String xunitImportEndPointUrl = this.polarionRestBaseUrl + "/projects/" + projectId + "/testruns/" + testRunId
                + "/actions/importXUnitTestResults";

        FileInputStream ins = null;
        Supplier<? extends InputStream> streamSupplier = null;
        try {
            ins = new FileInputStream(file); // FileNotFoundException extends IOException
            BufferedInputStream buf_ins = new BufferedInputStream(ins);
            streamSupplier = new Supplier<BufferedInputStream>() {
                @Override
                public BufferedInputStream get() {
                    return buf_ins;
                }
            };

        } catch (IOException e) {
            e.printStackTrace();
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(xunitImportEndPointUrl))
                .header("Content-Type", "application/octet-stream")
                .header("Accept", "application/json")
                .header("Authorization", this.authorization)
                .POST(HttpRequest.BodyPublishers.ofInputStream(streamSupplier))
                .build();

        long start = System.nanoTime();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        if (response.statusCode() != 202) {
            throw new HttpException(response, time, response.body().toString());
        }

        JSONObject obj = new JSONObject(response.body().toString());
        JSONObject data = obj.getJSONObject("data");
        return data.getString("id");
    }

    public String createNewTestRun(
            String projectId, String testRunIdPrefix, String testRunTitle, String testRunType, String groupId)
            throws IOException, InterruptedException {
        String testRunEndPointUrl = this.polarionRestBaseUrl + "/projects/" + projectId + "/testruns";
        String testRunId = testRunIdPrefix + "-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss-SS").format(new Date());
        String testRunJson = String.format(
                "{\"data\": [%s]}", singleTestRunWithAllFields(testRunId, testRunTitle, testRunType, groupId));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(testRunEndPointUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", this.authorization)
                .POST(BodyPublishers.ofString(testRunJson))
                .build();

        long start = System.nanoTime();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        if (response.statusCode() != 201) {
            throw new HttpException(response, time, response.body().toString());
        }

        JSONObject obj = new JSONObject(response.body().toString());
        JSONObject data = obj.getJSONArray("data").getJSONObject(0);
        return data.getString("id");
    }

    public void updateWorkItemWithWorkFlow(String projectId, String workitemId, String workflow)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.polarionRestBaseUrl + "/projects/" + projectId + "/workitems/" + workitemId
                        + "?workflowAction=" + workflow))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", this.authorization)
                .method(
                        "PATCH",
                        BodyPublishers.ofString(createWorkItemBodyForWokflowAction(projectId + "/" + workitemId)))
                .build();

        long start = System.nanoTime();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        if (response.statusCode() != 204) {
            throw new HttpException(response, time, response.body().toString());
        }
    }

    private String singleTestRunWithAllFields(String testRunId, String testTitle, String testRunType, String groupId) {
        return "" + "{" + "    \"type\":\"testruns\"," + "     \"attributes\":" + "        {"
                + "            \"groupId\":\"" + groupId + "\"," + "            \"id\":\"" + testRunId + "\","
                + "            \"isTemplate\":false," + "            \"keepInHistory\":true,"
                + "            \"status\":\"open\"," + "            \"title\":\"" + testTitle + "\","
                + "            \"type\":\"" + testRunType + "\"," + "            \"useReportFromTemplate\":true"
                + "         }" + "}";
    }

    private String createWorkItemBodyForWokflowAction(String id) {
        return "{" + "  \"data\": {" + "    \"type\": \"workitems\"," + "    \"id\":\"" + id + "\","
                + "    \"attributes\": {" + "    }" + "  }" + "}";
    }
}
