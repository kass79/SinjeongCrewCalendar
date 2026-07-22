import sys, os
from PIL import Image
sys.stdout.reconfigure(encoding='utf-8')
rot = os.path.join(os.path.dirname(__file__), "jiseon_rot")
dst = r"C:\Users\admin\Downloads\SinjeongCrewCalendar\app\src\main\assets\routes"
os.makedirs(dst, exist_ok=True)
# 평일 pyeongil_1..4 -> bwd_1..8, 휴일 hyuil_1..4 -> bhol_1..8. 한 장 상/하 2다이아, 겹치게 잘라 안 잘리게.
def slice(prefix, tag):
    n = 0
    for i in range(1, 5):
        fp = os.path.join(rot, f"{prefix}_{i}.jpg")
        if not os.path.exists(fp):
            print("MISSING", fp); continue
        im = Image.open(fp); W, H = im.size
        top = im.crop((0, 0, W, int(H*0.545)))
        bot = im.crop((0, int(H*0.455), W, H))
        top.save(os.path.join(dst, f"{tag}_{2*i-1}.webp"), "WEBP", quality=72)
        bot.save(os.path.join(dst, f"{tag}_{2*i}.webp"), "WEBP", quality=72)
        n += 2
    return n
a = slice("pyeongil", "bwd")
b = slice("hyuil", "bhol")
print("saved bwd", a, "bhol", b, "-> assets/routes")
