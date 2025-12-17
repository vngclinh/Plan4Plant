package com.example.plant_sever.DTO;

import java.util.List;
import java.util.Map;

public class SendNotificationRequest {
    private Long userId; 
    private String title;
    private String body;
    private Map<String, String> data;
    private List<String> targetTokens;

    public SendNotificationRequest() {}

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }

    public List<String> getTargetTokens() {
        return targetTokens;
    }

    public void setTargetTokens(List<String> targetTokens) {
        this.targetTokens = targetTokens;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}

