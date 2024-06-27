package com.github.twoyang0917;

import azkaban.alert.Alerter;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.Executor;
import azkaban.executor.ExecutorManagerException;
import azkaban.sla.SlaOption;
import azkaban.utils.Props;
import azkaban.utils.TimeUtils;

import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.apache.log4j.Logger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Request;
import okhttp3.Response;
import com.google.gson.Gson;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;

public class FeishuAlerter implements Alerter {
    private static final Logger logger = Logger.getLogger(FeishuAlerter.class);
    private String webhookUrl;
    private String urlPrefix;

    public FeishuAlerter(Props props) {
        this.webhookUrl = props.get("feishu.webhookUrl");
        this.urlPrefix = props.get("azkaban.urlPrefix");

        logger.info("FeishuAlerter initialized");
    }

    private String renderMessage(String title, String content, String color) {
        /*
            {
              "msg_type": "interactive",
              "card": {
                "elements": [
                  {
                    "tag": "div",
                    "text": {
                      "content": "内容",
                      "tag": "lark_md"
                    }
                  }
                ],
                "header": {
                  "template": "red",
                  "title": {
                    "content": "标题",
                    "tag": "plain_text"
                  }
                },
                "config": {"wide_screen_mode": true}
              }
            }
        */
        // https://jsontostring.com/
        String jsonTemplate = "{\"msg_type\":\"interactive\",\"card\":{\"elements\":[{\"tag\":\"div\",\"text\":{\"content\":\"内容\",\"tag\":\"lark_md\"}}],\"header\":{\"template\":\"red\",\"title\":{\"content\":\"标题\",\"tag\":\"plain_text\"}},\"config\":{\"wide_screen_mode\":true}}}";
        // Parse JSON template into JsonObject using Gson
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject jsonObject = gson.fromJson(jsonTemplate, JsonObject.class);

        // Modify content in header based on your dynamic needs
        jsonObject.getAsJsonObject("card")
                .getAsJsonObject("header")
                .addProperty("template", color);
        jsonObject.getAsJsonObject("card")
                .getAsJsonObject("header")
                .getAsJsonObject("title")
                .addProperty("content", title);
        jsonObject.getAsJsonObject("card")
                .getAsJsonArray("elements")
                .get(0).getAsJsonObject()
                .getAsJsonObject("text")
                .addProperty("content", content);

        return gson.toJson(jsonObject);
    }

    private String renderMessage(String title, String content) {
        return renderMessage(title, content, "red");
    }

    private void sendMesage(String message)  {
        logger.debug("AlertMessage:" + message);
        OkHttpClient client = new OkHttpClient();
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), message);
        Request request = new Request.Builder()
                .url(this.webhookUrl)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();
        try {
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                logger.info("Request successfully sent!");
                logger.info(response.body().string());
            } else {
                logger.error("Request failed!");
                logger.error("Response code: " + response.code());
                logger.error(response.body().string());
            }
        } catch (Exception e) {
            logger.trace(e);
        }
    }

    @Override
    public void alertOnSuccess(ExecutableFlow executableFlow) throws Exception {
        logger.debug("alertOnSuccess");
        String title = String.format("Execution %s has succeeded", executableFlow.getExecutionId());
        String link = String.format("%s/executor?execid=%s", this.urlPrefix, executableFlow.getExecutionId());
        String content = String.format("[%s](%s)\n" +
                                    "projectName: %s\n" +
                                    "flowId: %s\n" +
                                    "executionId: %s\n" +
                                    "startTime: %s\n" +
                                    "endTime: %s\n" +
                                    "duration: %s\n" +
                                    "status: %s\n\n",
                link, link,
                executableFlow.getProjectName(),
                executableFlow.getFlowId(),
                executableFlow.getExecutionId(),
                TimeUtils.formatDateTimeZone(executableFlow.getStartTime()),
                TimeUtils.formatDateTimeZone(executableFlow.getEndTime()),
                TimeUtils.formatDuration(executableFlow.getStartTime(), executableFlow.getEndTime()),
                executableFlow.getStatus()
        );

        String message = renderMessage(title, content, "green");
        sendMesage(message);
    }

    @Override
    public void alertOnError(ExecutableFlow executableFlow, String... extraReasons) {
        logger.debug("alertOnError");
        String title = String.format("Execution %s has failed", executableFlow.getExecutionId());
        String link = String.format("%s/executor?execid=%s", this.urlPrefix, executableFlow.getExecutionId());
        String content = String.format("[%s](%s)\n" +
                                    "projectName: %s\n" +
                                    "flowId: %s\n" +
                                    "executionId: %s\n" +
                                    "startTime: %s\n" +
                                    "endTime: %s\n" +
                                    "duration: %s\n" +
                                    "status: %s\n\n" +
                                    "extraReasons: %s\n",
                link, link,
                executableFlow.getProjectName(),
                executableFlow.getFlowId(),
                executableFlow.getExecutionId(),
                TimeUtils.formatDateTimeZone(executableFlow.getStartTime()),
                TimeUtils.formatDateTimeZone(executableFlow.getEndTime()),
                TimeUtils.formatDuration(executableFlow.getStartTime(), executableFlow.getEndTime()),
                executableFlow.getStatus(),
                String.join("\n", extraReasons)
        );

        String message = renderMessage(title, content);
        sendMesage(message);
    }

    @Override
    public void alertOnFirstError(ExecutableFlow executableFlow) throws Exception {
        logger.debug("alertOnFirstError");
        String title = String.format("Execution %s has encountered a failure", executableFlow.getExecutionId());
        String link = String.format("%s/executor?execid=%s", this.urlPrefix, executableFlow.getExecutionId());
        String content = String.format("[%s](%s)\n" +
                                    "projectName: %s\n" +
                                    "flowId: %s\n" +
                                    "executionId: %s\n" +
                                    "startTime: %s\n" +
                                    "endTime: %s\n" +
                                    "duration: %s\n" +
                                    "status: %s\n\n",
                link, link,
                executableFlow.getProjectName(),
                executableFlow.getFlowId(),
                executableFlow.getExecutionId(),
                TimeUtils.formatDateTimeZone(executableFlow.getStartTime()),
                TimeUtils.formatDateTimeZone(executableFlow.getEndTime()),
                TimeUtils.formatDuration(executableFlow.getStartTime(), executableFlow.getEndTime()),
                executableFlow.getStatus()
        );

        String message = renderMessage(title, content);
        sendMesage(message);
    }

    private String getJobOrFlowName(final SlaOption slaOption) {
        if (StringUtils.isNotBlank(slaOption.getJobName())) {
            return slaOption.getFlowName() + ":" + slaOption.getJobName();
        } else {
            return slaOption.getFlowName();
        }
    }

    @Override
    public void alertOnSla(SlaOption slaOption, String slaMessage) throws Exception {
        logger.debug("alertOnSla");
        String title = String.format("SLA violation for %s", getJobOrFlowName(slaOption));
        String message = renderMessage(title, slaMessage);
        sendMesage(message);
    }

    @Override
    public void alertOnFailedUpdate(Executor executor, List<ExecutableFlow> flows, ExecutorManagerException updateException) {
        logger.debug("alertOnFailedUpdate");
        String link = String.format("%s/status", this.urlPrefix);
        String title = String.format("Executor %s might have lost connection", executor.getHost());
        String content = String.format("Because getting status update from this azkaban executor is failing\n" +
                                    "the actual status of executions on it is unknown.\n" +
                                    "[%s](%s)\n\n" +
                                    "executor: %s\n\n" +
                                    "Error detail: %s\n",
                link, link,
                executor.getHost(),
                ExceptionUtils.getStackTrace(updateException)
        );
        String message = renderMessage(title, content);
        sendMesage(message);
    }
}
