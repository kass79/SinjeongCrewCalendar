import sys, os, glob
from PIL import Image
sys.stdout.reconfigure(encoding='utf-8')
src = r"C:\Users\admin\Downloads\지선행로표"
dst = os.path.join(os.path.dirname(__file__), "jiseon_rot")
os.makedirs(dst, exist_ok=True)
folders = ["지평일","지휴일","지평휴","지휴평","지펑평","지휴휴"]
tags = {"지평일":"pyeongil","지휴일":"hyuil","지평휴":"pyeonghyu","지휴평":"hyupyeong","지펑평":"pyeongpyeong","지휴휴":"hyuhyu"}
cnt = 0
for f in folders:
    files = sorted(glob.glob(os.path.join(src, f, "*.jpg")))
    for i, fp in enumerate(files, 1):
        im = Image.open(fp)
        # 문서가 세로가 되도록 반시계 90도
        im = im.rotate(-90, expand=True)
        # 폭 1400 기준 리사이즈
        w = 1400; h = int(im.height * w / im.width)
        im = im.resize((w, h), Image.LANCZOS)
        im.save(os.path.join(dst, f"{tags[f]}_{i}.jpg"), quality=85)
        cnt += 1
print("rotated", cnt, "-> ", dst)
