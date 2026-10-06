import os
import json
from PIL import Image

REAL_DIR = os.path.join(os.getcwd(), "dataset", "real_world_screens")

def evaluate_quality(img):
    width, height = img.size
    step = 16
    total_brightness = 0
    count = 0
    sum_diff_sq = 0.0

    # 转灰度
    gray = img.convert("L")
    pixels = gray.load()

    for y in range(0, height, step):
        for x in range(0, width - step, step):
            lum = pixels[x, y]
            total_brightness += lum
            count += 1

            next_lum = pixels[x + step, y]
            diff = float(next_lum - lum)
            sum_diff_sq += diff * diff

    if count == 0:
        return {"blurScore": 0, "brightness": 0, "isAcceptable": False}

    avg_brightness = total_brightness / count
    variance = sum_diff_sq / count
    blur_score = min(100.0, max(0.0, variance))
    is_acceptable = (25.0 <= avg_brightness <= 245.0) and (blur_score >= 15.0)

    return {
        "blurScore": round(blur_score, 2),
        "brightness": round(avg_brightness, 2),
        "isAcceptable": is_acceptable
    }

def evaluate_infusion(img):
    width, height = img.size
    gray = img.convert("L")
    pixels = gray.load()

    roi_start_x = int(width * 0.2)
    roi_end_x = int(width * 0.8)
    roi_start_y = int(height * 0.15)
    roi_end_y = int(height * 0.85)

    sample_step_x = 4
    sample_step_y = 2

    row_averages = []
    for y in range(roi_start_y, roi_end_y, sample_step_y):
        sum_lum = 0
        count = 0
        for x in range(roi_start_x, roi_end_x, sample_step_x):
            sum_lum += pixels[x, y]
            count += 1
        row_averages.append(sum_lum / count if count > 0 else 0)

    max_gradient = 0.0
    meniscus_idx = -1
    for i in range(2, len(row_averages) - 2):
        grad = abs(row_averages[i + 1] - row_averages[i - 1])
        if grad > max_gradient and grad > 12.0:
            max_gradient = grad
            meniscus_idx = i

    if meniscus_idx == -1:
        return {"detected": False, "levelPercent": None, "confidence": 0}

    total_span = len(row_averages)
    remaining_height = total_span - meniscus_idx
    level_percent = (remaining_height / total_span) * 100.0
    confidence = min(0.95, max(0.5, max_gradient / 40.0))

    return {
        "detected": True,
        "levelPercent": round(level_percent, 1),
        "confidence": round(confidence, 2)
    }

results = []
print("Evaluating Real-World Images...")
print("-" * 65)

for f in sorted(os.listdir(REAL_DIR)):
    if f.endswith(('.jpg', '.png', '.webp')):
        p = os.path.join(REAL_DIR, f)
        img = Image.open(p)
        q = evaluate_quality(img)
        
        infusion_res = None
        if "infusion" in f:
            infusion_res = evaluate_infusion(img)

        status = "ACCEPTABLE" if q["isAcceptable"] else "REJECTED (Low quality/blurry)"
        print(f"File: {f:<26} | Brightness: {q['brightness']:<6} | Blur: {q['blurScore']:<6} | Status: {status}")
        if infusion_res and infusion_res["detected"]:
            print(f"  --> Infusion Meniscus Detected! Level: {infusion_res['levelPercent']}%, Confidence: {infusion_res['confidence']}")

        results.append({
            "file": f,
            "dimensions": img.size,
            "quality": q,
            "infusion": infusion_res
        })

print("-" * 65)
summary_file = os.path.join(REAL_DIR, "evaluation_report.json")
with open(summary_file, "w", encoding="utf-8") as out:
    json.dump(results, out, ensure_ascii=False, indent=2)

print(f"Evaluation report saved to {summary_file}")
