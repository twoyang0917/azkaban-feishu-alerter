package com.github.twoyang0917;

import java.util.*;
import java.util.regex.Pattern;

public class RegexTest {
    // private static final String regex = "(?=.*[-_]mainland)(?!.*ai[-_]).*";

     private static final String regex = "(?=.*ai[-_]).*";

    public static boolean isMatch(String str) {
        return Pattern.compile(regex).matcher(str).matches();
    }

    private static final List<String> projects = Arrays.asList(
            "prod-batch-ai-audit_daily-mainland",
            "prod-batch-business-ab-mainland",
            "prod-batch-mainland-ai_access_log",
            "prod-batch-mainland-adm-ai",
            "prod-batch-mainland-admin-clued",
            "prod_clued_to_tencent-mainland"
    );

    public static void main(String[] args) {
        Set<String> projectsSet = new HashSet<>(projects);
        List<String> projectsInTriggers = new ArrayList<>(projectsSet);
        System.out.println("total: " + projectsInTriggers.size());
        int count = 0;
        for (String project : projectsInTriggers) {
            if (RegexTest.isMatch(project)) {
                count++;
                System.out.println(project);
            }
        }
        System.out.println("matched: " + count);
    }
}
