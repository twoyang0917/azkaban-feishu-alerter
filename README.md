# Azkaban 飞书告警插件
## 使用方法
- 插件依赖：azkaban源码编译后部分jar包，主要是解决编译时的依赖，打包成插件时不需要这些包，因为azkaban运行时classloader都能加载得到。 
- 编译插件`mvn clean package`, 编译成功后，会生成插件包azkaban-feishu-alerter-X.X-SNAPSHOT-dist.tar.gz。
- 将编译好的插件包放到$AZKBAN_HOME/plugins/alerter目录下，并解压。
```shell
$ tree azkaban-feishu-alerter
azkaban-feishu-alerter
├── conf
│   └── plugin.properties
└── lib
    ├── annotations.jar
    ├── azkaban-feishu-alerter-1.0-SNAPSHOT.jar
    ├── kotlin-stdlib-common.jar
    ├── kotlin-stdlib-jdk7.jar
    ├── kotlin-stdlib-jdk8.jar
    ├── kotlin-stdlib.jar
    ├── okhttp.jar
    ├── okio-jvm.jar
    └── okio.jar
```
- 创建一个飞书机器人, 获取webhook地址: https://open.feishu.cn/open-apis/bot/v2/hook/******
- 编辑文件$AZKBAN_HOME/plugins/alerter/azkaban-feishu-alerter/conf/plugin.properties，设置飞书机器人的webhook地址feishu.webhookUrl.
- 配置azkaban.urlPrefix作为告警信息中的链接地址前缀
```shell
$ vim conf/plugin.properties
alerter.name=azkaban-feishu-alerter
alerter.class=com.github.twoyang0917.FeishuAlerter

# 飞书机器人的 Webhook URL
feishu.webhookUrl=https://open.feishu.cn/open-apis/bot/v2/hook/******
# 告警信息中的链接地址前缀
azkaban.urlPrefix=https://your-azkaban-url
```

## 说明
- 因为我的线上azkaban版本是3.74.3，所以我的代码都是基于这个版本的。如果你的版本接口有变化，只能参考这个来修改，不能直接使用。
- 如果要使用飞书告警，需要在flow的属性中设置alert.type为azkaban-feishu-alerter，当前仅支持在UI中设置，不支持在调度选项中设置。
- 但实际需求往往是用飞书告警来替换邮件告警，这个需要修改azkaban源码，所以我修改了azkaban源码，添加了多告警支持，并可关闭邮件告警。

## azkaban源码修改
- 详情见[我的fork](https://github.com/azkaban/azkaban/compare/3.74.3...twoyang0917:azkaban:multi_alerter)
- 或者直接下载[diff](https://github.com/azkaban/azkaban/compare/3.74.3...twoyang0917:azkaban:multi_alerter.diff)

## 测试验证
+ alertOnFirstError
+ alertOnError
+ alertOnSuccess
+ alertOnSla
以上都可以正常触发，alertOnFailedUpdate这个不知道怎么触发，我以为时运行flow时关闭executor，但似乎不行。

## 升级部署
在已有的集群中做升级部署，经测试，只需要修改webserver即可，executor不用动，当然最好还是保持一致。
- azkaban源码修改重新编译后，替换azkaban-common-3.74.3.jar和az-core-3.74.3.jar即可。
- 部署此插件包
