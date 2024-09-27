package com.github.twoyang0917;

import azkaban.alert.Alerter;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.Executor;
import azkaban.executor.ExecutorManagerException;
import azkaban.sla.SlaOption;
import azkaban.utils.Props;
import azkaban.utils.TimeUtils;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

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
    private boolean enabled;
    private boolean alertSuccess;
    private String defaultWebhookUrl;
    private String urlPrefix;

    private Map<String, AlerterRule> rules = new HashMap<>();

    public FeishuAlerter(Props props) {
        this.enabled = props.getBoolean("feishu.enabled", true);
        this.alertSuccess = props.getBoolean("feishu.alertSuccess", false);
        this.defaultWebhookUrl = props.get("feishu.defaultWebhookUrl");
        this.urlPrefix = props.get("azkaban.urlPrefix");
        loadRules(props);
        logger.info("FeishuAlerter initialized");
    }

    public void loadRules(Props props) {
        List<String> ruleNames = props.getStringList("rule.names");
        if (ruleNames.isEmpty()) {
            logger.info("No rules defined");
        } else {
            for (String ruleName : ruleNames) {
                try {
                    String regex = props.getString("rule." + ruleName + ".regex");
                    String webhookUrl = props.getString("rule." + ruleName + ".webhookUrl");
                    AlerterRule rule = new AlerterRule(regex, webhookUrl);
                    this.rules.put(ruleName, rule);
                    logger.info("Added rule " + ruleName + " regex " + regex + " webhookUrl " + webhookUrl);
                } catch (Exception e) {
                    logger.error("Error loading rule " + ruleName + " " + e.getMessage());
                }
            }
        }
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

    private void sendMessage(String message, String webhookUrl)  {
        logger.debug("AlertMessage:" + message);
        OkHttpClient client = new OkHttpClient();
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), message);
        Request request = new Request.Builder()
                .url(webhookUrl)
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

    private void sendMessage(String message) {
        // Send message to default webhook url
        sendMessage(message, this.defaultWebhookUrl);
    }

    private String getWebhookUrl(String projectName) {
        for (AlerterRule rule : this.rules.values()) {
            if (rule.matches(projectName)) {
                return rule.getWebhookUrl();
            }
        }
        return this.defaultWebhookUrl;
    }

    @Override
    public void alertOnSuccess(ExecutableFlow executableFlow) throws Exception {
        if (!this.enabled || !this.alertSuccess) {
            logger.info("alertOnSuccess disabled");
            return;
        }

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
        sendMessage(message, getWebhookUrl(executableFlow.getProjectName()));
    }

    @Override
    public void alertOnError(ExecutableFlow executableFlow, String... extraReasons) {
        if (!this.enabled) {
            logger.info("alertOnError disabled");
            return;
        }
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
        sendMessage(message, getWebhookUrl(executableFlow.getProjectName()));
    }

    @Override
    public void alertOnFirstError(ExecutableFlow executableFlow) throws Exception {
        if (!this.enabled) {
            logger.info("alertOnFirstError disabled");
            return;
        }
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
        sendMessage(message, getWebhookUrl(executableFlow.getProjectName()));
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
        if (!this.enabled) {
            logger.info("alertOnSla disabled");
            return;
        }
        logger.debug("alertOnSla");
        String title = String.format("SLA violation for %s", getJobOrFlowName(slaOption));
        String message = renderMessage(title, slaMessage);
        sendMessage(message);
    }

    @Override
    public void alertOnFailedUpdate(Executor executor, List<ExecutableFlow> flows, ExecutorManagerException updateException) {
        if (!this.enabled) {
            logger.info("alertOnFailedUpdate disabled");
            return;
        }
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
        sendMessage(message);
    }
}
