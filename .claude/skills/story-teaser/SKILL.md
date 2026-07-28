---
name: story-teaser
description: 사연·임사체험·야담 롱폼 스크립트와 일러스트 몇 장으로 6~15초 하이라이트 티저(예고편) 영상을 만든다. 사용자가 대본(docx/txt/붙여넣기)과 이미지를 주며 "하이라이트 만들어줘", "티저 만들어줘", "쇼츠용 예고편", "이걸로 짧은 영상" 같은 요청을 하면 이 스킬을 쓴다. 자막 연출·켄번즈 줌·번개 효과·자체 합성 BGM까지 넣어 MP4로 렌더링한다.
---

# 사연/야담 하이라이트 티저 제작

롱폼 대본 + 일러스트 → **6~15초 티저 MP4**. 본편 유도가 목적이므로
"궁금하게 만들고 끊는" 것이 최우선이다. 카스는 비개발자다 — 진행은 쉬운 말로 보고한다.

## 준비 (없을 때만)

```bash
which ffmpeg || (apt-get update -qq && apt-get install -y -qq ffmpeg)
python3 -c "import numpy" || pip install --break-system-packages -q numpy
ls ~/.claude/skills/hyperframes >/dev/null || npx hyperframes skills update
```

## 작업 순서

### 1. 소재 확보

- **대본**: docx면 `/docx` 스킬로 읽는다. `pandoc`은 이 환경에 없으니 쓰지 말 것.
- **이미지**: 채팅에 붙여넣은 그림은 파일로 저장되지 않는다. 세션 기록에서 추출한다:

```bash
python3 <<'EOF'
import json, base64
from pathlib import Path
jl = Path("~/.claude/projects/<프로젝트>/<세션id>.jsonl").expanduser()
out = Path("assets"); out.mkdir(exist_ok=True)
n = 0
for line in jl.open(encoding="utf-8"):
    if '"image"' not in line: continue
    try: obj = json.loads(line)
    except Exception: continue
    for item in (obj.get("message") or {}).get("content") or []:
        if isinstance(item, dict) and item.get("type") == "image":
            src = item.get("source", {}); d = src.get("data")
            if d:
                n += 1
                ext = {"image/png":"png","image/jpeg":"jpg","image/webp":"webp"}.get(
                    src.get("media_type",""), "png")
                (out / f"img{n}.{ext}").write_bytes(base64.b64decode(d))
print(n, "장 추출")
EOF
```

### 2. 훅 선정 (가장 중요한 단계)

대본을 읽고 **결말을 알려주지 않으면서 가장 궁금하게 만드는** 문장 3~4개를 뽑는다.
각 문장은 이미지 한 장에 대응한다. 원칙:

- 마지막 자막은 **반드시 미완결**로 끝낸다 (결말 공개 금지)
- 문장당 강조어 1개를 정한다 — 감정의 정점이거나 반전을 암시하는 단어
- 한 줄은 짧게. 2줄 구성(`line1` 평서 → `line2` 강조 포함)이 기본
- 이미지 순서는 대본 시간순을 따르되, 가장 강한 그림을 마지막 바로 앞에 배치

**이미지와 자막의 내용이 어긋나면 안 된다.** 그림에 없는 장면을 자막으로 말하지 말 것.

### 3. 프로젝트 스캐폴드

```bash
npx hyperframes init teaser --non-interactive --example=blank
cd teaser && npm install gsap --silent
mkdir -p assets && cp <이미지들> assets/
```

### 4. spec.json 작성

`references/spec-example.json`을 복사해 채운다. 필드 설명은 그 파일 주석 참고.
`focus`는 이미지에서 **인물 얼굴의 가로 위치**(0=왼쪽 끝, 1=오른쪽 끝)다 —
이미지를 직접 `Read`해서 눈으로 확인하고 정할 것. 잘못 잡으면 얼굴이 잘린다.

### 5. 생성 → 검증 → 렌더링

```bash
S=<이 스킬 경로>/scripts
python3 $S/build_teaser.py spec.json > index.html
python3 $S/make_bgm.py spec.json bgm.wav

export PUPPETEER_EXECUTABLE_PATH=/opt/pw-browsers/chromium
npx hyperframes check                       # 0 error 나올 때까지
npx hyperframes snapshot --at 1.5,7.5,10.2  # 반드시 Read로 눈으로 확인
npx hyperframes render                      # 3~5분 소요
```

**스냅샷을 반드시 `Read`로 직접 볼 것.** check가 통과해도 얼굴이 잘리거나
자막이 인물을 가리는 건 잡히지 않는다.

### 6. 오디오 합치기 → 납품

```bash
ffmpeg -y -i renders/<렌더된>.mp4 -i bgm.wav -c:v copy -c:a aac -b:a 192k -shortest 최종.mp4
```

나레이션 음성(`voice.wav`)이 있으면 BGM을 깔개로 낮춰 함께 믹스한다:

```bash
ffmpeg -y -i renders/<렌더된>.mp4 -i voice.wav -i bgm.wav \
  -filter_complex "[2:a]volume=0.35[b];[1:a][b]amix=inputs=2:duration=first[a]" \
  -map 0:v -map "[a]" -c:v copy -c:a aac -b:a 192k -shortest 최종.mp4
```

완성 파일은 `SendUserFile`로 보낸다.

## 스타일 3종

`spec.json`의 `style` 값으로 고른다. 상세는 `references/styles.md`.

| 값 | 이름 | 어울리는 소재 |
|---|---|---|
| `cinematic` | 시네마틱 미스터리 (기본) | 사연, 임사체험 — 어두운 톤, 붉은 강조 |
| `scroll` | 야담 두루마리 | 전설, 옛날 이야기 — 세피아, 강한 그레인 |
| `punchy` | 킬러 쇼츠 훅 | 알고리즘 노출용 — 노란 강조, 굵은 고딕 |

## 화면 비율

`aspect`: `"16:9"`(기본, 카스 선호) 또는 `"9:16"`(쇼츠·릴스).
생성기가 자막 크기·위치·비네트를 비율에 맞춰 자동 조정한다.

## 나레이션 음성 (TTS) — 현재 제약

이 환경에서 **한국어 TTS는 불가능하다.** 확인된 사실:

- `hyperframes tts`(Kokoro-82M): 지원 언어에 한국어 없음 (en/es/fr/hi/it/pt/ja/zh만)
- `edge-tts`: 설치는 되나 마이크로소프트 서버 접근이 이그레스 정책에 막힘

→ 사용자가 **음성 파일을 직접 만들어 주는 것**이 유일한 방법이다.
`scripts/make_voice.py`를 카스에게 안내한다 — spec.json의 자막을 읽어
문장별 mp3를 한 번에 뽑는 스크립트다(카스 PC에서 `pip install edge-tts` 후 실행).
클로바더빙·타입캐스트로 직접 녹음해 주셔도 된다.

음성을 받으면 그 길이에 맞춰 `spec.json`의 자막 타이밍을 다시 잡고 재렌더링한다.

## 하지 말 것

- 결말을 자막으로 공개하기 (티저의 존재 이유가 사라짐)
- 15초 초과 (쇼츠 훅으로서의 기능 상실)
- 스냅샷 확인 없이 렌더링 (3~5분을 날린다)
- `pandoc` 사용 (이 환경에 없음)
