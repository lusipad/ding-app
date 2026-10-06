import urllib.request
import urllib.parse
import re
import os
import json
import ssl

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
}

OUTPUT_DIR = os.path.join(os.getcwd(), "dataset", "real_world_screens")
os.makedirs(OUTPUT_DIR, exist_ok=True)

def search_bing_images(query, max_count=5):
    print(f"Searching images for: {query}")
    url = f"https://www.bing.com/images/async?q={urllib.parse.quote(query)}&count={max_count}&first=0&scenario=ImageBasicHover"
    req = urllib.request.Request(url, headers=HEADERS)
    image_urls = []
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=12) as resp:
            html = resp.read().decode('utf-8', errors='ignore')
            # 提取 murl (media url)
            matches = re.findall(r'murl&quot;:&quot;(https?://[^&]+)&quot;', html)
            for m in matches:
                if m.endswith(('.jpg', '.jpeg', '.png', '.webp')) or 'image' in m:
                    image_urls.append(m)
                    if len(image_urls) >= max_count:
                        break
    except Exception as e:
        print(f"Error searching for {query}: {e}")
    return image_urls

queries = [
    ("医院 门诊 叫号屏 现场", "hospital_queue_screen"),
    ("医院 诊室门口 显示屏 叫号", "clinic_door_screen"),
    ("门诊排队叫号大屏 液晶屏", "triage_hall_screen"),
    ("输液瓶 滴液 护士", "infusion_bottle_real")
]

downloaded_records = []

for q, prefix in queries:
    urls = search_bing_images(q, max_count=4)
    print(f"Found {len(urls)} URLs for {prefix}")
    for idx, u in enumerate(urls):
        try:
            filename = f"{prefix}_{idx+1}.jpg"
            filepath = os.path.join(OUTPUT_DIR, filename)
            img_req = urllib.request.Request(u, headers=HEADERS)
            with urllib.request.urlopen(img_req, context=ctx, timeout=10) as r:
                data = r.read()
                if len(data) > 10000: # 至少 10KB
                    with open(filepath, 'wb') as f:
                        f.write(data)
                    print(f"  Successfully downloaded: {filename} ({len(data)} bytes)")
                    downloaded_records.append({
                        "filename": filename,
                        "path": filepath,
                        "url": u,
                        "query": q,
                        "prefix": prefix,
                        "size": len(data)
                    })
        except Exception as e:
            print(f"  Failed to download from {u}: {e}")

manifest_file = os.path.join(OUTPUT_DIR, "real_manifest.json")
with open(manifest_file, "w", encoding="utf-8") as f:
    json.dump(downloaded_records, f, ensure_ascii=False, indent=2)

print(f"\nDone! Downloaded {len(downloaded_records)} real images into {OUTPUT_DIR}")
