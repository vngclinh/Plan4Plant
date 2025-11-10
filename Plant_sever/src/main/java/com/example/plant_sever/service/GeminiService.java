package com.example.plant_sever.service;

import com.example.plant_sever.model.ChatHistory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

@Service
public class GeminiService {

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Value("${gemini.api-key}")
    private String apiKey;

    private static final String MODEL = "gemini-2.5-flash";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1/models/";

    public String askGemini(String userMessage, Long userId) {
        if (!isPlantRelated(userMessage)) {
            return "Xin lỗi, tôi chỉ hỗ trợ **cây trồng/làm vườn** (tưới, bón phân, sâu bệnh, giá thể, ánh sáng, đất, chậu...). "
                 + "Bạn có thể hỏi: *“Cách tưới lan?”, “Đất trộn cho xương rồng?”, “Trị rệp sáp thế nào?”*";
        }

        String url = BASE_URL + MODEL + ":generateContent?key=" + apiKey;

        // 🧠 1️⃣ Lấy 5–10 lượt chat gần nhất để gửi làm context
        List<ChatHistory> history = chatHistoryService.getRecentChats(userId);
        history.sort(Comparator.comparing(ChatHistory::getCreatedAt));

        JSONArray contents = new JSONArray();

        // 2️⃣ System rule (Gemini không có “system”, dùng role=user)
        JSONObject system = new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text",
                        "Bạn là trợ lý Plan4Plant. Trả lời bằng tiếng Việt, ngắn gọn, có gạch đầu dòng. "
                      + "Chỉ nói về cây trồng/làm vườn. Nếu câu hỏi ngoài chủ đề, hãy từ chối lịch sự.")));
        contents.put(system);

        // 3️⃣ Thêm toàn bộ hội thoại trước đó
        for (ChatHistory c : history) {
            contents.put(new JSONObject()
                    .put("role", "user")
                    .put("parts", new JSONArray().put(new JSONObject().put("text", c.getMessage()))));
            if (c.getResponse() != null) {
                contents.put(new JSONObject()
                        .put("role", "model")
                        .put("parts", new JSONArray().put(new JSONObject().put("text", c.getResponse()))));
            }
        }

        // 4️⃣ Cuối cùng, thêm câu hỏi mới
        contents.put(new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text", userMessage))));

        JSONObject payload = new JSONObject().put("contents", contents);

        // 5️⃣ Gửi API
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(payload.toString(), headers), String.class);

            JSONObject result = new JSONObject(response.getBody());
            String botReply = result.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");

            // 6️⃣ Lưu lượt chat vào DB
            chatHistoryService.saveChatTurn(userId, userMessage, botReply);

            return botReply;

        } catch (HttpClientErrorException e) {
            return "❌ Gemini API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString();
        } catch (Exception e) {
            return "❌ Internal error: " + e.getMessage();
        }
    }

    public String askGeminiWithImage(String userMessage, MultipartFile imageFile, Long userId) {
        if (imageFile == null || imageFile.isEmpty()) return "⚠️ Ảnh bị trống, vui lòng chọn lại.";

        try {
            byte[] imageBytes = imageFile.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String url = BASE_URL + MODEL + ":generateContent?key=" + apiKey;

            // 🧠 Gộp context cũ (nếu có)
            List<ChatHistory> history = chatHistoryService.getRecentChats(userId);
            history.sort(Comparator.comparing(ChatHistory::getCreatedAt));
            JSONArray contents = new JSONArray();

            JSONObject system = new JSONObject()
                    .put("role", "user")
                    .put("parts", new JSONArray().put(new JSONObject().put("text",
                            "Bạn là trợ lý Plan4Plant. Phân tích ảnh cây trồng người dùng gửi, "
                          + "nêu loại cây, dấu hiệu bệnh và hướng xử lý. Trả lời tiếng Việt, ngắn gọn.")));
            contents.put(system);

            for (ChatHistory c : history) {
                contents.put(new JSONObject()
                        .put("role", "user")
                        .put("parts", new JSONArray().put(new JSONObject().put("text", c.getMessage()))));
                if (c.getResponse() != null) {
                    contents.put(new JSONObject()
                            .put("role", "model")
                            .put("parts", new JSONArray().put(new JSONObject().put("text", c.getResponse()))));
                }
            }

            // 🧩 Thêm ảnh và yêu cầu hiện tại
            JSONObject userContent = new JSONObject()
                    .put("role", "user")
                    .put("parts", new JSONArray()
                            .put(new JSONObject().put("text", userMessage))
                            .put(new JSONObject().put("inlineData", new JSONObject()
                                    .put("mimeType", imageFile.getContentType())
                                    .put("data", base64Image))));
            contents.put(userContent);

            JSONObject payload = new JSONObject().put("contents", contents);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<String> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(payload.toString(), headers), String.class);

            JSONObject result = new JSONObject(response.getBody());
            String botReply = result.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");

            chatHistoryService.saveChatTurn(userId, userMessage + " [ảnh]", botReply);
            return botReply;

        } catch (IOException e) {
            return "❌ Lỗi đọc ảnh: " + e.getMessage();
        } catch (HttpClientErrorException e) {
            return "❌ Gemini API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString();
        } catch (Exception e) {
            return "❌ Internal error: " + e.getMessage();
        }
    }

    private boolean isPlantRelated(String text) {
        if (text == null) return false;
        String q = text.toLowerCase(Locale.ROOT);
        String[] kws = {"cây", "trồng", "tưới", "bón", "phân", "giá thể", "đất", "chậu", "sâu", "bệnh", "nấm", "lá", "rễ", "hoa", "lan"};
        for (String k : kws) if (q.contains(k)) return true;
        return false;
    }
}
