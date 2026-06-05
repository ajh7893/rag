package com.example.ragapp;

import org.springframework.context.annotation.Configuration;

/**
 * 벡터 저장소(Vector Store) 설정.
 *
 * [변경 이력] SimpleVectorStore(메모리) → ChromaVectorStore(영구 저장)
 *
 * 이전에는 여기서 SimpleVectorStore Bean 을 직접 만들었지만,
 * 이제는 spring-ai-starter-vector-store-chroma 스타터가
 * application.properties 의 spring.ai.vectorstore.chroma.* 설정을 읽어
 * ChromaVectorStore Bean 을 "자동으로" 만들어 줍니다. (Spring Boot 자동설정)
 *
 * 따라서 우리가 직접 만들 Bean 은 없어졌습니다.
 * (만약 여기서 SimpleVectorStore Bean 을 또 만들면 VectorStore 가 2개가 되어
 *  RagService 주입 시 충돌이 납니다.)
 *
 * RagService 의 코드(ingest/ask)는 VectorStore 인터페이스에만 의존하므로
 * 구현체가 Simple → Chroma 로 바뀌어도 한 줄도 고치지 않아도 됩니다.
 */
@Configuration
public class RagConfig {
    // 자동설정에 위임 — 별도 Bean 정의 없음
}
