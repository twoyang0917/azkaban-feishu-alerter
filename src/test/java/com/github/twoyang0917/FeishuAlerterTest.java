package com.github.twoyang0917;

import azkaban.executor.ExecutableFlow;
import azkaban.flow.Flow;
import azkaban.project.Project;
import azkaban.utils.Props;
import org.apache.log4j.PropertyConfigurator;

import java.io.IOException;

public class FeishuAlerterTest {

    private static ExecutableFlow MockExecutableFlow(String projectName) {
        Project project = new Project(1, projectName);
        Flow flow = new Flow("flowName");
        ExecutableFlow executableFlow = new ExecutableFlow(project, flow);
        executableFlow.setExecutionId(1);
        return executableFlow;
    }

    public static void main(String[] args) {
        // 输出当前工作目录
        System.out.println(System.getProperty("user.dir"));

        // 初始化 log4j
        String log4jConfPath = "src/test/java/com/github/twoyang0917/log4j.properties";
        PropertyConfigurator.configure(log4jConfPath);

        // 测试 FeishuAlerter 加载规则
        Props props = null;
        try {
            props = new Props(null, "src/main/resources/plugin.properties");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        FeishuAlerter alerter = new FeishuAlerter(props);
    }
}
