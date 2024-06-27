package com.github.twoyang0917;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Request;
import okhttp3.Response;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class FeishuCardMessageTest {

    public static void main(String[] args) {
        OkHttpClient client = new OkHttpClient();
        /*
            {
              "msg_type": "interactive",
              "card": {
                "elements": [
                  {
                    "tag": "div",
                    "text": {
                      "content": "链接",
                      "tag": "lark_md"
                    }
                  },
                  {"tag": "hr"},
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
        String jsonTemplate = "{\"msg_type\":\"interactive\",\"card\":{\"elements\":[{\"tag\":\"div\",\"text\":{\"content\":\"链接\",\"tag\":\"lark_md\"}},{\"tag\":\"hr\"},{\"tag\":\"div\",\"text\":{\"content\":\"内容\",\"tag\":\"lark_md\"}}],\"header\":{\"template\":\"red\",\"title\":{\"content\":\"标题\",\"tag\":\"plain_text\"}},\"config\":{\"wide_screen_mode\":true}}}";

        // Parse JSON template into JsonObject using Gson
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject jsonObject = gson.fromJson(jsonTemplate, JsonObject.class);

        // Modify content in header based on your dynamic needs
        jsonObject.getAsJsonObject("card")
                .getAsJsonObject("header")
                .getAsJsonObject("title")
                .addProperty("content", "标题信息");


        jsonObject.getAsJsonObject("card")
                .getAsJsonArray("elements")
                .get(0).getAsJsonObject()
                .getAsJsonObject("text")
                .addProperty("content", "https://azkaban.example.com");

        jsonObject.getAsJsonObject("card")
                .getAsJsonArray("elements")
                .get(2).getAsJsonObject()
                .getAsJsonObject("text")
                .addProperty("content", "内容信息");

        // Convert JsonObject back to JSON string
        String json = gson.toJson(jsonObject);

        // Request body
        RequestBody requestBody = RequestBody.create(MediaType.get("application/json"), json);

        // Build the request
        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/bot/v2/hook/******")
                .post(requestBody)
                .build();

        // Execute the request and handle response
        try {
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                System.out.println("Request successfully sent!");
                System.out.println(response.body().string());
            } else {
                System.out.println("Request failed!");
                System.out.println("Response code: " + response.code());
                System.out.println(response.body().string());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
