import urllib.request
import urllib.parse
import re
import os
import json
import ssl
import time
from PIL import Image

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
    'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8'
}

BASE_DIR = os.path.join(os.getcwd(), "dataset", "scaled_real_dataset")
os.makedirs(BASE_DIR, exist_ok=True)

CATEGORIES = {
    "triage_hall": [
        "医院门诊排队叫号大屏 现场",
        "门诊候诊区综合显示屏 实拍",
        "医院排队叫号液晶大屏 门诊大厅",
        "门诊大厅叫号大屏 正在就诊"
    ],
    "clinic_door": [
        "医院诊室门口排队显示屏 实拍",
        "诊室门牌叫号屏 液晶",
        "门诊诊室门牌显示屏"
    ],
    "specialty_queue": [
        "医院药房取药排队叫号屏 现场",
        "儿童医院候诊叫号大屏",
        "检验科抽血排队叫号屏",
        "医院排队叫号屏 过号名单"
    ],
    "infusion_real": [
        "输液室 输液架 挂水 现场照片",
        "医院输液瓶 滴液 现场",
        "静脉输液瓶 药液 实拍",
        "门诊输液大厅 护士 输液"
    ],
    "negative_samples": [
        "医院大厅 宣教宣传大屏 播放视频",
        "医院宣传栏 液晶导医屏"
    ]
}

def search_image_urls(query, target_count=15):
    urls = []
    for first_idx in [0, 20, 40]:
        if len(urls) >= target_count:
            break
        url = f"https://www.bing.com/images/async?q={urllib.parse.quote(query)}&count=25&first={first_idx}&scenario=ImageBasicHover"
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, context=ctx, timeout=8) as resp:
                html = resp.read().decode('utf-8', errors='ignore')
                matches = re.findall(r'murl&quot;:&quot;(https?://[^&]+)&quot;', html)
                for m in matches:
                    if m.startswith('http') and m not in urls:
                        urls.append(m)
        except Exception as e:
            print(f"  Warning: search query failed ({e})")
        time.sleep(0.3)
    return urls[:target_count]

downloaded_items = []
total_success = 0

for cat_name, query_list in CATEGORIES.items():
    cat_dir = os.path.join(BASE_DIR, cat_name)
    os.makedirs(cat_dir, exist_ok=True)
    print(f"\n=================== Category: {cat_name} ===================")
    
    cat_count = 0
    seen_urls = set()

    for q in query_list:
        print(f"Fetching search URLs for: {q}...")
        img_urls = search_image_urls(q, target_count=12)
        print(f"  Found {len(img_urls)} potential URLs")

        for u in img_urls:
            if u in seen_urls:
                continue
            seen_urls.add(u)

            filename = f"{cat_name}_{cat_count+1}.jpg"
            filepath = os.path.join(cat_dir, filename)

            try:
                img_req = urllib.request.Request(u, headers=HEADERS)
                with urllib.request.urlopen(img_req, context=ctx, timeout=7) as r:
                    data = r.read()

                # 必须大于 15KB，且能被 PIL 解析
                if len(data) >= 15000:
                    with open(filepath, 'wb') as f:
                        f.write(data)

                    # 校验是否为合法图片且分辨率足够
                    try:
                        with Image.open(filepath) as im:
                            w, h = im.size
                            if w >= 300 and h >= 250:
                                cat_count += 1
                                total_success += 1
                                print(f"  [OK] Saved {filename} ({w}x{h}, {len(data)//1024}KB)")
                                downloaded_items.append({
                                    "id": f"{cat_name}_{cat_count}",
                                    "category": cat_name,
                                    "filename": filename,
                                    "path": filepath,
                                    "width": w,
                                    "height": h,
                                    "size_bytes": len(data),
                                    "url": u
                                })
                            else:
                                os.remove(filepath)
                    except Exception:
                        if os.path.exists(filepath):
                            os.remove(filepath)

                if cat_count >= 18:
                    break
            except Exception:
                pass

        if cat_count >= 18:
            break

print(f"\nTotal real-world valid images downloaded: {total_success}")

# 导出数据集清单
manifest_path = os.path.join(BASE_DIR, "scaled_manifest.json")
with open(manifest_path, "w", encoding="utf-8") as out:
    json.dump({
        "total_images": len(downloaded_items),
        "categories": {k: len([x for x in downloaded_items if x['category'] == k]) for k in CATEGORIES.keys()},
        "items": downloaded_items
    }, out, ensure_ascii=False, indent=2)

print(f"Dataset manifest written to {manifest_path}")
