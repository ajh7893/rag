package com.example.ragapp;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    /** 파일 업로드 → 텍스트 추출 → 벡터DB 저장 */
    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        int chunks = ragService.ingest(file.getResource());
        return Map.of(
                "filename", file.getOriginalFilename(),
                "chunks", chunks,
                "message", "문서를 학습했습니다. 이제 질문할 수 있어요."
        );
    }

    /** 질문 → 관련 chunk 검색 → LLM 답변 */
    @PostMapping("/ask")
    public Map<String, String> ask(@RequestBody Map<String, String> body) {
        String answer = ragService.ask(body.getOrDefault("question", ""));
        return Map.of("answer", answer);
    }
}
