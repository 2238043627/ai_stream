package com.bald.chatStream.service.impl;

import com.bald.chatStream.service.ChatStreamService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Bald
 * @date 2026/1/9
 */
@Service
@Slf4j
public class ChatStreamServiceImpl implements ChatStreamService {

    private static final String SSE_DATA_PREFIX = "data: ";
    private static final String DONE_MARKER = "[DONE]";

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.base-url}")
    private String baseUrl;

    @Value("${ai.model}")
    private String model;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ChatStreamServiceImpl() {
        this.webClient = WebClient.builder()
//                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 流式调用智谱大模型API
     * @param userMessage 用户消息
     * @return 流式返回的消息内容
     */
    /**
     * SSE 事件缓冲区状态类
     */
    private static class BufferState {
        final StringBuilder buffer;
        final List<String> events;

        BufferState(StringBuilder buffer, List<String> events) {
            this.buffer = buffer;
            this.events = events;
        }

        BufferState() {
            this.buffer = new StringBuilder();
            this.events = new ArrayList<>();
        }
    }

    public Flux<String> streamChat(String userMessage) {
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("stream", true);

        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", userMessage);

        requestBody.put("messages", new Object[]{message});

        return webClient.post()
                .uri(baseUrl + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(BodyInserters.fromValue(requestBody))
                // 执行请求并获取响应。
                .retrieve()
                // 将响应体作为 原始字节流（DataBuffer）以 Flux 形式返回
                // 每个 DataBuffer 可能包含一个或多个 SSE 事件的一部分（因为网络传输是分块的）。
                .bodyToFlux(DataBuffer.class)
                // 将 DataBuffer 转为字符串
                // 注意: 每个 chunk 可能不是完整的 SSE 事件！可能是一个事件的一部分，或包含多个事件。
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
//                .doOnNext(chunk -> log.info("Received chunk: {}", chunk))
                .scan(new BufferState(), (state, chunk) -> {
                    // 创建新的缓冲区，将旧缓冲区和新数据合并（不修改原状态）
                    StringBuilder newBuffer = new StringBuilder(state.buffer);
                    newBuffer.append(chunk);

                    List<String> events = new ArrayList<>();
                    int lastIndex = 0;
                    String bufferContent = newBuffer.toString();

                    // 按 \n\n 分割 SSE 事件（SSE 标准格式）
                    while (true) {
                        int eventEnd = bufferContent.indexOf("\n\n", lastIndex);
                        if (eventEnd == -1) {
                            // 没有完整的事件，保留剩余部分在缓冲区
                            break;
                        }

                        String event = bufferContent.substring(lastIndex, eventEnd);
                        String data = extractDataFromEvent(event);
                        if (data != null && !data.isEmpty() && !DONE_MARKER.equals(data)) {
                            events.add(data);
                        }
                        lastIndex = eventEnd + 2; // 跳过 \n\n
                    }

                    // 移除已处理的部分，保留未完成的片段
                    StringBuilder remainingBuffer = new StringBuilder();
                    if (lastIndex < bufferContent.length()) {
                        remainingBuffer.append(bufferContent.substring(lastIndex));
                    }

                    return new BufferState(remainingBuffer, events);
                })
                .skip(1) // 跳过初始状态
                .flatMap(state -> Flux.fromIterable(state.events))
                .map(this::extractContent)
                .filter(content -> content != null && !content.isEmpty())
                .timeout(Duration.ofMinutes(5))
                .onErrorResume(this::handleError);
    }

    /**
     * 从 SSE 事件中提取 data 字段
     * SSE 格式示例:
     *   data: {"choices":[...]}\n\n
     *   或
     *   event: done\ndata: [DONE]\n\n
     */
    private String extractDataFromEvent(String event) {
        if (event == null || event.isEmpty()) {
            return null;
        }
        String[] lines = event.split("\n");
        String data = null;
        String eventType = null;
        
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith(SSE_DATA_PREFIX)) {
                data = line.substring(SSE_DATA_PREFIX.length()).trim(); // 去掉 "data: " 前缀
//                data = line.substring(SSE_DATA_PREFIX.length()); // 去掉 "data: " 前缀
            } else if (line.startsWith("event:")) {
                eventType = line.substring(6).trim(); // 去掉 "event:" 前缀
//                eventType = line.substring(6); // 去掉 "event:" 前缀
            }
        }
        
        // 如果是 done 事件，返回 null
        if ("done".equals(eventType) || DONE_MARKER.equals(data)) {
            return null;
        }
        return data;
    }


    public String extractContent(String jsonData) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonData);
            log.info("extractContent jsonNode: {}", jsonNode);
            JsonNode choices = jsonNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode delta = firstChoice.get("delta");
                if (delta != null && delta.has("content")) {
//                    log.info("delta: {}", delta.get("content").asText("") + "\n\n");
//                    return delta.get("content").asText("");
                    JsonNode root = objectMapper.readTree(jsonData);
                    ObjectNode result = objectMapper.createObjectNode();
                    result.set("choices", root.path("choices"));
                    return result.toString();
                }
            }
            return "";
        } catch (Exception e) {
            log.warn("Failed to parse JSON: {}", jsonData, e);
            return "";
        }
    }

    private Flux<String> handleError(Throwable throwable) {
        log.error("Stream chat error", throwable);
        return Flux.just("【错误: " + throwable.getMessage() + "】");
    }

    /**
     * 非流式调用智谱大模型API
     * @param userMessage 用户消息
     * @return 完整返回的消息内容
     */
    public Mono<String> chat(String userMessage) {
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("stream", false);

        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", userMessage);

        requestBody.put("messages", new Object[]{message});

        System.out.println("非流式service");

        return webClient.post()
                .uri(baseUrl + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(BodyInserters.fromValue(requestBody))
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    try {
                        JsonNode jsonNode = objectMapper.readTree(response);
                        JsonNode choices = jsonNode.get("choices");
                        if (choices != null && choices.isArray() && choices.size() > 0) {
                            JsonNode messageNode = choices.get(0).get("message");
                            if (messageNode != null && messageNode.has("content")) {
                                return messageNode.get("content").asText();
                            }
                        }
                        return "未获取到有效响应";
                    } catch (Exception e) {
                        return "解析响应失败: " + e.getMessage();
                    }
                })
                .timeout(Duration.ofMinutes(5))
                .onErrorResume(throwable -> {
                    return Mono.just("【错误: " + throwable.getMessage() + "】");
                });
    }
}
