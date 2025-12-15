package com.example.plant_sever.service;

import com.example.plant_sever.DTO.TreatmentPlanAction;
import com.example.plant_sever.DTO.TreatmentPlanResult;
import com.example.plant_sever.model.ChatHistory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
public class GeminiService {

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private TreatmentPlanService treatmentPlanService;

    @Value("${gemini.api-key}")
    private String apiKey;

    // 🛠️ FIX 1: Sử dụng tên model chính xác (1.5 thay vì 2.5)
    private static final String MODEL = "gemini-2.5-flash"; 
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    // 🧠 Xử lý câu hỏi dạng text
    public String askGemini(String userMessage, Long userId) {
        if (!isConfirmationIntent(userMessage) && !isPlantTopicByAI(userMessage)) {
            return "Xin lỗi, tôi chỉ hỗ trợ **cây trồng/làm vườn** (tưới, bón phân, sâu bệnh, giá thể, ánh sáng, đất, chậu...). "
                    + "Bạn có thể hỏi: *“Cách tưới lan?”, “Đất trộn cho xương rồng?”, “Trị rệp sáp thế nào?”*";
        }

        try {
            chatHistoryService.validateQuota(userId);
        } catch (RuntimeException e) {
            return e.getMessage();
        }

        String url = BASE_URL + MODEL + ":generateContent?key=" + apiKey;

        // Lấy 5–10 lượt chat gần nhất để gửi làm context
        List<ChatHistory> history = chatHistoryService.getRecentChats(userId);
        history.sort(Comparator.comparing(ChatHistory::getCreatedAt));

        JSONArray contents = new JSONArray();

        // System rule
        JSONObject system = new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text",
                        "Bạn là trợ lý Plan4Plant. Trả lời bằng tiếng Việt, ngắn gọn, có gạch đầu dòng. "
                    + "Chỉ nói về cây trồng/làm vườn. Nếu câu hỏi ngoài chủ đề, hãy từ chối lịch sự. "
                    + "Khi nhận được trường proposedActions[].impactedEvents, hãy tóm tắt lại cho người dùng "
                    + "các lịch đã bị dời hoặc được tạo mới.")));
        contents.put(system);
        contents.put(buildGeneralAnswerGuardrails());

        // Thêm lịch sử hội thoại
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

        // Câu hỏi mới
        contents.put(new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text", userMessage))));

        JSONArray tools = buildTools();

        // Payload gửi đi
        JSONObject payload = new JSONObject()
                .put("contents", contents)
                .put("tools", tools); // Ở v1beta, tools nằm ở root level là CHÍNH XÁC

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(payload.toString(), headers), String.class);

            JSONObject result = new JSONObject(response.getBody());
            JSONObject candidate = result.getJSONArray("candidates").getJSONObject(0);
            JSONArray parts = candidate.getJSONObject("content").getJSONArray("parts");

            // Kiểm tra xem Gemini có muốn gọi hàm không
            JSONObject functionCall = extractFunctionCall(parts);
            if (functionCall != null) {
                // Xử lý logic gọi hàm (tìm cây, chẩn đoán bệnh...)
                JSONObject functionResponse = handleFunctionCall(functionCall, userId);

                if (shouldFallbackToGeneral(functionResponse)) {
                    String generalReply = answerWithoutTools(contents, headers, restTemplate, url);
                    chatHistoryService.saveChatTurn(userId, userMessage, generalReply);
                    return generalReply;
                }

                // Tạo payload mới cho lượt gọi thứ 2 (Follow-up)
                JSONArray followupContents = new JSONArray(contents.toString());
                
                // 1. Thêm phản hồi function call của Model vào lịch sử
                followupContents.put(new JSONObject()
                        .put("role", "model")
                        .put("parts", new JSONArray().put(new JSONObject().put("functionCall", functionCall))));
                
                // 2. Thêm kết quả thực thi function (Function Response)
                followupContents.put(new JSONObject()
                        .put("role", "function")
                        .put("parts", new JSONArray().put(new JSONObject()
                                .put("functionResponse", new JSONObject()
                                        .put("name", functionCall.getString("name"))
                                        .put("response", functionResponse) // response phải là object, không phải string text
                                ))));

                JSONObject followupPayload = new JSONObject()
                        .put("contents", followupContents)
                        .put("tools", tools);

                ResponseEntity<String> followupResponse = restTemplate.postForEntity(
                        url, new HttpEntity<>(followupPayload.toString(), headers), String.class);

                JSONObject followupResult = new JSONObject(followupResponse.getBody());
                String finalReply = extractTextFromParts(
                        followupResult.getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts"));

                chatHistoryService.saveChatTurn(userId, userMessage, finalReply);
                return finalReply;
            }

            String botReply = extractTextFromParts(parts);
            chatHistoryService.saveChatTurn(userId, userMessage, botReply);
            return botReply;

        } catch (HttpClientErrorException e) {
            return "❌ Gemini API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString();
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Internal error: " + e.getMessage();
        }
    }

    // Xử lý câu hỏi có ảnh
    public String askGeminiWithImage(String userMessage, MultipartFile imageFile, Long userId) {
        if (imageFile == null || imageFile.isEmpty())
            return "⚠️ Ảnh bị trống, vui lòng chọn lại.";

        try {
            chatHistoryService.validateQuota(userId);

            byte[] imageBytes = imageFile.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String url = BASE_URL + MODEL + ":generateContent?key=" + apiKey;

            List<ChatHistory> history = chatHistoryService.getRecentChats(userId);
            history.sort(Comparator.comparing(ChatHistory::getCreatedAt));

            JSONArray contents = new JSONArray();
            JSONObject system = new JSONObject()
                    .put("role", "user")
                    .put("parts", new JSONArray().put(new JSONObject().put("text",
                            "Bạn là trợ lý Plan4Plant. Phân tích ảnh cây trồng người dùng gửi, "
                                    + "nêu loại cây, dấu hiệu bệnh và hướng xử lý. Trả lời tiếng Việt, ngắn gọn.")));
            contents.put(system);
            contents.put(buildGeneralAnswerGuardrails());

            // History loop (giống askGemini)
            for (ChatHistory c : history) {
                contents.put(new JSONObject().put("role", "user").put("parts", new JSONArray().put(new JSONObject().put("text", c.getMessage()))));
                if (c.getResponse() != null) contents.put(new JSONObject().put("role", "model").put("parts", new JSONArray().put(new JSONObject().put("text", c.getResponse()))));
            }

            JSONObject userContent = new JSONObject()
                    .put("role", "user")
                    .put("parts", new JSONArray()
                            .put(new JSONObject().put("text", userMessage))
                            .put(new JSONObject().put("inlineData", new JSONObject()
                                    .put("mimeType", imageFile.getContentType())
                                    .put("data", base64Image))));
            contents.put(userContent);

            JSONArray tools = buildTools();

            JSONObject payload = new JSONObject()
                    .put("contents", contents)
                    .put("tools", tools);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<String> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(payload.toString(), headers), String.class);

            JSONObject result = new JSONObject(response.getBody());
            JSONArray parts = result.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts");

            JSONObject functionCall = extractFunctionCall(parts);
            if (functionCall != null) {
                JSONObject functionResponse = handleFunctionCall(functionCall, userId);

                if (shouldFallbackToGeneral(functionResponse)) {
                    String generalReply = answerWithoutTools(contents, headers, restTemplate, url);
                    chatHistoryService.saveChatTurn(userId, userMessage + " [ §œnh]", generalReply);
                    return generalReply;
                }

                JSONArray followupContents = new JSONArray(contents.toString());
                followupContents.put(new JSONObject()
                        .put("role", "model")
                        .put("parts", new JSONArray().put(new JSONObject().put("functionCall", functionCall))));
                
                // Cấu trúc response chuẩn cho v1beta
                followupContents.put(new JSONObject()
                        .put("role", "function")
                        .put("parts", new JSONArray().put(new JSONObject()
                                .put("functionResponse", new JSONObject()
                                        .put("name", functionCall.getString("name"))
                                        .put("response", functionResponse)
                                ))));

                JSONObject followupPayload = new JSONObject()
                        .put("contents", followupContents)
                        .put("tools", tools);

                ResponseEntity<String> followupResponse = restTemplate.postForEntity(
                        url, new HttpEntity<>(followupPayload.toString(), headers), String.class);

                JSONObject followupResult = new JSONObject(followupResponse.getBody());
                String finalReply = extractTextFromParts(
                        followupResult.getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts"));

                chatHistoryService.saveChatTurn(userId, userMessage + " [ảnh]", finalReply);
                return finalReply;
            }

            String botReply = extractTextFromParts(parts);
            chatHistoryService.saveChatTurn(userId, userMessage + " [ảnh]", botReply);
            return botReply;

        } catch (IOException e) {
            return " Lỗi đọc ảnh: " + e.getMessage();
        } catch (HttpClientErrorException e) {
            return " Gemini API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString();
        } catch (Exception e) {
            return " Internal error: " + e.getMessage();
        }
    }

    private JSONArray buildTools() {
        JSONObject baseProperties = new JSONObject()
                .put("plant_name", new JSONObject()
                        .put("type", "string")
                        .put("description", "Tên cây hoặc biệt danh (nickname) mà người dùng gọi. Ví dụ: 'Hoa sứ', 'haha...', 'Cây ở ban công'."))
                .put("disease_name", new JSONObject()
                        .put("type", "string")
                        .put("description", "Tên bệnh (nếu người dùng nhắc đến)."))
                .put("garden_nickname", new JSONObject()
                        .put("type", "string")
                        .put("description", "Biệt danh cây."))
                .put("garden_id", new JSONObject()
                        .put("type", "integer")
                        .put("description", "ID garden."));

        JSONObject previewFunction = new JSONObject()
                .put("name", "get_disease_treatment_plan")
                // QUAN TRỌNG: Sửa mô tả để Gemini hiểu hàm này dùng để TÌM CÂY luôn
                .put("description", "Tìm kiếm thông tin cây trong vườn của người dùng (theo tên hoặc biệt danh) VÀ lấy kế hoạch điều trị nếu có bệnh.")
                .put("parameters", new JSONObject()
                        .put("type", "object")
                        .put("properties", baseProperties)
                        .put("required", new JSONArray().put("plant_name"))); // Chỉ bắt buộc tên cây

        JSONObject confirmFunction = new JSONObject()
                .put("name", "confirm_disease_treatment_plan")
                .put("description", "Áp dụng kế hoạch điều trị cho cây trong DB.")
                .put("parameters", new JSONObject()
                        .put("type", "object")
                        .put("properties", baseProperties)
                        .put("required", new JSONArray().put("plant_name").put("disease_name")));

        JSONArray functionDeclarations = new JSONArray()
                .put(previewFunction)
                .put(confirmFunction);

        return new JSONArray().put(new JSONObject().put("functionDeclarations", functionDeclarations));
    }
    private JSONObject extractFunctionCall(JSONArray parts) {
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.getJSONObject(i);
            if (part.has("functionCall")) {
                return part.getJSONObject("functionCall");
            }
        }
        return null;
    }

    private String extractTextFromParts(JSONArray parts) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.getJSONObject(i);
            if (part.has("text")) {
                builder.append(part.getString("text"));
            }
        }
        return builder.toString().trim();
    }

    private JSONObject handleFunctionCall(JSONObject functionCall, Long userId) {
        try {
            String name = functionCall.getString("name");
            JSONObject args = functionCall.optJSONObject("args");
            if (args == null) args = new JSONObject();

            TreatmentPlanResult result = null;

            switch (name) {
                case "get_disease_treatment_plan" -> {
                    result = treatmentPlanService.previewTreatmentPlan(
                            userId,
                            optString(args, "plant_name"),
                            optString(args, "disease_name"),
                            optString(args, "garden_nickname"),
                            optLong(args, "garden_id"));
                }
                case "confirm_disease_treatment_plan" -> {
                    result = treatmentPlanService.applyTreatmentPlan(
                            userId,
                            optString(args, "plant_name"),
                            optString(args, "disease_name"),
                            optString(args, "garden_nickname"),
                            optLong(args, "garden_id"));
                }
                default -> {
                    return new JSONObject().put("success", false).put("message", "Function không hỗ trợ: " + name);
                }
            }

            // Dù thành công hay thất bại (như không tìm thấy cây), hãy trả về JSON kết quả
            // để Gemini đọc được thông báo lỗi trong 'message'
            return buildFunctionResult(result);

        } catch (Exception ex) {
            // Trả về JSON lỗi thay vì throw exception để app không crash
            return new JSONObject()
                    .put("success", false)
                    .put("message", "Hệ thống gặp lỗi khi xử lý dữ liệu: " + ex.getMessage());
        }
    }

    private JSONObject buildFunctionResult(TreatmentPlanResult result) {
        if (result == null) {
             return new JSONObject().put("success", false).put("message", "Kết quả xử lý rỗng.");
        }
        JSONObject json = new JSONObject()
                .put("success", result.isSuccess())
                .put("message", result.getMessage());

        if (result.getStatus() != null) json.put("status", result.getStatus());

        if (result.getGardenId() != null) json.put("gardenId", result.getGardenId());
        if (result.getGardenNickname() != null) json.put("gardenNickname", result.getGardenNickname());
        if (result.getPlantName() != null) json.put("plantName", result.getPlantName());
        if (result.getDiseaseName() != null) json.put("diseaseName", result.getDiseaseName());

        json.put("currentSchedule", toJsonArray(result.getCurrentSchedule()));
        json.put("proposedActions", toJsonArray(result.getProposedActions()));
        return json;
    }

    private JSONObject buildGeneralAnswerGuardrails() {
        return new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text",
                        "Guardrail: Luon tra loi cac cau hoi ve cay/lam vuon du khong tim thay cay hoac benh trong DB. "
                                + "Chi goi cac ham xu ly benh khi nguoi dung dang noi ve cay trong vuon cua ho (co nickname/ID hoac noi 'cay cua toi') "
                                + "hoac ho muon ap dung ke hoach. Neu functionResponse.status la PLANT_NOT_FOUND, PLANT_NAME_MISSING, DISEASE_NOT_FOUND "
                                + "hoac DISEASE_NAME_MISSING thi bo qua viec yeu cau nhap DB va dua ra goi y chung, khong chan cau tra loi.")));
    }

    private boolean shouldFallbackToGeneral(JSONObject functionResponse) {
        if (functionResponse == null) return false;
        if (functionResponse.optBoolean("success", true)) return false;

        String status = functionResponse.optString("status", "");
        if (status == null) status = "";
        status = status.toUpperCase(Locale.ROOT);

        return status.equals("PLANT_NOT_FOUND")
                || status.equals("PLANT_NAME_MISSING")
                || status.equals("DISEASE_NOT_FOUND")
                || status.equals("DISEASE_NAME_MISSING");
    }

    private String answerWithoutTools(JSONArray contents, HttpHeaders headers, RestTemplate restTemplate, String url) {
        JSONObject payload = new JSONObject().put("contents", contents);
        ResponseEntity<String> fallbackResponse = restTemplate.postForEntity(
                url, new HttpEntity<>(payload.toString(), headers), String.class);

        JSONObject fallbackResult = new JSONObject(fallbackResponse.getBody());
        JSONArray fallbackParts = fallbackResult.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts");

        return extractTextFromParts(fallbackParts);
    }

    private JSONArray toJsonArray(List<TreatmentPlanAction> actions) {
        JSONArray array = new JSONArray();
        if (actions == null) return array;
        for (TreatmentPlanAction action : actions) {
            JSONObject obj = new JSONObject()
                    .put("type", action.getType())
                    .put("scheduledTime", action.getScheduledTime())
                    .put("description", action.getDescription())
                    .put("impactedEvents", new JSONArray(
                            Optional.ofNullable(action.getImpactedEvents()).orElse(Collections.emptyList())));
            array.put(obj);
        }
        return array;
    }

    private String optString(JSONObject args, String key) {
        if (args.has(key)) {
            String value = args.optString(key, null);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private Long optLong(JSONObject args, String key) {
        if (!args.has(key)) return null;
        try {
            return args.getLong(key);
        } catch (Exception ex) {
            String value = args.optString(key, null);
            if (value != null) {
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
private boolean isPlantTopicByAI(String userMessage) {
    try {
        String url = BASE_URL + MODEL + ":generateContent?key=" + apiKey;

        // Prompt phân loại
        String prompt = """
            Bạn là bộ lọc intent.
            Hãy phân loại xem câu nói sau có liên quan đến chủ đề cây trồng, chăm sóc cây,
            sâu bệnh, giá thể, tưới/bón phân hay không.

            Nếu liên quan → chỉ trả lời đúng 1 từ: "YES"
            Nếu không liên quan → chỉ trả lời đúng 1 từ: "NO"

            Câu cần phân loại: "%s"
            """.formatted(userMessage);

        // Tạo JSON payload theo API v1beta
        JSONObject content = new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray()
                        .put(new JSONObject().put("text", prompt)));

        JSONObject payload = new JSONObject()
                .put("contents", new JSONArray().put(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<String> response = restTemplate.postForEntity(
                url,
                new HttpEntity<>(payload.toString(), headers),
                String.class
        );

        JSONObject result = new JSONObject(response.getBody());
        String reply = result.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
                .toUpperCase(Locale.ROOT);

        return reply.equals("YES");

    } catch (Exception e) {
        return true; // fallback để không chặn câu hỏi
    }
}
private boolean isConfirmationIntent(String text) {
    if (text == null) return false;
    text = text.toLowerCase();

    String[] confirmKeywords = {
        "tôi xác nhận",
        "xác nhận",
        "áp dụng",
        "đồng ý",
        "ok áp dụng",
        "áp dụng ngay",
        "có, áp dụng",
        "ok làm đi",
        "làm đi",
        "thực hiện đi",
        "tiến hành",
        "áp dụng kế hoạch",
        "tôi đồng ý"
    };

    for (String k : confirmKeywords) {
        if (text.contains(k)) return true;
    }
    return false;
}

}
