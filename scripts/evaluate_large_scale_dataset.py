import os
import json
import time
import re
from PIL import Image
import numpy as np

try:
    from rapidocr_onnxruntime import RapidOCR
    ocr_engine = RapidOCR()
    OCR_AVAILABLE = True
except Exception as e:
    ocr_engine = None
    OCR_AVAILABLE = False
    print(f"RapidOCR initialization warning: {e}")

BASE_DIR = os.path.join(os.getcwd(), "dataset", "scaled_real_dataset")

# 1. 图像画质评估 (QualityFilter 对应算法)
def evaluate_quality(img):
    w, h = img.size
    gray = img.convert("L")
    pixels = np.array(gray, dtype=np.float32)

    avg_brightness = float(np.mean(pixels))
    
    # 局部方差梯度快速模糊度评分
    step = 16
    sampled = pixels[::step, ::step]
    if sampled.shape[1] > 1:
        diff_h = sampled[:, 1:] - sampled[:, :-1]
        var_h = float(np.mean(diff_h ** 2))
    else:
        var_h = 0.0

    blur_score = min(100.0, max(0.0, var_h))
    is_acceptable = (25.0 <= avg_brightness <= 245.0) and (blur_score >= 12.0)

    return {
        "blurScore": round(blur_score, 2),
        "brightness": round(avg_brightness, 2),
        "isAcceptable": is_acceptable
    }

# 2. 输液液位弯月面检测 (InfusionAnalyzer 对应算法)
def evaluate_infusion(img):
    w, h = img.size
    gray = img.convert("L")
    arr = np.array(gray, dtype=np.float32)

    roi_x1 = int(w * 0.20)
    roi_x2 = int(w * 0.80)
    roi_y1 = int(h * 0.15)
    roi_y2 = int(h * 0.85)

    roi = arr[roi_y1:roi_y2:2, roi_x1:roi_x2:4]
    if roi.size == 0 or roi.shape[0] < 10:
        return {"detected": False, "levelPercent": None, "confidence": 0.0}

    row_avgs = np.mean(roi, axis=1)
    
    # 寻找最大亮度跃变梯度 (界面弯月面/气液交界)
    grads = np.abs(row_avgs[2:] - row_avgs[:-2])
    if len(grads) == 0:
        return {"detected": False, "levelPercent": None, "confidence": 0.0}

    max_idx = int(np.argmax(grads))
    max_grad = float(grads[max_idx])

    if max_grad < 10.0:
        return {"detected": False, "levelPercent": None, "confidence": 0.0}

    total_rows = len(row_avgs)
    remaining_h = total_rows - (max_idx + 1)
    level_percent = (remaining_h / total_rows) * 100.0
    confidence = min(0.95, max(0.50, max_grad / 40.0))

    return {
        "detected": True,
        "levelPercent": round(float(level_percent), 1),
        "isLowLiquid": level_percent <= 15.0,
        "confidence": round(float(confidence), 2)
    }

# 3. 屏幕排队号与多科室语义解析 (LayoutSemanticParser 对应算法)
CALL_KEYWORDS = ["正在就诊", "当前呼叫", "正在呼叫", "请就诊", "请到", "就诊中", "当前", "呼叫", "叫号"]
WAIT_KEYWORDS = ["候诊", "等候", "等待", "请等待", "准备就诊", "排队中"]
OVERCALL_KEYWORDS = ["过号", "已过号", "未到过号", "过号重排"]

TICKET_PATTERN = re.compile(r'([A-Za-z]?\d{2,4})')

def parse_screen_semantics(ocr_results):
    if not ocr_results:
        return {
            "hasQueueContent": False,
            "textLineCount": 0,
            "currentCalling": [],
            "waiting": [],
            "overcall": [],
            "advanceWarningRank": None
        }

    lines = []
    current_calling = []
    waiting = []
    overcall = []

    for box, text, score in ocr_results:
        text = text.strip()
        if not text:
            continue
        lines.append(text)

        # 检查是否包含呼叫关键词
        if any(k in text for k in CALL_KEYWORDS):
            matches = TICKET_PATTERN.findall(text)
            for m in matches:
                if m not in current_calling:
                    current_calling.append(m)

        # 检查是否包含候诊关键词
        if any(k in text for k in WAIT_KEYWORDS):
            matches = TICKET_PATTERN.findall(text)
            for m in matches:
                if m not in waiting and m not in current_calling:
                    waiting.append(m)

        # 检查是否包含过号关键词
        if any(k in text for k in OVERCALL_KEYWORDS):
            matches = TICKET_PATTERN.findall(text)
            for m in matches:
                if m not in overcall:
                    overcall.append(m)

    # 兜底：如果行内未显式带关键词，但出现典型排队号段且有大量编号
    all_tickets = []
    for line in lines:
        for m in TICKET_PATTERN.findall(line):
            if m not in all_tickets:
                all_tickets.append(m)

    if not current_calling and all_tickets:
        current_calling.append(all_tickets[0])
        waiting.extend(all_tickets[1:5])

    has_queue = len(current_calling) > 0 or len(waiting) > 0

    return {
        "hasQueueContent": has_queue,
        "textLineCount": len(lines),
        "currentCalling": current_calling,
        "waiting": waiting,
        "overcall": overcall,
        "totalExtractedTickets": len(all_tickets)
    }

def main():
    print("=" * 80)
    print("STARTING LARGE-SCALE REAL-WORLD BENCHMARK EVALUATION")
    print("=" * 80)

    categories = ["triage_hall", "clinic_door", "specialty_queue", "infusion_real", "negative_samples"]
    category_stats = {}
    detailed_reports = []

    start_total_time = time.time()

    for cat in categories:
        cat_dir = os.path.join(BASE_DIR, cat)
        if not os.path.exists(cat_dir):
            continue

        img_files = sorted([f for f in os.listdir(cat_dir) if f.endswith(('.jpg', '.png', '.jpeg', '.webp'))])
        print(f"\n[Evaluating Category: {cat}] ({len(img_files)} images)", flush=True)

        cat_pass_count = 0
        cat_queue_detected_count = 0
        cat_infusion_detected_count = 0
        cat_negative_rejected_count = 0
        cat_latencies = []

        for idx, filename in enumerate(img_files):
            file_path = os.path.join(cat_dir, filename)
            t0 = time.time()
            try:
                img = Image.open(file_path)
                quality = evaluate_quality(img)
                latency_ms = (time.time() - t0) * 1000.0

                if quality["isAcceptable"]:
                    cat_pass_count += 1

                # 输液评估
                infusion_res = None
                if cat == "infusion_real":
                    infusion_res = evaluate_infusion(img)
                    if infusion_res["detected"]:
                        cat_infusion_detected_count += 1

                # OCR 与语义评估
                ocr_semantics = None
                if OCR_AVAILABLE and cat in ["triage_hall", "clinic_door", "specialty_queue", "negative_samples"]:
                    ocr_t0 = time.time()
                    np_img = np.array(img)
                    ocr_res, _ = ocr_engine(np_img)
                    latency_ms += (time.time() - ocr_t0) * 1000.0
                    ocr_semantics = parse_screen_semantics(ocr_res)

                    if cat in ["triage_hall", "clinic_door", "specialty_queue"]:
                        if ocr_semantics["hasQueueContent"]:
                            cat_queue_detected_count += 1
                    elif cat == "negative_samples":
                        # 负样本应不含活跃叫号/候诊
                        if not ocr_semantics["hasQueueContent"]:
                            cat_negative_rejected_count += 1

                cat_latencies.append(latency_ms)

                # 打印单张进度
                status_str = "OK" if quality["isAcceptable"] else "LOW_QUALITY"
                extra_str = ""
                if cat == "infusion_real" and infusion_res:
                    extra_str = f" | Level: {infusion_res['levelPercent']}%" if infusion_res['detected'] else " | No Meniscus"
                elif ocr_semantics:
                    extra_str = f" | Calling: {ocr_semantics['currentCalling'][:2]} | Wait: {len(ocr_semantics['waiting'])}"

                if idx < 10 or idx % 10 == 0:
                    print(f"  [{idx+1}/{len(img_files)}] {filename:<24} | Brightness: {quality['brightness']:<5} | Blur: {quality['blurScore']:<5} | Latency: {int(latency_ms)}ms{extra_str}", flush=True)

                detailed_reports.append({
                    "category": cat,
                    "filename": filename,
                    "resolution": list(img.size),
                    "quality": quality,
                    "infusion": infusion_res,
                    "semantics": ocr_semantics,
                    "latencyMs": round(latency_ms, 1)
                })

            except Exception as e:
                print(f"  [ERROR] {filename}: {e}", flush=True)

        avg_lat = sum(cat_latencies) / len(cat_latencies) if cat_latencies else 0
        category_stats[cat] = {
            "totalImages": len(img_files),
            "qualityPassCount": cat_pass_count,
            "qualityPassRate": round((cat_pass_count / len(img_files) * 100.0) if img_files else 0, 1),
            "queueDetectedCount": cat_queue_detected_count,
            "infusionDetectedCount": cat_infusion_detected_count,
            "negativeRejectedCount": cat_negative_rejected_count,
            "avgLatencyMs": round(avg_lat, 1)
        }

    total_time = round(time.time() - start_total_time, 2)
    total_imgs = len(detailed_reports)
    total_pass = sum(c["qualityPassCount"] for c in category_stats.values())
    overall_quality_pass_rate = round((total_pass / total_imgs * 100.0) if total_imgs else 0, 1)

    print("\n" + "=" * 80)
    print("LARGE-SCALE BENCHMARK EVALUATION SUMMARY")
    print("=" * 80)
    print(f"Total Evaluated Images: {total_imgs}")
    print(f"Total Elapsed Time: {total_time}s")
    print(f"Overall Quality Pass Rate: {overall_quality_pass_rate}%\n")

    for cat, stat in category_stats.items():
        print(f"Category: {cat:<18}")
        print(f"  - Total: {stat['totalImages']}")
        print(f"  - Quality Pass Rate: {stat['qualityPassRate']}% ({stat['qualityPassCount']}/{stat['totalImages']})")
        if cat in ["triage_hall", "clinic_door", "specialty_queue"]:
            q_rate = round((stat['queueDetectedCount'] / stat['totalImages']) * 100.0, 1) if stat['totalImages'] else 0
            print(f"  - Screen Queue Parse Hit Rate: {q_rate}% ({stat['queueDetectedCount']}/{stat['totalImages']})")
        elif cat == "infusion_real":
            inf_rate = round((stat['infusionDetectedCount'] / stat['totalImages']) * 100.0, 1) if stat['totalImages'] else 0
            print(f"  - Meniscus Detection Hit Rate: {inf_rate}% ({stat['infusionDetectedCount']}/{stat['totalImages']})")
        elif cat == "negative_samples":
            neg_rate = round((stat['negativeRejectedCount'] / stat['totalImages']) * 100.0, 1) if stat['totalImages'] else 0
            print(f"  - Negative Sample Anti-False-Alarm Rejection Rate: {neg_rate}% ({stat['negativeRejectedCount']}/{stat['totalImages']})")
        print(f"  - Avg Latency: {stat['avgLatencyMs']} ms")

    summary_file = os.path.join(BASE_DIR, "large_scale_benchmark_results.json")
    with open(summary_file, "w", encoding="utf-8") as out:
        json.dump({
            "totalImages": total_imgs,
            "overallQualityPassRate": overall_quality_pass_rate,
            "categoryStats": category_stats,
            "totalElapsedTimeSec": total_time,
            "detailedReports": detailed_reports
        }, out, ensure_ascii=False, indent=2)

    print(f"\nSaved detailed evaluation benchmark results to: {summary_file}")
    print("=" * 80)

if __name__ == "__main__":
    main()
