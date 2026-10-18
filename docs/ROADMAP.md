# 🚀 Roadmap پروژه F1 Race Strategy & Decision Support System

> منتور هر وقت می‌خواهید ببینید کجای کار هستیم، به این فایل برگردید.
> وضعیت هر فاز با ✅ علامت خورده و به کامیت‌های ریپازیتوری لینک است.

**مدت زمان:** حدود ۳ تا ۴ ماه (نسخه رزومه‌ای و قابل دفاع)
**روش کار:** Pair Programming + Code Review

**قانون پروژه:**

* ❌ یک نفر فقط Java و یک نفر فقط Python نباشد.
* ✅ هر دو تمام بخش‌ها را یاد بگیرند.
* Driver و Navigator مرتب عوض شود.

---

## فاز ۱ — طراحی پروژه (۳ تا ۵ روز) — ✅ انجام شد

- [x] ساخت Repository در GitHub
- [x] ساخت پوشه‌ها (`backend/`, `python-engine/`, `javafx/`, `database/`, `docs/`, `tests/`)
- [x] طراحی معماری → [ARCHITECTURE.md](ARCHITECTURE.md)
- [x] طراحی Database → [schema.sql](../database/schema.sql) و [DATABASE.md](DATABASE.md)
- [x] Task Board → همین فایل + GitHub Issues

## فاز ۲ — شناخت داده‌ها (۷ روز) — ✅ انجام شد (FastF1 + حالت آفلاین)

- [x] نصب FastF1
- [x] مسیر دانلود یک مسابقه واقعی (`python -m app.ingest --year 2024 --round 8`)
- [x] بررسی Lapها، Telemetry، Weather، Pit Stops (کد ingest)
- [x] حالت آفلاین `--demo` برای شبکه‌های بلاک‌شده

## فاز ۳ — Database (۷ تا ۱۰ روز) — ✅ انجام شد

- [x] SQLite (`race.db`) با ۸ جدول طبق Roadmap
- [x] Python اطلاعات مسابقه را داخل SQLite ذخیره می‌کند
- [ ] مهاجرت به PostgreSQL (بعداً، وقتی پروژه بزرگ شد)

## فاز ۴ — Spring Boot Backend (۱۰ روز) — ✅ انجام شد

- [x] اتصال به Database (Spring Data JPA + SQLite)
- [x] REST API: `GET /api/drivers`, `GET /api/races`, `POST /api/simulate` و بقیه
- [x] ارسال درخواست به Python و برگرداندن نتیجه به UI

## فاز ۵ — ارتباط Java و Python (۵ تا ۷ روز) — ✅ انجام شد

- [x] Python به صورت سرویس FastAPI (پورت 8000)
- [x] Java با HTTP و JSON با Python صحبت می‌کند (`POST /simulate`, `POST /predict`, ...)
- [x] قرارداد دقیق → [API_CONTRACT.md](API_CONTRACT.md)

## فاز ۶ — Data Analysis (۱۰ روز) — ✅ انجام شد

- [x] Lap Time Analysis
- [x] Pace Trend
- [x] Fuel Estimation
- [x] Tyre Age Analysis
- [x] Gap Analysis
- ابزار: Pandas و NumPy

## فاز ۷ — Race Simulator (۲ تا ۳ هفته) — ✅ انجام شد

- [x] Tire Degradation
- [x] Fuel Consumption
- [x] Pit Stop
- [x] Traffic
- [x] Weather
- خروجی: `Pit Lap 20 → Finish P4 → Race Time`

## فاز ۸ — Machine Learning (۲ هفته) — ✅ انجام شد

- [x] Linear Regression
- [x] Random Forest
- [x] Gradient Boosting
- هدف: پیش‌بینی Lap Time و Tyre Degradation و Driver Pace
- (XGBoost/LightGBM بعداً اگر لازم شد)

## فاز ۹ — Strategy Recommendation Engine (۱ هفته) — ✅ انجام شد

- [x] ورودی: Current Race State
- [x] امتحان چند سناریو (Pit Now / +2 / +5 / ...) با چند Compound
- [x] شبیه‌سازی همه و پیشنهاد بهترین با Confidence

## فاز ۱۰ — JavaFX (۱ هفته) — ✅ انجام شد

- [x] Race Selection
- [x] Driver Selection
- [x] Import Data (Ingest)
- [x] Analyze
- [x] Simulate
- [x] Compare Strategies
- (صفحه Reports در Simulate/Strategy خلاصه شده)

## فاز ۱۱ — Documentation (۱ هفته) — ✅ انجام شد

- [x] README حرفه‌ای
- [x] Architecture Diagram
- [x] Database Diagram
- [x] API Documentation
- [x] Unit Tests (pytest + JUnit)
- [ ] UML کامل و Screenshots (بعد از اجرای UI)

---

## نسخه‌های پروژه (Releases)

- **v0.1** — دانلود داده از FastF1، ذخیره در SQLite ✅
- **v0.2** — تحلیل Lap Time، گزارش اولیه ✅
- **v0.3** — شبیه‌سازی Pit Stop، Tire Degradation ✅
- **v0.4** — Driver Pace Prediction، Machine Learning ✅
- **v0.5** — Strategy Recommendation Engine ✅
- **v1.0** — سیستم کامل با JavaFX، Spring Boot، Python، SQLite/PostgreSQL و مستندات کامل ✅ (نسخه اول)

## مهم‌ترین اصل پروژه

هر بار که می‌خواهید چیزی اضافه کنید، از خودتان یک سؤال بپرسید:

> **«این قابلیت چه تصمیم واقعی را برای تیم مسابقه بهتر می‌کند؟»**

اگر جواب مشخصی داشت (مثل «الان پیت کنیم یا نه؟»، «Undercut بهتر است یا Overcut؟»)، ارزش ساختن دارد. اگر فقط یک نمودار جدید است، احتمالاً نه.
