package com.example.plant_sever.service;

import com.example.plant_sever.model.PlantResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.net.ssl.*;
import java.net.HttpURLConnection;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class PlantNetService {

    @Value("${plantnet.api.key}")
    private String plantNetApiKey;

    @Value("${plantnet.api.base-url}")
    private String plantNetBaseUrl;

    private final RestTemplate restTemplate = createInsecureRestTemplate(); // 👈 Dùng RT bỏ qua SSL

    private RestTemplate createInsecureRestTemplate() {
        try {
            // TrustManager bỏ qua verify certificate (DEV ONLY 🔥)
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(HttpURLConnection connection, String httpMethod) {
                    if (connection instanceof HttpsURLConnection httpsURLConnection) {
                        httpsURLConnection.setSSLSocketFactory(sslContext.getSocketFactory());
                        httpsURLConnection.setHostnameVerifier((hostname, session) -> true); // bỏ qua check host
                    }
                    try {
                        super.prepareConnection(connection, httpMethod);
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            };

            return new RestTemplate(requestFactory);
        } catch (Exception e) {
            throw new RuntimeException("Không tạo được RestTemplate insecure", e);
        }
    }

    public PlantResponse identify(MultipartFile image, String organ) throws Exception {
        // URL: https://my-api.plantnet.org/v2/identify/all?api-key=xxx
        String url = UriComponentsBuilder
                .fromHttpUrl(plantNetBaseUrl + "/identify/all")
                .queryParam("api-key", plantNetApiKey)
                .toUriString();

        // multipart gửi lên PlantNet
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        // MultipartFile -> org.springframework.core.io.ByteArrayResource
        org.springframework.core.io.ByteArrayResource imageResource =
                new org.springframework.core.io.ByteArrayResource(image.getBytes()) {
                    @Override
                    public String getFilename() {
                        return image.getOriginalFilename();
                    }
                };

        // PlantNet yêu cầu field "images" và "organs"
        body.add("images", imageResource);
        body.add("organs", organ);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity =
                new HttpEntity<>(body, headers);

        ResponseEntity<PlantResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                PlantResponse.class
        );

        return response.getBody();
    }
}
