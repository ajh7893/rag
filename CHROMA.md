# 🗄 Chroma 벡터DB 연동 가이드

> 벡터 저장소를 **인메모리(`SimpleVectorStore`) → Chroma(영구 저장)** 로 전환한 작업 기록입니다.
> `RagService`(핵심 로직)는 **한 줄도 고치지 않고** 저장소만 갈아끼웠습니다.

---

## 1. 왜 바꿨나

| | 이전: `SimpleVectorStore` | 이후: `ChromaVectorStore` |
|---|---|---|
| 저장 위치 | 앱 프로세스 메모리(HashMap) | 별도 Chroma 서버 |
| 영속성 | **앱 끄면 사라짐** | 앱을 껐다 켜도 유지됨 |
| 검색 방식 | 전체 벡터 완전 탐색 | 근사 최근접(ANN) 인덱스 |
| 용도 | 학습/데모 | 실서비스에 가까운 구성 |

핵심 동기: **앱을 재시작해도 학습한 문서가 사라지지 않게** 하기 위함.

---

## 2. 동작 구조

```
[앱 :8080] ──임베딩/검색──▶ [Ollama :11434]   글↔벡터 변환, 답변 생성
     │
     └──벡터 저장/검색──▶ [Chroma :8000]      임베딩을 디스크에 영구 저장
```

- 앱은 Chroma와 **HTTP REST API(v2)** 로 통신한다.
- 임베딩 계산은 여전히 Ollama(`nomic-embed-text`)가 담당하고, Chroma는 그 결과 벡터를 **저장·검색**만 한다.

---

## 3. 변경한 파일 (총 3개)

### (1) `pom.xml` — 의존성 추가
```xml
<!-- Chroma 벡터 저장소 (영구 저장) — 별도 Chroma 서버에 연결 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-chroma</artifactId>
</dependency>
```

### (2) `application.properties` — 접속 설정
```properties
# ===== Chroma 벡터 저장소 (영구 저장) =====
spring.ai.vectorstore.chroma.client.host=http://localhost
spring.ai.vectorstore.chroma.client.port=8000
# Chroma v2 API의 격리 단위 (기본값)
spring.ai.vectorstore.chroma.tenant-name=default_tenant
spring.ai.vectorstore.chroma.database-name=default_database
# 벡터를 담을 컬렉션(테이블 같은 개념) 이름
spring.ai.vectorstore.chroma.collection-name=rag_docs
# 컬렉션이 없으면 앱 시작 시 자동 생성
spring.ai.vectorstore.chroma.initialize-schema=true
```

### (3) `RagConfig.java` — 직접 만들던 Bean 제거
```java
@Configuration
public class RagConfig {
    // 자동설정에 위임 — 별도 Bean 정의 없음
}
```
> 이전에는 `SimpleVectorStore` Bean을 직접 만들었지만, 스타터가 `ChromaVectorStore`를
> **자동 생성(Auto-configuration)** 하므로 직접 만들 Bean이 사라졌다.
> (직접 또 만들면 `VectorStore` Bean이 2개가 되어 주입 충돌이 난다.)

---

## 4. Chroma 서버 실행

```bash
# 시작 (이미지 자동 다운로드)
docker run -d --name chroma -p 8000:8000 chromadb/chroma:latest

# 기동 확인 (정상이면 heartbeat JSON 반환)
curl http://localhost:8000/api/v2/heartbeat

# 중지 / 재시작
docker stop chroma
docker start chroma
```

> ⚠️ `chromadb/chroma:latest` 는 **Chroma 1.x** 라 **v2 API만** 제공한다.
> (구버전 `/api/v1/*` 은 `410 Gone`) → Spring AI 1.1.x 의 `tenant-name`/`database-name`
> 설정이 v2 API에 대응하므로 그대로 동작한다.

---

## 5. 동작 확인 (검증 순서)

```bash
# (1) 앱 실행
./mvnw spring-boot:run

# (2) 문서 업로드 → Chroma에 임베딩 적재
curl -X POST http://localhost:8080/api/upload -F "file=@sample.txt"

# (3) 질문 → RAG 답변
curl -X POST http://localhost:8080/api/ask \
  -H "Content-Type: application/json" -d '{"question":"연차는 며칠인가요?"}'

# (4) ★영속성 검증★ 앱을 껐다 켠 뒤, "재업로드 없이" 다시 질문
#     → 정상 답변이 나오면 Chroma에 영구 저장된 것
```

### Chroma 내부 직접 들여다보기 (별도 DB 툴 불필요, curl로 충분)
```bash
BASE=http://localhost:8000/api/v2/tenants/default_tenant/databases/default_database/collections

# 컬렉션 목록 / 저장된 벡터 개수
curl -s $BASE
curl -s $BASE/<컬렉션ID>/count

# 저장된 실제 레코드(원문 + 벡터 + 메타데이터) 꺼내기
curl -s -X POST $BASE/<컬렉션ID>/get \
  -H "Content-Type: application/json" \
  -d '{"include":["documents","embeddings","metadatas"]}'
```

레코드 1줄의 구성:
```
🆔 id        고유 식별자
📄 document  원문 chunk 텍스트   ← 검색되면 프롬프트로 들어감
🔢 embedding 768차원 벡터        ← 유사도 검색에 사용
🏷 metadata  source(파일명), chunk_index 등
```

---

## 6. 현재 한계 & 다음 단계

- [ ] **데이터 영속성 완성** — 지금은 컨테이너를 `docker rm` 하면 데이터가 사라진다.
      볼륨 마운트로 컨테이너 수명과 분리해야 진짜 영구 저장이 된다:
  ```bash
  docker run -d --name chroma -p 8000:8000 \
    -v $HOME/chroma-data:/data chromadb/chroma:latest
  ```
- [ ] **출처 표시** — 검색된 chunk의 `metadata.source`(파일명)를 답변에 함께 반환
- [ ] **메타데이터 필터링** — 특정 파일에서만 검색(`where: {source: ...}`)
- [ ] **운영 설정** — 인증/백업/인덱스(HNSW) 파라미터 튜닝

---

## 7. 핵심 교훈

1. **인터페이스 추상화** — `RagService`는 `VectorStore` 인터페이스에만 의존하므로
   구현체(Simple→Chroma)를 갈아끼워도 비즈니스 로직은 그대로다.
2. **Spring Boot 자동설정** — `ChromaVectorStore`를 직접 `new` 하지 않았다.
   스타터 의존성 + `application.properties` 설정만으로 Bean이 자동 생성·주입된다.
