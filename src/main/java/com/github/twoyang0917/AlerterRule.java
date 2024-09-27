package com.github.twoyang0917;

import java.util.regex.Pattern;

public class AlerterRule {
    private Pattern regex;
    private String webhookUrl;

    public AlerterRule(String regex, String webhookUrl) {
        this.setRegex(regex);
        this.setWebhookUrl(webhookUrl);
    }
    public void setRegex(String regex) {
        this.regex = Pattern.compile(regex);
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public boolean matches(String projectName) {
        return regex != null && regex.matcher(projectName).matches();
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }
}
