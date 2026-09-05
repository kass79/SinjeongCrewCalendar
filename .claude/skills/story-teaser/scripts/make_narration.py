#!/usr/bin/env python3
"""spec.json의 자막을 그대로 읽는 일본어 나레이션 생성기 (자막 = 나레이션 보장).

    python3 make_narration.py spec.json --dry            # 가나 변환 결과만 표로 확인
    python3 make_narration.py spec.json --out voice      # TTS 생성 + 선명도 처리 + 길이 측정

- 한자→가나는 pykakasi로 자동 변환하되, 자주 틀리는 단어는 사전으로 먼저 고친다.
- 그래도 이상하면 spec의 해당 자막에 "kana": "..." 를 넣어 덮어쓴다 (최우선).
- 결과 표를 반드시 눈으로 확인할 것 — 변환기는 85% 정확도다.
"""
import argparse, json, re, subprocess, sys
from pathlib import Path

FIX = {  # 변환기가 틀리는 단어 (긴 것부터 매칭)
    "八万八千": "はちまんはっせん", "迎え火": "むかえび", "送り火": "おくりび",
    "提灯": "ちょうちん", "灯り": "あかり", "灯": "ひ", "目印": "めじるし",
    "心臓": "しんぞう", "石段": "いしだん", "本編": "ほんぺん", "臨死": "りんし",
    "誰も": "だれも", "誰か": "だれか", "誰に": "だれに", "誰の": "だれの", "誰": "だれ",
    "今年": "ことし", "八月十三日": "はちがつじゅうさんにち",
    "下りられず": "おりられず", "見下ろした": "みおろした", "見ていた": "みていた",
    "四十五年": "よんじゅうごねん", "三十一分": "さんじゅういっぷん",
    "二十七分": "にじゅうななふん", "十九分": "じゅうきゅうふん",
}
SMALL = "ゃゅょぁぃぅぇぉ"

def to_kana(text: str) -> str:
    t = text
    for k in sorted(FIX, key=len, reverse=True):
        t = t.replace(k, FIX[k])
    import pykakasi
    parts = [x["hira"] for x in pykakasi.kakasi().convert(t)]
    kana = " ".join(p.strip() for p in parts if p.strip())
    kana = re.sub(r"[「」『』]", "", kana)
    kana = re.sub(r"[—―…]+", "、", kana)
    kana = re.sub(r"\s*([、。])\s*", r"\1", kana)      # 문장부호 앞뒤 공백 제거
    kana = re.sub(r"[、。]{2,}", "。", kana)
    kana = re.sub(r" (?=[っ])", "", kana)                        # 止ま った → 止まった
    kana = re.sub(r" ([にがはをのでともへ])(?=$| |[、。])", r"\1", kana)  # 조사는 앞말에 붙임
    return kana.strip()

def morae(k: str) -> int:
    return sum(1 for c in k if c not in SMALL and c not in " 、。")

def dur(f):
    return float(subprocess.run(["ffprobe","-v","error","-show_entries","format=duration",
                                 "-of","csv=p=0",f],capture_output=True,text=True).stdout or 0)

def speech_dur(f, tmp):
    subprocess.run(["ffmpeg","-y","-v","error","-i",f,"-af",
        "silenceremove=start_periods=1:start_threshold=-40dB:stop_periods=-1:stop_threshold=-40dB",
        tmp],capture_output=True)
    return dur(tmp)

CLEAN = ("highpass=f=90,equalizer=f=3000:width_type=q:width=1:g=3,"
         "equalizer=f=250:width_type=q:width=1:g=-2,"
         "acompressor=threshold=-20dB:ratio=3:attack=8:release=120:makeup=2,"
         "loudnorm=I=-15:TP=-1.5:LRA=7")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("spec"); ap.add_argument("--out", default="voice")
    ap.add_argument("--voice", default="jm_kumo"); ap.add_argument("--speed", default="0.9")
    ap.add_argument("--dry", action="store_true"); ap.add_argument("--no-end", action="store_true")
    a = ap.parse_args()
    spec = json.loads(Path(a.spec).read_text(encoding="utf-8"))

    items = []  # (tag, caption_text, kana, start, dur)
    for i, c in enumerate(spec.get("captions", []), 1):
        text = "。".join(x for x in (c.get("line1",""), c.get("line2","")) if x.strip())
        kana = c.get("kana") or to_kana(text)
        items.append((f"{i:02d}", text, kana, float(c["start"]), float(c["dur"])))
    end = spec.get("endcard") or {}
    if not a.no_end:
        text = "。".join(x for x in (end.get("line1",""), end.get("line2","")) if x.strip())
        if text:
            kana = end.get("kana") or to_kana(text)
            items.append((f"{len(items)+1:02d}_end", text, kana, float(end.get("start", 0)), 0.0))

    print(f"{'#':<7}{'자막':<28}→ 가나 (눈으로 확인!)")
    for tag, text, kana, *_ in items:
        print(f"{tag:<7}{text:<28}→ {kana}")
    if a.dry:
        return

    out = Path(a.out); clean = Path(a.out + "_clean")
    out.mkdir(exist_ok=True); clean.mkdir(exist_ok=True)
    print(f"\nTTS {a.voice} / 속도 {a.speed}\n{'#':<7}{'전체s':>6}{'발화s':>6}{'모라/초':>7}  {'자막dur':>6}  판정")
    result = []
    for tag, text, kana, start, cdur in items:
        raw = out / f"{tag}.wav"; cln = clean / f"{tag}.wav"
        subprocess.run(["npx","hyperframes","tts",kana,"-v",a.voice,"-s",a.speed,"-o",str(raw)],
                       capture_output=True, timeout=240)
        subprocess.run(["ffmpeg","-y","-v","error","-i",str(raw),"-af",CLEAN,str(cln)],capture_output=True)
        d = dur(str(cln)); sp = speech_dur(str(cln), "/tmp/_sp.wav"); r = morae(kana)/sp if sp else 0
        flag = "⚠ 자막보다 김" if cdur and d > cdur + 0.2 else ("빠름" if r > 10 else "OK")
        print(f"{tag:<7}{d:>6.2f}{sp:>6.2f}{r:>7.1f}  {cdur:>6.1f}  {flag}")
        result.append({"tag": tag, "text": text, "kana": kana, "start": start,
                       "caption_dur": cdur, "voice_dur": round(d, 2), "file": str(cln)})
    Path("narration.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")

    # 믹스 명령 조각 출력 (BGM이 amix 첫 입력)
    ins = " ".join(f"-i {r['file']}" for r in result)
    delays = ";".join(f"[{i+1}:a]adelay={int((r['start']+0.1)*1000)}|{int((r['start']+0.1)*1000)}[v{i+1}]"
                      for i, r in enumerate(result))
    vs = "".join(f"[v{i+1}]" for i in range(len(result)))
    n = len(result) + 1
    print(f"\n# 믹스 (렌더된 mp4를 -i 첫 입력으로):\nffmpeg -y -i RENDER.mp4 {ins} -i bgm.wav -filter_complex \""
          f"{delays};[{n}:a]volume=0.22[b];[b]{vs}amix=inputs={n}:duration=first:normalize=0,"
          f"aformat=channel_layouts=stereo[a]\" -map 0:v -map \"[a]\" -c:v copy -c:a aac -b:a 192k -shortest 최종.mp4")

if __name__ == "__main__":
    main()
