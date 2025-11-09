package com.example.plant_sever.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.json.JSONObject;

@Service
public class GeminiService {

    @Value("${gemini.api-key}")
    private String apiKey;

    public String askGemini(String userMessage) {
        if (!isPlantRelated(userMessage)) {
                return "Xin lỗi, tôi chỉ hỗ trợ **cây trồng/làm vườn** (tưới, bón phân, sâu bệnh, giá thể, ánh sáng, đất, chậu...). "
                + "Bạn có thể hỏi: *“Cách tưới lan?”, “Đất trộn cho xương rồng?”, “Trị rệp sáp thế nào?”*";
        }
        String modelName = "gemini-2.5-flash"; // Model mới nhất, hiệu quả
        String url = "https://generativelanguage.googleapis.com/v1/models/" + modelName + ":generateContent?key=" + apiKey;

        String systemRule =
        "Bạn là trợ lý Plan4Plant. Chỉ trả lời các câu hỏi liên quan đến cây trồng/làm vườn: "
        + "thông tin cây, tưới, bón phân, giá thể, đất, ánh sáng, sâu bệnh, cắt tỉa, nhân giống... "
        + "Nếu câu hỏi không liên quan, hãy từ chối ngắn gọn và gợi ý người dùng quay về chủ đề cây trồng. "
        + "Câu trả lời ngắn gọn, gạch đầu dòng, có cảnh báo an toàn nếu cần. Ngôn ngữ: Tiếng Việt.";

        JSONObject systemContent = new JSONObject()
            .put("parts", new org.json.JSONArray()
                .put(new JSONObject().put("text", systemRule))
            );

        // 2. Tạo JSON cho User Content
        JSONObject userContent = new JSONObject()
            .put("role", "user")
            .put("parts", new org.json.JSONArray()
                .put(new JSONObject().put("text", userMessage))
            );

        // 3. Xây dựng Payload với System Content ĐỨNG ĐẦU mảng 'contents'
        JSONObject payload = new JSONObject()
            .put("contents", new org.json.JSONArray()
                .put(systemContent) // Thêm System Content trước
                .put(userContent)  // Thêm User Content sau
            )
            .put("safetySettings", new org.json.JSONArray()
                .put(new JSONObject()
                    .put("category", "HARM_CATEGORY_DANGEROUS_CONTENT")
                    .put("threshold", "BLOCK_MEDIUM_AND_ABOVE")
                )
                .put(new JSONObject()
                    .put("category", "HARM_CATEGORY_HATE_SPEECH")
                    .put("threshold", "BLOCK_MEDIUM_AND_ABOVE")
                )
                .put(new JSONObject()
                    .put("category", "HARM_CATEGORY_HARASSMENT")
                    .put("threshold", "BLOCK_MEDIUM_AND_ABOVE")
                )
            );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        RestTemplate restTemplate = new RestTemplate();

        try {
                ResponseEntity<String> response = restTemplate.postForEntity(
                        url, new HttpEntity<>(payload.toString(), headers), String.class);

                JSONObject result = new JSONObject(response.getBody());
                return result.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text");

        } catch (HttpClientErrorException e) {
                return " Gemini API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString();
        } catch (Exception e) {
                return "Internal error: " + e.getMessage();
        }
    }
    private boolean isPlantRelated(String text) {
        if (text == null) return false;
        String q = text.toLowerCase(java.util.Locale.ROOT);

        String[] kws = {
                "cây", "trồng", "tưới", "bón", "phân", "giá thể", "đất", "chậu",
                "sâu", "bệnh", "nấm", "lá", "rễ", "hoa", "lan", "giâm", "gieo",
                "pH", "tưới nước", "phun", "vườn", "cay", "trong", "tuoi","bon","phan","gia the", "dat", "benh","chau",
                "sau","benh", "nam", "la", "re", "giam",
                "plant", "watering", "fertilizer", "soil", "pot", "orchid", "pest", "fungus", "leaf", "root"
        };

        for (String k : kws) {
                if (q.contains(k)) return true;
        }
        return false;
        }
}
