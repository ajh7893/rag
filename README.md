# 📄 RAG 문서 질의응답 (Spring Boot + Ollama/Gemma)

업로드한 문서(PDF, Word, txt 등)의 내용을 **근거로** 질문에 답하는 RAG 웹 애플리케이션입니다.
무료 로컬 모델(**Ollama + Gemma 3**)을 사용하므로 API 키도, 비용도 필요 없습니다.

> 💡 RAG = Retrieval-Augmented Generation (검색 증강 생성).
> LLM에게 "오픈북 시험"처럼 관련 문서를 같이 줘서, 모르는 내용도 정확히 답하게 하는 기법.

---

## 🎬 데모

| 질문 | 🤖 Gemma 혼자 | 📄 이 RAG 앱 (문서 기반) |
|------|--------------|------------------------|
| 재택근무는 주 몇 회 가능해? | 엉뚱한 헛소리 (환각) | **"주 2회까지 가능합니다"** ✅ |
| 병가 진단서는 언제 내? | 지어냄 | **"3일 이상 연속 시 제출"** ✅ |
| 점심 식대는? (문서에 없음) | 아무 숫자나 지어냄 | **"자료에서 찾을 수 없습니다"** ✅ |

같은 모델이라도 문서를 주면(RAG) 정확해지고, 없는 건 솔직히 모른다고 답합니다.

---

## 🧠 동작 원리

```
[저장 단계]
  문서 업로드 → 텍스트 추출(Tika) → chunk 분할 → 임베딩(nomic-embed-text) → 벡터DB 저장

[질문 단계]
  질문 → 임베딩 → 벡터DB에서 의미가 비슷한 chunk 검색(top 4)
                → "참고자료 + 질문"을 프롬프트로 만들어 Gemma에 전달 → 답변
```

**핵심 용어**
- **Chunk**: 문서를 검색하기 좋게 자른 조각
- **Embedding**: 글을 의미가 담긴 숫자 배열(768차원 벡터)로 변환한 것. 뜻이 비슷하면 벡터도 가깝다.
- **Vector Store**: 임베딩을 저장하고 "비슷한 것 찾기"를 해주는 DB
- **Similarity Search**: 질문 벡터와 가장 가까운 chunk를 cosine 유사도로 찾는 것

---

## 🛠 기술 스택

| 구분 | 사용 기술 |
|------|-----------|
| 언어 / 런타임 | Java 17 |
| 프레임워크 | Spring Boot 3.5.14 |
| AI 라이브러리 | Spring AI 1.1.7 |
| LLM (답변 생성) | Ollama + **gemma3** |
| 임베딩 | Ollama + **nomic-embed-text** |
| 문서 파싱 | Apache Tika (PDF/Word/txt 등) |
| 벡터 저장소 | SimpleVectorStore (메모리, 학습용) |
| 빌드 | Maven (`./mvnw`) |

---

## 🚀 시작하기

### 1. 준비물 (최초 1회)

```bash
# (1) Ollama 설치 — 반드시 cask(정식 앱)로! formula 버전은 깨져 있음 (아래 '함정' 참고)
brew install --cask ollama-app

# (2) Ollama 서버 실행
#     방법 A) /Applications/Ollama.app 을 한 번 실행 (메뉴막대에 상주, 권장)
#     방법 B) 터미널에서 직접:
ollama serve

# (3) 모델 내려받기 (gemma3 ≈ 3.3GB, nomic-embed-text ≈ 274MB)
ollama pull gemma3
ollama pull nomic-embed-text
```

### 2. 앱 실행

```bash
cd rag-app
./mvnw spring-boot:run
```

### 3. 사용

브라우저에서 **http://localhost:8080** 접속 →
`sample.txt`(휴가 규정 예시 문서) 업로드 → `"연차는 며칠인가요?"` 같은 질문 입력.

---

## 📁 프로젝트 구조

```
rag-app/
├── pom.xml                          # 의존성 (web, ollama, tika, vector-store)
├── sample.txt                       # 테스트용 휴가규정 문서
├── README.md                        # (이 파일)
├── STUDY.md                         # 단계별 공부 가이드 + 실험
└── src/main/
    ├── java/com/example/ragapp/
    │   ├── RagAppApplication.java    # 앱 진입점
    │   ├── RagController.java        # 웹 API: POST /api/upload, /api/ask
    │   ├── RagService.java           # ★ RAG 핵심 로직 (ingest + ask)
    │   └── RagConfig.java            # 벡터 저장소(Bean) 설정
    └── resources/
        ├── application.properties    # Ollama 주소·모델·업로드 설정
        └── static/index.html         # 웹 화면 (업로드 + 질문 UI)
```

### 주요 파일 역할

| 파일 | 역할 |
|------|------|
| `RagService.java` | **여기가 심장.** `ingest()`=문서 저장, `ask()`=검색·답변 |
| `RagController.java` | 웹 요청을 받아 `RagService`에 위임 |
| `RagConfig.java` | 메모리 벡터 저장소 `SimpleVectorStore` 등록 |
| `application.properties` | 어떤 모델을 쓸지, Ollama 주소, 업로드 용량 |
| `static/index.html` | 파일 업로드·질문 화면 (fetch로 API 호출) |

---

## 🌐 API

| 메서드 | 경로 | 설명 | 요청 | 응답 |
|--------|------|------|------|------|
| POST | `/api/upload` | 파일 업로드 → 학습 | `multipart/form-data` (file) | `{filename, chunks, message}` |
| POST | `/api/ask` | 질문 → 답변 | `{"question": "..."}` | `{"answer": "..."}` |

```bash
# 예시
curl -X POST http://localhost:8080/api/upload -F "file=@sample.txt"
curl -X POST http://localhost:8080/api/ask \
  -H "Content-Type: application/json" -d '{"question":"연차는 며칠인가요?"}'
```

---

## ⚠️ 만들면서 겪은 함정 (중요)

1. **Homebrew `ollama` formula는 깨져 있다**
   `brew install ollama`(formula)는 모델을 구동하는 `llama-server` 바이너리가 빠져 있어
   `error starting llama-server: llama-server binary not found` 오류가 난다.
   → 반드시 **`brew install --cask ollama-app`**(정식 앱)을 사용할 것.

2. **Spring AI는 모듈이 잘게 쪼개져 있다**
   Ollama 스타터만으로는 벡터 저장소 클래스가 들어오지 않아
   `spring-ai-vector-store` 의존성을 `pom.xml`에 따로 추가해야 한다.

3. **Spring Initializr 버전 표기 주의**
   Initializr는 `3.5.14.RELEASE`라고 부르지만 실제 Maven 아티팩트는 `3.5.14`다.

---

## 🔧 트러블슈팅

| 증상 | 원인 / 해결 |
|------|-------------|
| 업로드/질문 시 500 에러 | Ollama 서버가 꺼져 있음 → `ollama serve` 또는 Ollama 앱 실행 |
| "자료에서 찾을 수 없습니다"만 나옴 | 앱 재시작으로 메모리 벡터DB가 비워짐 → 문서를 다시 업로드 |
| 첫 답변이 느림 | 로컬 모델 첫 로딩 때문. 두 번째부터 빨라짐 |
| `llama-server not found` | formula 버전 ollama 문제 → cask(`ollama-app`)로 재설치 |

---

## 📌 한계와 다음 단계

현재는 **학습용** 구성입니다.

- 벡터 저장소가 메모리 기반(`SimpleVectorStore`)이라 **앱을 끄면 학습 내용이 사라진다.**

**해볼 만한 발전 과제** (난이도 순)
1. 답변에 **출처(파일명) 표시** — 검색된 chunk의 `getMetadata()` 활용
2. **답변 스트리밍** — `.call()` → `.stream()` 으로 글자가 실시간 출력
3. **영구 벡터DB** — `SimpleVectorStore` → Chroma / PGVector 로 교체
4. **여러 문서 관리** — 문서 목록·삭제 기능 추가

> 자세한 공부 순서와 실험은 [`STUDY.md`](STUDY.md) 참고.
