package com.bald.chatStream.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Bald
 * @date 2026/1/12 22:28
 */
public interface ChatStreamService {
    public Flux<String> streamChat(String userMessage);

    public Mono<String> chat(String userMessage);
}
