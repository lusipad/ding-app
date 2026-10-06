import os
import json
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUTPUT_DIR = os.path.join(os.getcwd(), "dataset")
SCREENS_DIR = os.path.join(OUTPUT_DIR, "screens")
INFUSION_DIR = os.path.join(OUTPUT_DIR, "infusion")

os.makedirs(SCREENS_DIR, exist_ok=True)
os.makedirs(INFUSION_DIR, exist_ok=True)

# 尝试获取中文字体，Windows 下使用 msyh.ttc 或 simhei.ttf
font_path = "C:\\Windows\\Fonts\\msyh.ttc"
if not os.path.exists(font_path):
    font_path = "C:\\Windows\\Fonts\\simhei.ttf"

def get_font(size):
    try:
        return ImageFont.truetype(font_path, size)
    except:
        return ImageFont.load_default()

dataset_manifest = []

# 1. 单诊室确认命中
def gen_single_clinic_hit():
    img = Image.new("RGB", (900, 600), color=(15, 25, 45))
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, 900, 90], fill=(25, 55, 105))
    draw.text((40, 25), "综合门诊部 智能排队叫号系统", fill=(255, 255, 255), font=get_font(34))
    
    # 主卡片
    draw.rectangle([40, 120, 860, 340], fill=(22, 38, 70))
    draw.text((60, 140), "【3号诊室】正在就诊", fill=(255, 215, 0), font=get_font(32))
    draw.text((60, 210), "请 128 号 到 3号诊室就诊", fill=(255, 75, 75), font=get_font(52))
    
    # 候诊
    draw.rectangle([40, 360, 860, 460], fill=(22, 38, 70))
    draw.text((60, 395), "候诊等待: 129号, 130号, 131号", fill=(140, 210, 255), font=get_font(28))
    
    # 历史
    draw.rectangle([40, 480, 860, 570], fill=(22, 38, 70))
    draw.text((60, 505), "历史呼叫: 126号, 127号", fill=(180, 180, 180), font=get_font(24))
    
    path = os.path.join(SCREENS_DIR, "screen_single_clinic_hit.png")
    img.save(path)
    dataset_manifest.append({
        "id": "screen_single_clinic_hit",
        "file": path,
        "type": "hospital_screen",
        "scenario": "single_clinic",
        "expectedClinic": "3号诊室",
        "expectedCalling": "128",
        "targetNumber": "128",
        "expectedResult": "CONFIRMED_MATCH",
        "description": "单诊室清晰大屏正在呼叫128号"
    })

# 2. 多科室矩阵隔离测试（1诊室叫128，用户等3诊室）
def gen_multi_clinic_isolation():
    img = Image.new("RGB", (960, 640), color=(18, 28, 48))
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, 960, 85], fill=(30, 60, 110))
    draw.text((40, 24), "中心医院 门诊部多科室综合叫号大屏", fill=(255, 255, 255), font=get_font(32))
    
    # 行 1: 儿科 1号诊室
    draw.rectangle([30, 110, 930, 220], fill=(25, 42, 75))
    draw.text((50, 140), "儿科门诊 · 1号诊室", fill=(255, 210, 0), font=get_font(26))
    draw.text((360, 135), "当前呼叫: 128号", fill=(255, 90, 90), font=get_font(32))
    draw.text((650, 145), "候诊: 129号, 130号", fill=(150, 200, 255), font=get_font(22))
    
    # 行 2: 内科 2号诊室
    draw.rectangle([30, 240, 930, 350], fill=(25, 42, 75))
    draw.text((50, 270), "消化内科 · 2号诊室", fill=(255, 210, 0), font=get_font(26))
    draw.text((360, 265), "当前呼叫: 045号", fill=(255, 90, 90), font=get_font(32))
    draw.text((650, 275), "候诊: 046号, 047号", fill=(150, 200, 255), font=get_font(22))
    
    # 行 3: 外科 3号诊室 (用户实际等候的诊室)
    draw.rectangle([30, 370, 930, 480], fill=(25, 42, 75))
    draw.text((50, 400), "外科门诊 · 3号诊室", fill=(255, 210, 0), font=get_font(26))
    draw.text((360, 395), "当前呼叫: 124号", fill=(255, 90, 90), font=get_font(32))
    draw.text((650, 405), "候诊: 125号, 126号, 127号, 128号", fill=(150, 200, 255), font=get_font(22))
    
    path = os.path.join(SCREENS_DIR, "screen_multi_clinic_isolation.png")
    img.save(path)
    dataset_manifest.append({
        "id": "screen_multi_clinic_isolation",
        "file": path,
        "type": "hospital_screen",
        "scenario": "multi_clinic",
        "expectedClinic": "3号诊室",
        "expectedDepartment": "外科门诊",
        "expectedCalling": "124",
        "targetNumber": "128",
        "expectedResult": "WAITING_LIST",
        "shouldNotMatchClinic1": True,
        "description": "多科室分屏中1诊室叫128号，用户等3诊室，应严格隔离不产生误报"
    })

# 3. 提前叫号预警
def gen_advance_warning():
    img = Image.new("RGB", (900, 600), color=(15, 25, 45))
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, 900, 90], fill=(25, 55, 105))
    draw.text((40, 25), "眼科门诊 排队叫号系统", fill=(255, 255, 255), font=get_font(34))
    
    draw.rectangle([40, 120, 860, 340], fill=(22, 38, 70))
    draw.text((60, 140), "【3号诊室】正在就诊", fill=(255, 215, 0), font=get_font(32))
    draw.text((60, 210), "请 126 号 到 3号诊室就诊", fill=(255, 75, 75), font=get_font(50))
    
    draw.rectangle([40, 360, 860, 460], fill=(22, 38, 70))
    draw.text((60, 395), "候诊等待: 127号, 128号, 129号", fill=(140, 210, 255), font=get_font(28))
    
    path = os.path.join(SCREENS_DIR, "screen_advance_warning.png")
    img.save(path)
    dataset_manifest.append({
        "id": "screen_advance_warning",
        "file": path,
        "type": "hospital_screen",
        "scenario": "advance_warning",
        "expectedClinic": "3号诊室",
        "expectedCalling": "126",
        "targetNumber": "128",
        "expectedResult": "PRE_CALL_WARNING",
        "aheadCount": 1,
        "description": "当前126号，目标128号在候诊排第2位（前面剩1人，触发提前叫号预警）"
    })

# 4. 显式过号
def gen_explicit_overcall():
    img = Image.new("RGB", (900, 600), color=(15, 25, 45))
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, 900, 90], fill=(25, 55, 105))
    draw.text((40, 25), "专家门诊 智能排队叫号系统", fill=(255, 255, 255), font=get_font(34))
    
    draw.rectangle([40, 120, 860, 320], fill=(22, 38, 70))
    draw.text((60, 140), "【3号诊室】正在就诊", fill=(255, 215, 0), font=get_font(32))
    draw.text((60, 210), "请 135 号 就诊", fill=(255, 75, 75), font=get_font(52))
    
    # 过号栏
    draw.rectangle([40, 340, 860, 470], fill=(45, 22, 22))
    draw.text((60, 360), "【过号 / 弃号名单】请至分诊台刷卡激活", fill=(255, 120, 120), font=get_font(26))
    draw.text((60, 410), "过号人员: 122号, 128号, 131号", fill=(255, 180, 180), font=get_font(28))
    
    path = os.path.join(SCREENS_DIR, "screen_explicit_overcall.png")
    img.save(path)
    dataset_manifest.append({
        "id": "screen_explicit_overcall",
        "file": path,
        "type": "hospital_screen",
        "scenario": "explicit_overcall",
        "expectedClinic": "3号诊室",
        "expectedCalling": "135",
        "targetNumber": "128",
        "expectedResult": "MISSED_CALL",
        "description": "目标128号明确出现在大屏过号名单栏中"
    })

# 5. 严重模糊反光（质量过滤测试）
def gen_blurry_noisy():
    img = Image.new("RGB", (800, 500), color=(100, 110, 120))
    draw = ImageDraw.Draw(img)
    draw.text((50, 100), "3号诊室 128号", fill=(120, 125, 130), font=get_font(20))
    # 极度高斯模糊模拟移动手抖
    blurred = img.filter(ImageFilter.GaussianBlur(radius=15))
    path = os.path.join(SCREENS_DIR, "screen_blurry_noisy.png")
    blurred.save(path)
    dataset_manifest.append({
        "id": "screen_blurry_noisy",
        "file": path,
        "type": "hospital_screen",
        "scenario": "blurry_noise",
        "expectedResult": "REJECTED_BLURRY",
        "description": "严重手抖运动模糊，应触发质量过滤拦截"
    })

# 6. 输液瓶正常液位与低液位
def gen_infusion_bottles():
    # 6.1 正常高液位 75%
    img_normal = Image.new("RGB", (400, 600), color=(30, 35, 45))
    draw_n = ImageDraw.Draw(img_normal)
    draw_n.rounded_rectangle([120, 80, 280, 520], radius=25, outline=(220, 220, 220), width=4)
    # 液位 (75%)
    liquid_top_n = 520 - int((520 - 80) * 0.75)
    draw_n.rectangle([125, liquid_top_n, 275, 515], fill=(50, 150, 235))
    # 液气界面高亮反射线
    draw_n.line([125, liquid_top_n, 275, liquid_top_n], fill=(255, 255, 255), width=3)
    path_n = os.path.join(INFUSION_DIR, "infusion_normal_75.png")
    img_normal.save(path_n)
    dataset_manifest.append({
        "id": "infusion_normal_75",
        "file": path_n,
        "type": "infusion_bottle",
        "liquidPercent": 75,
        "expectedResult": "SAFE_LEVEL",
        "description": "输液瓶余量约为 75%，处于安全状态"
    })

    # 6.2 临界低液位 10% (低于 15% 报警)
    img_low = Image.new("RGB", (400, 600), color=(30, 35, 45))
    draw_l = ImageDraw.Draw(img_low)
    draw_l.rounded_rectangle([120, 80, 280, 520], radius=25, outline=(220, 220, 220), width=4)
    # 液位 (10%)
    liquid_top_l = 520 - int((520 - 80) * 0.10)
    draw_l.rectangle([125, liquid_top_l, 275, 515], fill=(50, 150, 235))
    draw_l.line([125, liquid_top_l, 275, liquid_top_l], fill=(255, 255, 255), width=3)
    path_l = os.path.join(INFUSION_DIR, "infusion_low_10.png")
    img_low.save(path_l)
    dataset_manifest.append({
        "id": "infusion_low_10",
        "file": path_l,
        "type": "infusion_bottle",
        "liquidPercent": 10,
        "expectedResult": "INFUSION_LOW_LEVEL",
        "description": "输液瓶余量约为 10%，触发低液位报警"
    })

gen_single_clinic_hit()
gen_multi_clinic_isolation()
gen_advance_warning()
gen_explicit_overcall()
gen_blurry_noisy()
gen_infusion_bottles()

manifest_path = os.path.join(OUTPUT_DIR, "benchmark_manifest.json")
with open(manifest_path, "w", encoding="utf-8") as f:
    json.dump(dataset_manifest, f, ensure_ascii=False, indent=2)

print(f"Generated {len(dataset_manifest)} benchmark samples successfully into {OUTPUT_DIR}")
