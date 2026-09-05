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

완성 파일은 `SendUserFile`로 보낸다. **단 30MiB를 넘으면 전송이 거부된다** —
`ffprobe`로 크기를 먼저 확인하고, 넘으면 재인코딩해서 보낸다 (화질 차이 거의 없음):

```bash
ffmpeg -y -i 최종.mp4 -c:v libx264 -preset slow -crf 21 -pix_fmt yuv420p \
  -c:a aac -b:a 160k 최종_압축.mp4   # 실측: 40MB → 13MB
```

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

## 나레이션 음성 (TTS) — 매번 자동 시도할 것

티저 제작 때마다 **edge-tts 접근을 먼저 시도한다** (환경 허용 도메인에
`speech.platform.bing.com`이 추가되어 있으면 성공한다):

```bash
pip install --break-system-packages -q edge-tts
timeout 30 python3 <스킬경로>/scripts/make_voice.py spec.json --out voice
```

**성공하면 (voice/ 에 mp3 생성됨) — 전자동 나레이션 믹스:**

1. 각 mp3 길이를 `ffprobe`로 재고, 문장이 자막 표시 시간보다 길면
   `spec.json`의 해당 자막 `dur`(과 이후 타이밍)을 음성 길이 +0.4초로 늘려 재빌드한다.
2. 문장별 mp3를 자막 시작 시각에 `adelay`로 배치하고, BGM은 -10dB로 덕킹해 믹스:

```bash
ffmpeg -y -i renders/<렌더>.mp4 \
  -i voice/01.mp3 -i voice/02.mp3 -i voice/03.mp3 -i voice/04.mp3 -i voice/05_end.mp3 \
  -i bgm.wav -filter_complex "\
[1:a]adelay=400|400[v1];[2:a]adelay=3500|3500[v2];[3:a]adelay=6500|6500[v3];\
[4:a]adelay=9400|9400[v4];[5:a]adelay=11800|11800[v5];\
[6:a]volume=0.32[b];[b][v1][v2][v3][v4][v5]amix=inputs=6:duration=first:normalize=0,aformat=channel_layouts=stereo[a]" \
  -map 0:v -map "[a]" -c:v copy -c:a aac -b:a 192k -shortest 최종.mp4
```
(adelay 값 = 각 자막 start × 1000ms. spec이 바뀌면 같이 바꿀 것)

기본 목소리 `ko-KR-InJoonNeural`(차분한 남성), 속도 `-8%`. 사용자가 원하면 변경.

**실패하면 (타임아웃/403) — 조용히 BGM만으로 진행한다.** 매번 사용자에게
TTS 안 된다고 반복해 알리지 말 것. 사용자가 음성을 원할 때만: 환경 설정
Network access → Custom에 `speech.platform.bing.com` 추가(새 세션부터 적용)를
안내하거나, 카스 PC에서 `scripts/make_voice.py`를 돌려 mp3를 받는다.
(참고: `hyperframes tts`의 Kokoro 모델은 한국어 미지원이라 대안이 못 된다)

## 일본어 콘텐츠 (검증 완료)

일본어 채널 대본이면:

- 자막·엔드카드를 자연스러운 일본어로 쓰고, spec에 `"font": "\"Noto Serif CJK JP\", serif"` 지정
- **나레이션은 내장 TTS로 즉시 가능** (한국어와 달리 네트워크 불필요):

```bash
pip install --break-system-packages -q kokoro-onnx soundfile
npx hyperframes tts "<문장>" -v jm_kumo -s 0.9 -o voice/01.wav   # 남성 성우 (기본 추천)
npx hyperframes tts "<문장>" -v jf_alpha -s 0.95 -o voice/01.wav # 여성
```

⚠️ **반드시 히라가나/가타카나로 입력할 것.** 한자를 주면 낭독이 망가져
5배쯤 길어진다 (실측: 같은 문장이 한자 24초 vs 가나 2.7초).
자막은 한자, TTS 입력만 가나로 분리한다. 생성 후 각 파일 길이를 `ffprobe`로
재서 자막 타이밍을 맞추고, 위의 나레이션 믹스 명령으로 합친다
(amix 첫 입력은 반드시 BGM — 음성을 먼저 넣으면 영상이 음성 길이로 잘린다).

**TTS 원음은 그대로 쓰면 흐리멍텅하다** (평균 -18.7dB, 저역이 뭉침). 믹스 전에
파일마다 선명도 처리를 거친다 — 검증 완료, "흐리멍텅" 지적을 이걸로 해결했다:

```bash
ffmpeg -y -i voice/01.wav -af "highpass=f=90,\
equalizer=f=3000:width_type=q:width=1:g=3,equalizer=f=250:width_type=q:width=1:g=-2,\
acompressor=threshold=-20dB:ratio=3:attack=8:release=120:makeup=2,\
loudnorm=I=-15:TP=-1.5:LRA=7" voice_clean/01.wav
```
(저역 컷 → 3kHz 프레즌스 +3dB / 250Hz 뭉침 -2dB → 컴프레서 → -15LUFS 정규화)
처리 후 BGM은 `volume=0.22`로 낮춘다. 가나 입력은 **단어 사이를 띄어 쓰면** 억양이 산다.

### 속도는 0.9 · 길이는 15초 안팎 (카스 확정 취향)

`-s 0.9`가 **확정 기본값**이다. 한 번 0.72로 늦춰 19.4초 "또박또박판"을 만들어
15초판과 나란히 들려드렸고, 카스는 **15초·0.9 쪽을 골랐다**. 티저는 속도감이
살아야 한다는 판단이다. 그러니 발화가 다소 빨라 보여도(8~10 모라/초) 임의로
늦추지 말 것. 늦추면 길이가 늘어나고, 길이가 늘면 티저의 성격이 죽는다.

**나레이션은 자막을 그대로, 전부 읽는다.** 자막 두 줄이면 두 줄 다 읽는다.
한 번 나레이션만 반 토막으로 줄였다가 "자막을 다 안 읽는다"는 지적을 받았다.
줄여야 하면 **자막과 나레이션을 함께** 줄인다 — 둘이 어긋나면 안 된다.

말이 빨라 안 들린다는 피드백이 오면 — **속도를 늦추기 전에 문장을 줄인다.**
같은 15초 안에서 모라 수가 줄면 자연히 또박또박해진다.
(참고 측정: 17모라를 1.43초에 읽으면 11.9 모라/초로 속사포. 문장당 14모라
이하로 쓰면 0.9 속도에서도 8 모라/초 안팎이 나온다.)

**길이는 15~17초 사이면 된다.** 자막 전문을 읽느라 15초를 조금 넘는 건
허용 (카스: "15.8초든 16.8초든 상황에 맞게"). 18초를 넘길 때만 자막과
나레이션을 함께 줄인다 — 나레이션만 깎아서 자막과 어긋나게 하지 말 것.

## 밝은 그림일 때 자막 판독성

수채화·파스텔처럼 배경이 밝으면 강조어 대비가 WCAG 3:1에 미달한다.
spec에 다음을 넣으면 해결된다 (검증 완료):

```json
"cap_backdrop": 1.0,      // 하단 그라데이션 강화 + 강조어 뒤 어두운 판
"em_color": "#ff6b6b"     // 어두운 판 위에 올리므로 밝은 붉은색이 더 잘 보인다
```

자막을 크게 요청받으면 `"cap_scale": 1.3` (자막·엔드카드 폰트 일괄 확대).

## 하지 말 것

- 결말을 자막으로 공개하기 (티저의 존재 이유가 사라짐)
- 18초 초과. 15초가 목표지만 **나레이션이 자막을 다 읽는 게 우선**이다 —
  15.8초든 16.8초든 상황에 맞게 잡는다 (카스 확정). 18초를 넘기면 그때 문장을 줄인다
- 스냅샷 확인 없이 렌더링 (3~5분을 날린다)
- `pandoc` 사용 (이 환경에 없음)
