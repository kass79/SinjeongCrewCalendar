#!/usr/bin/env python3
"""서울 열린데이터광장 역별 열차 시간표 → app/src/main/assets/timetable/line2.csv
사용: python tools/fetch_line2_timetable.py [--key KEY]   (키 생략 시 BranchLive.kt 마지막 키)
"""
import argparse, json, re, sys, time, urllib.parse, urllib.request, datetime, pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
STATIONS = ["시청","을지로입구","을지로3가","을지로4가","동대문역사문화공원","신당","상왕십리","왕십리","한양대","뚝섬",
    "성수","건대입구","구의","강변","잠실나루","잠실","잠실새내","종합운동장","삼성","선릉","역삼","강남","교대","서초","방배",
    "사당","낙성대","서울대입구","봉천","신림","신대방","구로디지털단지","대림","신도림","문래","영등포구청","당산","합정",
    "홍대입구","신촌","이대","아현","충정로",
    "도림천","양천구청","신정네거리","까치산"]

def key_from_source():
    src = (ROOT / "app/src/main/java/com/sinjeong/crewcalendar/presentation/live/BranchLive.kt").read_text("utf-8")
    return re.findall(r'"([0-9a-f]{30})"', src)[-1]

def get(url):
    err = None
    for i in range(3):
        try:
            with urllib.request.urlopen(url, timeout=20) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:
            err = e; time.sleep(1 + i)
    raise err

def station_code(key, name):
    d = get(f"http://openapi.seoul.go.kr:8088/{key}/json/SearchInfoBySubwayNameService/1/9/{urllib.parse.quote(name)}")
    for r in d["SearchInfoBySubwayNameService"]["row"]:
        if r["LINE_NUM"] == "02호선":
            return r["STATION_CD"]
    raise SystemExit(f"2호선 코드 없음: {name}")

def sec(hms):
    h, m, s = (int(x) for x in hms.split(":"))
    return -1 if (h, m, s) == (0, 0, 0) else h * 3600 + m * 60 + s

def timetable(key, code, week, inout):
    d = get(f"http://openapi.seoul.go.kr:8088/{key}/json/SearchSTNTimeTableByIDService/1/1000/{code}/{week}/{inout}")
    body = d.get("SearchSTNTimeTableByIDService")
    return body.get("row", []) if body else []     # INFO-200 = 데이터 없음

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--key"); a = ap.parse_args()
    key = a.key or key_from_source()
    codes = {}
    for name in STATIONS:
        codes[name] = station_code(key, name); time.sleep(0.2)
    out = []
    for i, name in enumerate(STATIONS):
        for week in (1, 2, 3):
            for inout in (1, 2):
                rows = timetable(key, codes[name], week, inout)
                out += [(week, inout, i, r["TRAIN_NO"], sec(r["ARRIVETIME"]), sec(r["LEFTTIME"])) for r in rows]
                print(f"{name} w{week} io{inout}: {len(rows)}", file=sys.stderr)
                time.sleep(0.2)
    out.sort()
    dst = ROOT / "app/src/main/assets/timetable/line2.csv"
    dst.parent.mkdir(parents=True, exist_ok=True)
    with dst.open("w", encoding="utf-8", newline="\n") as f:
        f.write(f"# fetched={datetime.date.today()} stations={len(STATIONS)} rows={len(out)}\n")
        # inout 의 뜻은 실데이터로 확인한 것이다 — 2006 은 신도림→성수(내선)인데 inout=1 로 왔다.
        # 갱신 때 이 확인이 뒤집히면(=2) 아래 주석과 Line2Timetable.inoutOf 를 함께 뒤집어야 한다.
        checked = next((io for w, io, si, tn, _, _ in out if (w, si, tn) == (1, STATIONS.index("홍대입구"), "2006")), "?")
        f.write("# columns=weekTag,inout,stationIdx,trainNo,arriveSec,leftSec ; weekTag 1=weekday 2=sat 3=holiday ; "
                f"inout 1=inner(내선) 2=outer(외선) ; checked=2006@홍대입구→inout={checked}\n")
        for w, io, si, tn, ar, lf in out:
            f.write(f"{w},{io},{si},{tn},{ar},{lf}\n")
    print(f"wrote {dst} rows={len(out)} bytes={dst.stat().st_size}")

if __name__ == "__main__":
    main()
