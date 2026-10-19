# Hướng dẫn cho người mới — Challenge 06: Policy Benefits Calculator

Tài liệu này giúp bạn **chạy**, **test** và **hiểu cách tính quyền lợi** của project, kể cả khi chưa từng đọc code.

---

## 1. Project làm gì?

Cho một **hợp đồng bảo hiểm** (`data/policy.json`) và **danh sách chi phí y tế** (`data/expenses.json`), module tính cho từng chi phí:

- **covered_amount**: số tiền bảo hiểm trả.
- **copay_amount** và **member_pays**: phần đồng chi trả, và tổng số thành viên phải tự trả.
- **decision**: `COVERED`, `PARTIALLY_COVERED` hoặc `DENIED`.
- **reason**: lý do bằng câu dễ đọc. **reason_codes**: mã quy tắc cho máy đọc.
- **remaining_annual_limit**, **remaining_visit_limit**: hạn mức năm và số lượt khám còn lại sau chi phí đó.

Cuối cùng module trả về **số dư còn lại của từng quyền lợi**.

| File | Vai trò |
|---|---|
| `data/policy.json` | Hợp đồng mẫu: 4 quyền lợi, 3 loại trừ |
| `data/expenses.json` | 20 chi phí, phủ đủ mọi quy tắc |
| `data/expected_results.json` | Kết quả mong đợi **tính tay**, độc lập với code |
| `output/coverage_results.json` | Kết quả thực tế: từng chi phí và summary |
| `output/coverage_report.md` | Báo cáo dễ đọc |
| `test-report/benefits_test_report.xlsx` | Kết quả test: Excel kèm ảnh evidence |

---

## 2. Yêu cầu môi trường

Chỉ cần **JDK 21** trở lên. Không dùng Maven/Gradle, không thư viện ngoài, kể cả JSON: project tự viết bộ đọc/ghi JSON nhỏ.

```bash
java -version
```

---

## 3. Cách chạy

Mở terminal tại thư mục `challenge_06`.

### Windows (PowerShell)

```powershell
.\run.ps1
```

> Nếu bị chặn chạy script: `powershell -ExecutionPolicy Bypass -File .\run.ps1`

### Windows (CMD)

Cách 1: gọi script PowerShell.

```bat
powershell -ExecutionPolicy Bypass -File run.ps1
```

Cách 2: dùng `javac`/`java` trực tiếp.

```bat
mkdir build\classes
dir /s /b src\main\java\*.java > build\sources.txt
javac -encoding UTF-8 -d build\classes @build\sources.txt
java -cp build\classes benefits.Main calculate
```

### macOS / Linux / Git Bash

```bash
./run.sh
```

### Kết quả mong đợi

```
Policy POL-2024-000123 - 20 expenses, processed in chronological order:
   1. EXP-002  2024-02-10 DENTAL/Filling                       2000 ->          0  DENIED
   2. EXP-001  2024-03-15 OUTPATIENT/Doctor Visit              2500 ->       2000  PARTIALLY_COVERED
   ...
  20. EXP-020  2024-12-20 OUTPATIENT/Doctor Visit              1000 ->          0  DENIED
Covered 100000 of 136400 THB submitted | policy annual limit left: 0
[calculate] results -> output/coverage_results.json
[calculate] report  -> output/coverage_report.md
```

### Tham số

| Tham số | Mặc định | Ví dụ |
|---|---|---|
| `--policy` | `data/policy.json` | `./run.sh calculate --policy my_policy.json` |
| `--expenses` | `data/expenses.json` | `./run.sh calculate --expenses my_claims.json` |
| `--out-dir` | `output` | `./run.sh calculate --out-dir result` |

Nếu file đầu vào sai, chương trình báo **đúng trường bị lỗi** và thoát với mã 2. Ví dụ: `Invalid input: policy.benefits[0].copay.value: must be between 0 and 100 for a PERCENTAGE copay`.

---

## 4. Cách test

```powershell
.\test.ps1
```

```bash
./test.sh
```

CMD (cách 2):

```bat
mkdir build\test-classes
dir /s /b src\main\java\*.java src\test\java\*.java > build\test-sources.txt
javac -encoding UTF-8 -d build\test-classes @build\test-sources.txt
java -cp build\test-classes benefits.TestRunner
```

Kết quả mong đợi: `Tests: 26 passed, 0 failed, 0 errors`, rồi tự sinh `test-report/benefits_test_report.xlsx` và `test-report/evidence/*.png`.

| Nhóm | ID | Kiểm tra gì |
|---|---|---|
| `CalculatorTest` | CALC-01..16 | Logic lõi trên policy nhỏ: chi trả đủ, copay % và cố định, deductible qua nhiều chi phí, trần mỗi lần khám, **biên thời gian chờ** (ngày trước bị từ chối, ngày đầu được trả), loại trừ, hết lượt khám, **nhiều chi phí tiêu cùng một hạn mức**, hạn mức tổng chặn chéo các quyền lợi, hạn mức năm của sub-benefit, **xử lý theo thời gian bất kể thứ tự đầu vào**, quyền lợi không tồn tại / ngoài thời hạn, chi phí bị từ chối không tiêu hạn mức, gọi lại nhiều lần không rò rỉ trạng thái, làm tròn |
| `ScenarioTest` | E2E-01..04 | 20 chi phí mẫu **khớp 20/20** với `expected_results.json`, số dư cuối khớp, đủ mọi loại tình huống đề yêu cầu, EXP-001 **giống hệt ví dụ trong đề** |
| `JsonTest` | JSON-01..03 | Bộ đọc/ghi JSON: escape, số thập phân chính xác, báo lỗi có dòng/cột |
| `ValidationTest` | VAL-01..03 | Đọc policy mẫu; policy hoặc chi phí sai bị từ chối với đường dẫn trường cụ thể |

### Báo cáo test (Excel + ảnh evidence)

| Sheet | Nội dung |
|---|---|
| **Summary** | Thời điểm chạy, môi trường, tổng Passed / Failed / Errors, tỷ lệ pass, thống kê theo class, ảnh console |
| **Testcase** | Mỗi test một dòng: ID, Title, Test Steps / Input, Expected Result, **Actual Result**, **Status**, link tới evidence |
| **Evidence** | Ảnh kiểu terminal cho từng test: đầu vào, kết quả từng chi phí, lý do, `RESULT: PASS/FAIL` |

> Đóng file `.xlsx` trong Excel trước khi chạy test lại, nếu không Windows sẽ khóa file.

---

## 5. Cách tính: thứ tự áp dụng quy tắc

```
Danh sách chi phí ──► sắp xếp theo (date, expense_id)
                         │
           ┌─────────────▼─────────────┐   với từng chi phí, dùng chung 1 "sổ cái" (BenefitLedger)
           │ ① Điều kiện → DENIED nếu: │   ghi lại: đã trả theo policy / benefit / sub-benefit,
           │   ngoài thời hạn HĐ       │   deductible đã đóng, số lượt khám đã dùng
           │   quyền lợi không có      │
           │   bị loại trừ (keyword)   │
           │   trong thời gian chờ     │
           │   hết lượt khám           │
           │   hạn mức đã về 0         │
           ├───────────────────────────┤
           │ ② Deductible năm          │  thành viên trả phần deductible chưa đóng trước
           │ ③ Copay (% hoặc cố định)  │  tính trên phần còn lại sau deductible
           │ ④ Áp trần nhỏ nhất trong: │  per-visit, sub-benefit năm, benefit năm, policy năm
           │ ⑤ Ghi sổ + quyết định     │  COVERED / PARTIALLY_COVERED / DENIED + reason
           └───────────────────────────┘
                         │
                         ▼
     Kết quả từng chi phí + số dư từng quyền lợi (summary)
```

Ví dụ EXP-003: Doctor Visit 4,500 THB, copay 20%, trần 3,000/lần.
- Copay 20% = 900, nên phần được xét trả là 3,600.
- Áp trần 3,000/lần, nên **bảo hiểm trả 3,000**.
- Thành viên trả 1,500 = copay 900 + 600 vượt trần.

Ví dụ hạn mức chồng nhau, EXP-018: thai sản 25,000.
- Hạn mức MATERNITY còn 28,500, trần mỗi lần là 25,000.
- Nhưng **hạn mức tổng của hợp đồng chỉ còn 18,220**, nên chỉ trả 18,220.

### Quy ước thiết kế (giả định)

- **Copay tính trước, trần áp sau.** Hạn mức là số tiền tối đa bảo hiểm trả, nên copay được tính trên chi phí trước khi áp trần.
- **Deductible tính theo năm, theo từng quyền lợi.** Nó được trừ trước copay.
- **Thời gian chờ:** được bảo hiểm từ ngày `effective_date + waiting_period_days`. Ví dụ hợp đồng hiệu lực 2024-01-01, chờ 90 ngày, thì được bảo hiểm từ 2024-03-31.
- **Lượt khám chỉ bị tính khi có tiền được trả.** Chi phí bị từ chối không tiêu hạn mức, deductible hay lượt khám.
- **`DENIED` nghĩa là không trả đồng nào.** Nguyên nhân có thể là bị từ chối, hoặc toàn bộ số tiền rơi vào deductible, như EXP-006. Phần `reason` và `reason_codes` nói rõ nguyên nhân.
- **Loại trừ:** so khớp không phân biệt hoa thường; chẩn đoán chứa keyword là bị loại trừ.
- **Tiền:** dùng `BigDecimal`, 2 chữ số thập phân, làm tròn HALF_UP.
- **Đẳng thức luôn đúng:** `submitted = covered + member_pays` và `member_pays = deductible + copay + not_covered`.

---

## 6. 20 chi phí mẫu (thứ tự xử lý)

| # | Expense | Ngày | Quyền lợi | Số tiền | Trả | Kết quả |
|---|---|---|---|---|---|---|
| 1 | EXP-002 | 02-10 | DENTAL / Filling | 2,000 | 0 | Từ chối: chờ 90 ngày |
| 2 | EXP-001 | 03-15 | OPD / Doctor Visit | 2,500 | 2,000 | Copay 20% (ví dụ của đề) |
| 3 | EXP-003 | 03-22 | OPD / Doctor Visit | 4,500 | 3,000 | Copay + trần 3,000/lần |
| 4 | EXP-004 | 04-10 | DENTAL / Cleaning | 1,200 | 1,000 | Copay cố định 200 |
| 5 | EXP-005 | 04-18 | DENTAL / Cleaning | 3,000 | 0 | Từ chối: loại trừ thẩm mỹ |
| 6 | EXP-006 | 05-05 | IPD / Room & Board | 1,500 | 0 | Toàn bộ vào deductible (còn 500) |
| 7 | EXP-007 | 05-06 | IPD / Room & Board | 4,000 | 3,500 | Đóng nốt deductible 500 |
| 8 | EXP-008 | 06-12 | OPD / Physiotherapy | 800 | 640 | Copay; lượt 1/2 |
| 9 | EXP-009 | 06-19 | OPD / Physiotherapy | 800 | 640 | Copay; lượt 2/2 |
| 10 | EXP-010 | 06-26 | OPD / Physiotherapy | 800 | 0 | Từ chối: hết lượt khám |
| 11 | EXP-011 | 07-15 | DENTAL / Filling | 3,500 | 2,500 | Copay + trần 2,500/lần |
| 12 | EXP-012 | 08-01 | DENTAL / Filling | 1,800 | 500 | Hạn mức DENTAL chỉ còn 500 |
| 13 | EXP-013 | 08-20 | DENTAL / Cleaning | 1,000 | 0 | Từ chối: DENTAL hết hạn mức |
| 14 | EXP-014 | 09-10 | IPD / Surgery | 45,000 | 40,000 | Trần 40,000/lần |
| 15 | EXP-015 | 09-11 | IPD / Room & Board | 5,000 | 5,000 | Trả đủ |
| 16 | EXP-016 | 10-01 | MATERNITY / Prenatal | 1,500 | 0 | Từ chối: chờ 280 ngày (đến 10-07) |
| 17 | EXP-017 | 10-20 | MATERNITY / Prenatal | 1,500 | 1,500 | Trả đủ |
| 18 | EXP-019 | 11-05 | IPD / Surgery | 30,000 | 21,500 | INPATIENT chỉ còn 21,500 |
| 19 | EXP-018 | 12-02 | MATERNITY / Delivery | 25,000 | 18,220 | **Hạn mức tổng** chỉ còn 18,220 |
| 20 | EXP-020 | 12-20 | OPD / Doctor Visit | 1,000 | 0 | Từ chối: hạn mức tổng đã hết |

Tổng cộng: 136,400 THB được nộp, **100,000 THB được chi trả**, hạn mức năm dùng hết.

> EXP-018 và EXP-019 được cố ý đặt ngược thứ tự ID so với ngày. Vì xử lý theo thời gian, EXP-019 (tháng 11) tiêu hạn mức trước EXP-018 (tháng 12).

---

## 7. Dùng như một module (tái sử dụng)

```java
Policy policy = PolicyReader.read(Path.of("policy.json"));
List<Expense> expenses = ExpenseReader.read(Path.of("expenses.json"));
CalculationResult result = BenefitsCalculator.calculate(policy, expenses);
```

`BenefitsCalculator` không giữ trạng thái giữa các lần gọi; mỗi lần `calculate()` dùng sổ cái mới (test CALC-15).

---

## 8. Cấu trúc thư mục

```
challenge_06/
├── src/main/java/benefits/
│   ├── Main.java                 # CLI: calculate
│   ├── json/                     # Json (đọc/ghi), JsonObject (truy cập có kiểu + báo lỗi theo đường dẫn)
│   ├── model/                    # Policy, Benefit, SubBenefit, Copay, Exclusion, Expense, Money
│   ├── engine/                   # BenefitsCalculator (quy tắc), BenefitLedger (sổ cái), ExpenseResult,
│   │                             # BenefitBalance, CalculationResult, Decision, ReasonCode
│   └── io/                       # PolicyReader, ExpenseReader (kiểm tra hợp lệ), ResultJson, CoverageReport
├── src/test/java/benefits/       # TestRunner, Fixtures, 4 class test (26 test) + testkit/ (Excel, ảnh)
├── data/                         # policy.json, expenses.json, expected_results.json
├── output/                       # coverage_results.json, coverage_report.md
├── test-report/                  # benefits_test_report.xlsx + evidence/*.png
├── run.sh / run.ps1
└── test.sh / test.ps1
```

---

## 9. Timeline (ước lượng)

| Hạng mục | Thời gian |
|---|---|
| Phân tích đề, thiết kế schema policy và thứ tự quy tắc | 30 phút |
| JSON reader/writer + model + validation | 40 phút |
| Engine tính toán + câu giải thích | 60 phút |
| 20 chi phí + kết quả mong đợi tính tay | 30 phút |
| Test (26) + báo cáo Excel | 45 phút |
| Tài liệu, review | 20 phút |
| **Tổng** | **≈ 3h45** |
