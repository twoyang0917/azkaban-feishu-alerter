package com.github.twoyang0917;

import java.util.List;
import java.util.regex.Pattern;

public class NotifyRule {
    public Pattern regex;
    /*
    * 飞书用户，逗号分隔
    * 可以用open_id, user_id, email
    */
    public List<String> persons;

    public NotifyRule(String regex, List<String> persons) {
        this.regex = Pattern.compile(regex);
        this.persons = persons;
    }

    public boolean matches(String projectName) {
        return regex != null && regex.matcher(projectName).matches();
    }

    public String toString() {
        return String.format("{%s, %s}", regex, persons);
    }
}
