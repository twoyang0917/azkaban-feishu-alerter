package com.github.twoyang0917;

import azkaban.alert.Alerter;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.Executor;
import azkaban.executor.ExecutorManagerException;
import azkaban.sla.SlaOption;
import azkaban.utils.Props;
import azkaban.utils.TimeUtils;

import java.util.*;

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
    private List<String> defaultNotifyPersons;
    private String urlPrefix;

    private ArrayList<WebhookRule> webhookRules = new ArrayList<>();
    private ArrayList<NotifyRule> notifyRules = new ArrayList<>();

    public FeishuAlerter(Props props) {
        logger.info("properties file path: " + props.getSource());
        this.enabled = props.getBoolean("feishu.enabled", true);
        this.alertSuccess = props.getBoolean("feishu.alertSuccess", false);
        this.defaultWebhookUrl = props.get("feishu.defaultWebhookUrl");
        this.defaultNotifyPersons = props.getStringList("feishu.defaultNotifyPersons");
        this.urlPrefix = props.get("azkaban.urlPrefix");
        logger.info("enabled: " + this.enabled);
        logger.info("alertSuccess: " + this.alertSuccess);
        logger.info("defaultWebhookUrl: " + this.defaultWebhookUrl);
        logger.info("defaultNotifyPersons: " + this.defaultNotifyPersons);
        logger.info("urlPrefix: " + this.urlPrefix);
        loadRules(props);
        logger.info("initialized");
    }

    private void loadRules(Props props) {
        // WebhookRule
        List<String> webhookRuleNames = props.getStringList("rule.webhook.name");
        if (webhookRuleNames.isEmpty()) {
            logger.info("No webhook rules defined");
        } else {
            for (String webhookRuleName : webhookRuleNames) {
                try {
                    String regex = props.getString("rule.webhook." + webhookRuleName + ".regex");
                    String url = props.getString("rule.webhook." + webhookRuleName + ".url");
                    WebhookRule webhook = new WebhookRule(regex, url);
                    this.webhookRules.add(webhook);
                    logger.info("Added webhook rule " + webhookRuleName + ": " +webhook.toString());
                } catch (Exception e) {
                    logger.error("Error loading webhook rule " + webhookRuleName + " " + e.getMessage());
                }
            }
        }

        // NotifyRule
        List<String> notifyRuleNames = props.getStringList("rule.notify.name");
        if (notifyRuleNames.isEmpty()) {
            logger.info("No notify rules defined");
        } else {
            for (String notifyRuleName : notifyRuleNames) {
                try {
                    String regex = props.getString("rule.notify." + notifyRuleName + ".regex");
                    List<String> persons = props.getStringList("rule.notify." + notifyRuleName + ".persons");
                    NotifyRule notify = new NotifyRule(regex, persons);
                    this.notifyRules.add(notify);
                    logger.info("Added notify rule " + notifyRuleName + ": " + notify.toString());
                } catch (Exception e) {
                    logger.error("Error loading notify rule " + notifyRuleName + " " + e.getMessage());
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
                    "tag": "markdown",
                    "content": "内容"
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
        String jsonTemplate = "{\"msg_type\":\"interactive\",\"card\":{\"elements\":[{\"tag\":\"markdown\",\"content\":\"内容\"}],\"header\":{\"template\":\"red\",\"title\":{\"content\":\"标题\",\"tag\":\"plain_text\"}},\"config\":{\"wide_screen_mode\":true}}}";
        // Parse JSON template into JsonObject using Gson
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject jsonObject = gson.fromJson(jsonTemplate, JsonObject.class);
        try {
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
                    .addProperty("content", content);

            return gson.toJson(jsonObject);
        } catch (Exception e) {
            logger.error("Error rendering message: " + e.getMessage());
            // Return an empty string in case of error
            return gson.toJson(new JsonObject());
        }
    }

    private String renderMessage(String title, String content) {
        return renderMessage(title, content, "red");
    }

    private void sendMessage(String message, String url)  {
        logger.debug("message:" + message);
        OkHttpClient client = new OkHttpClient();
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), message);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();
        try {
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                logger.debug("Request successfully sent!");
                logger.debug(response.body().string());
            } else {
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

    private String getUrl(String projectName) {
        for (WebhookRule rule : this.webhookRules) {
            if (rule.matches(projectName)) {
                logger.debug("Matched webhook rule: " + rule.toString() + " for project: " + projectName);
                return rule.url;
            }
        }
        logger.info("No matched webhook rule for project: " + projectName + ", using default webhook url: " + this.defaultWebhookUrl);
        return this.defaultWebhookUrl;
    }

    private List<String> getPersons(String projectName) {
        for (NotifyRule rule : this.notifyRules) {
            if (rule.matches(projectName)) {
                logger.debug("Matched notify rule: " + rule.toString() + " for project: " + projectName);
                return rule.persons;
            }
        }
        logger.info("No matched notify rule for project: " + projectName + ", using default notify persons: " + this.defaultNotifyPersons);
        return this.defaultNotifyPersons;
    }
    private String renderContent(ExecutableFlow executableFlow) {
        return renderContent(executableFlow, null);
    }

    private String renderContent(ExecutableFlow executableFlow, SlaOption slaOption) {
        String link = String.format("%s/executor?execid=%s", this.urlPrefix, executableFlow.getExecutionId());
        StringBuilder content = new StringBuilder(String.format("[%s](%s)\n" +
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
        ));

        // Add SLA message
        if (slaOption != null) {
            content.append(String.format("%s\n\n", slaOption.createSlaMessage(executableFlow)));
        }

        // Add persons
        List<String> persons = getPersons(executableFlow.getProjectName());
        if (!persons.isEmpty()) {
            for (String person : persons) {
                content.append(String.format("<at email=%s></at>\t", person));
            }
        }
        logger.debug(content.toString());
        return content.toString();
    }

    /*
    * 通过web页面配置Flow Parameters中的alert参数也可以控制是否发送报警
    * 此函数用来获取alert参数
    */
    private boolean isFlowAlertOn(ExecutableFlow executableFlow) {
        String alert = executableFlow.getExecutionOptions().getFlowParameters().getOrDefault("alert", "true");
        return Boolean.parseBoolean(alert);
    }

    @Override
    public void alertOnSuccess(ExecutableFlow executableFlow) throws Exception {
        if (!this.alertSuccess || !this.enabled || !isFlowAlertOn(executableFlow)) {
            logger.info("alertOnSuccess disabled");
            return;
        }

        logger.debug("alertOnSuccess");
        String title = String.format("Execution %s has succeeded", executableFlow.getExecutionId());
        String content = renderContent(executableFlow);
        String message = renderMessage(title, content, "green");
        sendMessage(message, getUrl(executableFlow.getProjectName()));
    }

    @Override
    public void alertOnError(ExecutableFlow executableFlow, String... extraReasons) {
        if (!this.enabled || !isFlowAlertOn(executableFlow)) {
            logger.info("alertOnError disabled");
            return;
        }
        logger.debug("alertOnError");
        String title = String.format("Execution %s has failed", executableFlow.getExecutionId());
        String content = renderContent(executableFlow);
        String message = renderMessage(title, content);
        sendMessage(message, getUrl(executableFlow.getProjectName()));
    }

    @Override
    public void alertOnFirstError(ExecutableFlow executableFlow) throws Exception {
        if (!this.enabled || !isFlowAlertOn(executableFlow)) {
            logger.info("alertOnFirstError disabled");
            return;
        }
        logger.debug("alertOnFirstError");
        String title = String.format("Execution %s has encountered a failure", executableFlow.getExecutionId());
        String content = renderContent(executableFlow);
        String message = renderMessage(title, content);
        sendMessage(message, getUrl(executableFlow.getProjectName()));
    }

    private String getJobOrFlowName(final SlaOption slaOption) {
        if (StringUtils.isNotBlank(slaOption.getJobName())) {
            return slaOption.getFlowName() + ":" + slaOption.getJobName();
        } else {
            return slaOption.getFlowName();
        }
    }

    @Override
    public void alertOnSla(SlaOption slaOption, ExecutableFlow executableFlow) throws Exception {
        if (!this.enabled || !isFlowAlertOn(executableFlow)) {
            logger.info("alertOnSla disabled");
            return;
        }
        logger.debug("alertOnSla");
        String title = String.format("SLA violation for execution %s", executableFlow.getExecutionId());
        String content = renderContent(executableFlow, slaOption);
        String message = renderMessage(title, content);
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
