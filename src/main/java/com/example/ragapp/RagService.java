package com.example.ragapp;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * [1단계 - 저장] 업로드된 파일을 읽어 chunk로 나누고 벡터DB에 저장합니다.
     * 반환값: 저장된 chunk 개수
     */
    public int ingest(Resource fileResource) {
        // (1) 파일에서 텍스트 추출 — Tika가 PDF/Word/txt 등 형식을 알아서 처리
        List<Document> documents = new TikaDocumentReader(fileResource).get();

        // (2) 긴 텍스트를 검색하기 좋은 작은 chunk로 분할
        List<Document> chunks = new TokenTextSplitter().apply(documents);

        // (3) 각 chunk를 임베딩(벡터화)하여 저장 — 임베딩은 VectorStore가 자동 수행
        vectorStore.add(chunks);

        return chunks.size();
    }

    /**
     * [2단계 - 질문] 질문과 의미가 비슷한 chunk를 찾아 LLM에게 함께 전달하고 답변을 생성합니다.
     */
    public String ask(String question) {
        // (1) 질문과 의미적으로 가까운 chunk 상위 4개를 검색
        List<Document> related = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(4).build());

        if (related.isEmpty()) {
            return "먼저 문서를 업로드해 주세요. 아직 학습된 자료가 없습니다.";
        }

        // (2) 검색된 chunk들을 하나의 참고자료(context)로 합침
        String context = related.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        // (3) 참고자료 + 질문을 프롬프트로 만들어 LLM 호출 (이것이 RAG의 핵심!)
        String prompt = """
                당신은 문서 기반 질의응답 도우미입니다.
                아래 [참고자료]만 근거로 사용자의 [질문]에 한국어로 답하세요.
                참고자료에 답이 없으면 "자료에서 찾을 수 없습니다"라고만 답하세요.

                [참고자료]
                %s

                [질문]
                %s
                """.formatted(context, question);

        return chatClient.prompt().user(prompt).call().content();
    }
}
