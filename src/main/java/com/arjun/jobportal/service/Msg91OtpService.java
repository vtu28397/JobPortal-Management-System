package com.arjun.jobportal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class Msg91OtpService {
    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String authKey;
    private final String verifyUrl;

    public Msg91OtpService(ObjectMapper mapper,
                           @Value("${app.msg91.auth-key}") String authKey,
                           @Value("${app.msg91.verify-url}") String verifyUrl) {
        this.restClient = RestClient.create();
        this.mapper = mapper;
        this.authKey = authKey;
        this.verifyUrl = verifyUrl;
    }

    public boolean isVerified(String accessToken, String phone) {
        if (isBlank(accessToken) || isBlank(phone) || isBlank(authKey)) {
            return false;
        }

        return verifyWithFormPost(accessToken, phone)
                || verifyWithJsonBody(accessToken, phone)
                || verifyWithAuthHeader(accessToken, phone);
    }

    private boolean verifyWithFormPost(String accessToken, String phone) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("authkey", authKey);
        form.add("access-token", accessToken);

        return verify(() -> restClient.post()
                .uri(verifyUrl)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class), phone);
    }

    private boolean verifyWithJsonBody(String accessToken, String phone) {
        return verify(() -> restClient.post()
                .uri(verifyUrl)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "authkey", authKey,
                        "access-token", accessToken
                ))
                .retrieve()
                .body(String.class), phone);
    }

    private boolean verifyWithAuthHeader(String accessToken, String phone) {
        return verify(() -> restClient.post()
                .uri(verifyUrl)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header("authkey", authKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("access-token", accessToken))
                .retrieve()
                .body(String.class), phone);
    }

    private boolean verify(ResponseCall call, String phone) {
        try {
            String response = call.execute();
            return responseIndicatesSuccess(response, phone);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean responseIndicatesSuccess(String response, String phone) {
        if (isBlank(response)) {
            return false;
        }
        try {
            JsonNode root = mapper.readTree(response);
            String normalizedPhone = onlyDigits(phone);
            String responseText = response.toLowerCase();
            boolean success = responseText.contains("success")
                    || responseText.contains("verified")
                    || root.path("type").asText("").equalsIgnoreCase("success")
                    || root.path("status").asText("").equalsIgnoreCase("success");
            boolean phoneMatches = response.contains(normalizedPhone)
                    || response.contains("91" + normalizedPhone);
            boolean noIdentifierInResponse = !responseText.contains("mobile")
                    && !responseText.contains("phone")
                    && !responseText.contains("identifier");
            return success && (phoneMatches || noIdentifierInResponse);
        } catch (Exception ex) {
            String normalized = response.toLowerCase();
            return normalized.contains("success") && normalized.contains(onlyDigits(phone));
        }
    }

    private String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @FunctionalInterface
    private interface ResponseCall {
        String execute();
    }
}
