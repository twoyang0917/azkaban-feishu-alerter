package com.github.twoyang0917;

import java.util.regex.Pattern;

public class WebhookRule {
    public Pattern regex;
    public String url;

    public WebhookRule(String regex, String url) {
        this.regex = Pattern.compile(regex);
        this.url = url;
    }
    public boolean matches(String projectName) {
        return regex != null && regex.matcher(projectName).matches();
    }
    public String toString() {
        return String.format("{%s, %s}", regex, url);
    }
}
