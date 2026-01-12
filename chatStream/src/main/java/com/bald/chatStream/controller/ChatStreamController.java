package com.bald.chatStream.controller;

import com.bald.chatStream.request.ChatRequest;
import com.bald.chatStream.service.ChatStreamService;
import com.bald.chatStream.service.impl.ChatStreamServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Bald
 * @date 2026/1/9 16:26
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatStreamController {

    @Autowired
    private ChatStreamService chatStreamService;

    @GetMapping("/test")
    public String test(){
        return "Hello World!";
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@RequestBody ChatRequest request) {
        System.out.println("开始流式调用");
        String message = request.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("消息不能为空")
                    .build());
        }

        return chatStreamService.streamChat(message)
                .map(content -> ServerSentEvent.<String>builder()
                        .data(content)
                        .build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("[DONE]")
                        .build()))
                .onErrorResume(throwable -> Flux.just(ServerSentEvent.<String>builder()
                        .event("error")
                        .data("发生错误: " + throwable.getMessage())
                        .build()));
    }

    @GetMapping(value = "/getStream")
    public Flux<ServerSentEvent<String>> streamGetChat(@RequestParam String message) {
        System.out.println("开始流式调用");
        if (message == null || message.trim().isEmpty()) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("消息不能为空")
                    .build());
        }

        return chatStreamService.streamChat(message)
                .map(content -> ServerSentEvent.<String>builder()
                        .data(content)
                        .build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("[DONE]")
                        .build()))
                .onErrorResume(throwable -> Flux.just(ServerSentEvent.<String>builder()
                        .event("error")
                        .data("发生错误: " + throwable.getMessage())
                        .build()));
    }

    @GetMapping("/chat")
    public Mono<Map<String, String>> chat(@RequestParam String message) {
        System.out.println("开始非流式调用");
        if (message == null || message.trim().isEmpty()) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "消息不能为空");
            return Mono.just(errorResponse);
        }

        return chatStreamService.chat(message)
                .map(content -> {
                    Map<String, String> response = new HashMap<>();
                    response.put("message", content);
                    return response;
                })
                .onErrorResume(throwable -> {
                    Map<String, String> errorResponse = new HashMap<>();
                    errorResponse.put("error", "发生错误: " + throwable.getMessage());
                    return Mono.just(errorResponse);
                });
    }
}