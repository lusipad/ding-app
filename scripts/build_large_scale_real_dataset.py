import urllib.request
import urllib.parse
import re
import os
import json
import ssl
import time
import hashlib
from concurrent.futures import ThreadPoolExecutor, as_completed
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
        "门诊大厅叫号大屏 正在就诊",
        "三甲医院门诊大厅排队显示屏",
        "门诊综合候诊大屏幕 实景",
        "医院多科室排队综合大屏 现场",
        "门诊排队信息大屏 液晶电视"
    ],
    "clinic_door": [
        "医院诊室门口排队显示屏 实拍",
        "诊室门牌叫号屏 液晶",
        "门诊诊室门牌显示屏",
        "诊室门口液晶显示屏 正在呼叫",
        "医院诊区门牌排队屏 现场",
        "诊室外壁挂排队机显示屏",
        "门诊专科诊室门口排队屏"
    ],
    "specialty_queue": [
        "医院药房取药排队叫号屏 现场",
        "儿童医院候诊叫号大屏",
        "检验科抽血排队叫号屏",
        "医院排队叫号屏 过号名单",
        "西药房发药排队显示屏 实拍",
        "医院超声影像排队叫号大屏",
        "放射科候诊排队显示屏",
        "口腔科排队叫号大屏"
    ],
    "infusion_real": [
        "输液室 输液架 挂水 现场照片",
        "医院输液瓶 滴液 现场",
        "静脉输液瓶 药液 实拍",
        "门诊输液大厅 护士 输液",
        "输液架 挂水吊瓶 实拍",
        "医院静脉滴注 输液瓶 药水",
        "输液大厅 塑料输液瓶 吊瓶",
        "儿科输液室 挂水 滴液瓶"
    ],
    "negative_samples": [
        "医院大厅 宣教宣传大屏 播放视频",
        "医院宣传栏 液晶导医屏",
        "医院大厅 导医指南液晶屏",
        "医院门诊大厅 楼层导览图指示牌",
        "医院走廊 等候座椅 无屏幕背景",
        "医院宣传橱窗 科普宣教展板"
    ]
}

TARGET_PER_CATEGORY = 50  # 50 * 5 = 250 张真实场景高分辨率照片

def search_bing(query, count=40):
    urls = []
    for first_idx in [0, 30, 60]:
        url = f"https://www.bing.com/images/async?q={urllib.parse.quote(query)}&count={count}&first={first_idx}&scenario=ImageBasicHover"
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, context=ctx, timeout=6) as resp:
                html = resp.read().decode('utf-8', errors='ignore')
                matches = re.findall(r'murl&quot;:&quot;(https?://[^&]+)&quot;', html)
                for m in matches:
                    if m.startswith('http') and m not in urls:
                        urls.append(m)
        except Exception:
            pass
        time.sleep(0.1)
    return urls

def search_ddg(query, max_res=35):
    urls = []
    try:
        from ddgs import DDGS
        with DDGS() as ddgs:
            for r in ddgs.images(query, max_results=max_res):
                u = r.get('image')
                if u and u.startswith('http') and u not in urls:
                    urls.append(u)
    except Exception:
        pass
    return urls

def download_single_image(url):
    try:
        req = urllib.request.Request(url, headers=HEADERS)
        with urllib.request.urlopen(req, context=ctx, timeout=5) as resp:
            data = resp.read()
        if len(data) < 15000:
            return None, None
        return data, hashlib.md5(data).hexdigest()
    except Exception:
        return None, None

def main():
    existing_hashes = set()
    category_counts = {}

    for cat in CATEGORIES.keys():
        cat_dir = os.path.join(BASE_DIR, cat)
        os.makedirs(cat_dir, exist_ok=True)
        files = [f for f in os.listdir(cat_dir) if f.endswith(('.jpg', '.png', '.jpeg', '.webp'))]
        category_counts[cat] = len(files)
        for f in files:
            fp = os.path.join(cat_dir, f)
            try:
                with open(fp, 'rb') as hf:
                    existing_hashes.add(hashlib.md5(hf.read()).hexdigest())
            except Exception:
                pass

    print(f"Current initial counts: {category_counts}", flush=True)
    print(f"Loaded {len(existing_hashes)} existing unique image hashes.", flush=True)

    for cat_name, query_list in CATEGORIES.items():
        cat_dir = os.path.join(BASE_DIR, cat_name)
        current = category_counts[cat_name]
        needed = TARGET_PER_CATEGORY - current
        if needed <= 0:
            print(f"Category {cat_name} already has {current} images (target: {TARGET_PER_CATEGORY}). Skipping.", flush=True)
            continue

        print(f"\n>>> Crawling category: {cat_name} (Need {needed} more to reach {TARGET_PER_CATEGORY})", flush=True)
        url_pool = []
        for q in query_list:
            b_urls = search_bing(q, count=40)
            d_urls = search_ddg(q, max_res=35)
            for u in b_urls + d_urls:
                if u not in url_pool:
                    url_pool.append(u)
            if len(url_pool) >= needed * 3:
                break

        print(f"Gathered {len(url_pool)} candidate URLs. Launching parallel download threads...", flush=True)

        with ThreadPoolExecutor(max_workers=14) as executor:
            futures = {executor.submit(download_single_image, u): u for u in url_pool}
            for fut in as_completed(futures):
                if current >= TARGET_PER_CATEGORY:
                    break
                data, md5_val = fut.result()
                if not data or not md5_val or md5_val in existing_hashes:
                    continue

                filename = f"{cat_name}_{current + 1}.jpg"
                filepath = os.path.join(cat_dir, filename)

                try:
                    with open(filepath, 'wb') as wf:
                        wf.write(data)

                    with Image.open(filepath) as im:
                        w, h = im.size
                        if w < 280 or h < 240:
                            os.remove(filepath)
                            continue

                    existing_hashes.add(md5_val)
                    current += 1
                    category_counts[cat_name] = current
                    print(f"  [Cat {cat_name}] {current}/{TARGET_PER_CATEGORY} saved: {filename} ({w}x{h}, {len(data)//1024}KB)", flush=True)
                except Exception:
                    if os.path.exists(filepath):
                        os.remove(filepath)

    print("\n" + "=" * 60, flush=True)
    print("Download completed! Final category counts:", flush=True)
    for cat, cnt in category_counts.items():
        print(f"  - {cat}: {cnt} images", flush=True)
    total_imgs = sum(category_counts.values())
    print(f"Total dataset size: {total_imgs} images", flush=True)

    # 生成新清单
    all_items = []
    for cat in CATEGORIES.keys():
        cat_dir = os.path.join(BASE_DIR, cat)
        files = sorted([f for f in os.listdir(cat_dir) if f.endswith(('.jpg', '.png', '.jpeg', '.webp'))])
        for f in files:
            fp = os.path.join(cat_dir, f)
            try:
                with Image.open(fp) as im:
                    w, h = im.size
                all_items.append({
                    "category": cat,
                    "filename": f,
                    "path": fp,
                    "width": w,
                    "height": h,
                    "size_bytes": os.path.getsize(fp)
                })
            except Exception:
                pass

    manifest_path = os.path.join(BASE_DIR, "scaled_manifest.json")
    with open(manifest_path, "w", encoding="utf-8") as out:
        json.dump({
            "total_images": len(all_items),
            "categories": {cat: len([x for x in all_items if x['category'] == cat]) for cat in CATEGORIES.keys()},
            "items": all_items
        }, out, ensure_ascii=False, indent=2)

    print(f"Manifest saved to: {manifest_path}", flush=True)

if __name__ == "__main__":
    main()
