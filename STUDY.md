# RAG 공부 가이드 (이 프로젝트로 배우기)

> 목표: "RAG가 왜 필요하고, 코드에서 어떻게 동작하는지"를 직접 만져보며 이해한다.
> 순서대로 따라오면 됩니다. 막히면 그 부분만 질문하세요.

---

## STEP 0. 큰 그림 먼저 (5분)

LLM(Gemma)은 자기가 학습한 것만 압니다. **내 문서**는 모릅니다.
RAG는 "오픈북 시험"처럼, 질문에 관련된 문서 조각을 찾아 LLM에게 같이 줍니다.

```
[저장 단계]  문서 → 조각내기(chunk) → 숫자벡터로 변환(embedding) → 벡터DB 저장
[질문 단계]  질문 → 벡터변환 → 벡터DB에서 비슷한 조각 검색 → 질문+조각을 LLM에 전달 → 답변
```

핵심 용어 4개만 기억하세요:
- **Chunk(청크)**: 문서를 검색하기 좋게 자른 작은 조각.
- **Embedding(임베딩)**: 글을 의미가 담긴 숫자 배열(벡터)로 바꾼 것. 의미가 비슷하면 벡터도 가깝다.
- **Vector Store(벡터DB)**: 임베딩을 저장하고 "비슷한 것 찾기"를 해주는 저장소.
- **Similarity Search(유사도 검색)**: 질문 벡터와 가장 가까운 chunk를 찾는 것.

---

## STEP 1. 코드를 "데이터 흐름" 순서로 읽기 (30분)

아무 파일이나 보지 말고, **데이터가 흐르는 순서**대로 보세요.

### 1) 입구 — `RagController.java`
- `/api/upload` 와 `/api/ask` 두 개의 문이 있다.
- 브라우저(`index.html`)가 이 두 곳으로 요청을 보낸다.
- 실제 일은 `RagService`에 시킨다(위임). 컨트롤러는 얇게 유지하는 게 정석.

### 2) 심장 — `RagService.java`  ← **여기가 제일 중요!**
두 메서드만 이해하면 RAG를 이해한 것입니다.

- `ingest()` (저장): `TikaDocumentReader`(읽기) → `TokenTextSplitter`(자르기) → `vectorStore.add()`(임베딩+저장)
- `ask()` (질문): `vectorStore.similaritySearch()`(검색) → 검색결과를 프롬프트에 넣기 → `chatClient.call()`(답변)
- `ask()` 안의 **프롬프트 문자열**을 꼭 정독하세요. "참고자료만 근거로 답해라"는 이 한 문장이 RAG 품질의 절반입니다.

### 3) 설정 — `RagConfig.java` + `application.properties`
- `RagConfig`: 벡터DB로 `SimpleVectorStore`(메모리)를 쓴다고 선언.
- `application.properties`: 어떤 모델을 쓸지(gemma3, nomic-embed-text), Ollama 주소는 어딘지.

### 4) 화면 — `static/index.html`
- `fetch('/api/upload')`, `fetch('/api/ask')` 부분만 보면 됨. 백엔드와 어떻게 대화하는지.

---

## STEP 2. 직접 실험하며 체감하기 (제일 중요!)

읽기만 하면 안 늘어요. 아래를 **직접 바꿔보고 결과를 관찰**하세요.

### 실험 A. 임베딩이 뭔지 눈으로 보기
터미널에서:
```bash
curl -s http://localhost:11434/api/embeddings \
  -d '{"model":"nomic-embed-text","prompt":"고양이"}' | head -c 200
```
→ 글자가 숫자 배열로 바뀌는 걸 확인. 이게 임베딩입니다.
"고양이"와 "강아지", "고양이"와 "자동차"를 각각 넣어보고, 숫자가 얼마나 다른지 느껴보세요.

### 실험 B. 검색이 진짜 되는지 보기
`RagService.ask()`에서 검색된 chunk를 로그로 찍어보세요. `related` 리스트를 출력하면
"질문과 관련된 조각이 정말 뽑히는구나"를 눈으로 볼 수 있습니다.

### 실험 C. topK 숫자 바꿔보기
`ask()`의 `.topK(4)` 를 `.topK(1)` 또는 `.topK(8)` 로 바꿔보세요.
- 너무 작으면 → 정보가 부족해 답을 못 함
- 너무 크면 → 관련 없는 내용까지 섞여 답이 흐려짐

### 실험 D. 프롬프트 바꿔보기
`ask()`의 프롬프트에서 "참고자료에 없으면 찾을 수 없다고 답하라" 문장을 지워보세요.
→ 모델이 자료에 없는 걸 지어내기(환각, hallucination) 시작하는 걸 관찰. 다시 넣으면 멈춤.

### 실험 E. RAG 없이 그냥 물어보기 (비교)
Gemma에게 RAG 없이 직접 물어보세요:
```bash
curl -s http://localhost:11434/api/generate \
  -d '{"model":"gemma3","prompt":"우리 회사 배우자 출산휴가는 며칠이야?","stream":false}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['response'])"
```
→ 엉뚱한 답이 나옴. 그런데 우리 RAG 앱에 물으면 "10일"이라고 정확히 답함.
**이 차이가 바로 RAG가 하는 일입니다.**

---

## STEP 3. 한 단계씩 키워보기 (응용)

이해됐으면 직접 기능을 추가해보며 익히세요. 쉬운 것부터:

1. **답변에 출처 표시**: `ask()`에서 검색된 chunk의 `getMetadata()`(파일명 등)도 같이 반환하기.
2. **여러 파일 누적**: 지금도 여러 번 업로드하면 쌓입니다. 파일별로 잘 구분되는지 실험.
3. **답변 스트리밍**: `.call()` 대신 `.stream()`을 써서 글자가 실시간으로 나오게.
4. **영구 저장**: `SimpleVectorStore`(메모리) → Chroma/PGVector로 교체해 앱을 꺼도 유지되게.

---

## 막히면?

- 개념이 안 잡힐 때: "임베딩이 왜 의미를 담아?" 처럼 **왜**를 물어보세요.
- 코드가 안 읽힐 때: 파일명 + 줄 번호를 알려주면 그 부분만 풀어 설명해드립니다.
- 추천 학습 키워드: `embedding`, `cosine similarity`, `chunking strategy`, `vector database`, `prompt engineering`, `hallucination`, `Spring AI ChatClient`.
