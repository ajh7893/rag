package com.example.ragapp;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    /**
     * 벡터 저장소(Vector Store).
     * SimpleVectorStore 는 메모리에 보관하는 가장 단순한 구현체로, 학습용으로 적합합니다.
     * (앱을 끄면 내용이 사라집니다. 실제 서비스에서는 PGVector, Redis, Chroma 등을 씁니다.)
     *
     * EmbeddingModel 은 spring-ai-starter-model-ollama 가 자동으로 만들어 주입해 줍니다.
     */
    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
