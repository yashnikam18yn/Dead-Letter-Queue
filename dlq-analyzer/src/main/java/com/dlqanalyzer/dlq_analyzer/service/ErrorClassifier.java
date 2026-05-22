package com.dlqanalyzer.dlq_analyzer.service;

import com.dlqanalyzer.dlq_analyzer.model.DlqMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Service
public class ErrorClassifier {

    public String classify(DlqMessage message){
        try{
            String stackTrace = message.getStackTrace();
            String errorClass = message.getErrorClass();
            if (stackTrace == null || stackTrace.isEmpty()){
                String fallback = (errorClass != null ? errorClass : "UnknownError")
                        + (message.getErrorMessage() != null
                        ? message.getErrorMessage().substring(
                        0, Math.min(100, message.getErrorMessage().length()))
                        : "");
                return md5(fallback);
            }

            List<String> appFrames= extractAppFrames(stackTrace);

            if (appFrames.isEmpty()) {
                return md5(errorClass != null ? errorClass : "UnknownError");
            }

            StringBuilder key = new StringBuilder(errorClass != null ? errorClass : "");
            for (int i = 0; i < Math.min(3, appFrames.size()); i++) {
                key.append("|").append(normalize(appFrames.get(i)));
            }

            return md5(key.toString());

        }catch (Exception e){
            log.error("Error classifying message: {}", e.getMessage());
            return md5("UnknownError");
        }
    }

    private String normalize(String frame) {
        // Strip line number: "at com.myapp.OrderService.process(OrderService.java:42)"
        // becomes: "com.myapp.OrderService.process"
        return frame
                .replace("at ", "")
                .replaceAll("\\(.*\\)", "")
                .trim();
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "fallback-" + Math.abs(input.hashCode());
        }
    }

    private List<String> extractAppFrames(String stackTrace) {
        List<String> appFrames = new ArrayList<>();
        String[] lines = stackTrace.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (!line.startsWith("at ")) continue;

            // Skip internal framework lines
            if (line.contains("java.base") ||
                    line.contains("java.lang") ||
                    line.contains("sun.") ||
                    line.contains("org.springframework") ||
                    line.contains("org.apache") ||
                    line.contains("com.sun") ||
                    line.contains("jdk.internal")) {
                continue;
            }

            appFrames.add(line);
        }
        return appFrames;
    }
}
