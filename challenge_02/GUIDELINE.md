# Hướng dẫn cho người mới — Challenge 02: Claims Data Cleanup & Report

Tài liệu này giúp bạn **chạy**, **test** và **hiểu luồng xử lý** của project, kể cả khi chưa từng đọc code.

---

## 1. Project làm gì?

1. **Sinh** file CSV 500 claims bảo hiểm có lỗi dữ liệu cố ý (≈18% số dòng bị lỗi).
2. **Làm sạch** file đó: xóa dòng trùng, chuẩn hóa text/ngày/tiền/tiền tệ, đánh dấu dòng không hợp lệ.
3. **Xuất báo cáo** chất lượng dữ liệu (Markdown).

| File | Vai trò |
|---|---|
| `data/claims_dirty.csv` | Dữ liệu bẩn đầu vào (sinh tự động, seed cố định = 42) |
| `output/claims_clean.csv` | Dữ liệu sạch đầu ra |
| `output/data_quality_report.md` | Báo cáo chất lượng dữ liệu |

---

## 2. Yêu cầu môi trường

- **JDK 21** trở lên (chỉ cần vậy — **không** dùng Maven/Gradle, **không** có thư viện ngoài).
- Kiểm tra:

```bash
java -version
```

```bash
javac -version
```

Nếu chưa có: cài [Eclipse Temurin 21](https://adoptium.net/) và đảm bảo `java`, `javac` nằm trong `PATH`.

---

## 3. Cách chạy

Mở terminal tại thư mục `challenge_02`.

### Windows (PowerShell)

```powershell
.\run.ps1
```

> Nếu PowerShell báo lỗi *running scripts is disabled*, chạy một lần: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`, hoặc dùng `powershell -ExecutionPolicy Bypass -File .\run.ps1`.

### Windows (CMD)

Cách 1: gọi script PowerShell từ CMD.

```bat
powershell -ExecutionPolicy Bypass -File run.ps1
```

Thêm lệnh con hoặc tham số ở cuối, ví dụ `powershell -ExecutionPolicy Bypass -File run.ps1 clean --out-dir result`.

Cách 2: tự biên dịch và chạy bằng `javac`/`java` (không cần PowerShell).

```bat
mkdir build\classes
dir /s /b src\main\java\*.java > build\sources.txt
javac -encoding UTF-8 -d build\classes @build\sources.txt
java -cp build\classes claims.Main all
```

Đổi `all` thành `generate` hoặc `clean`, và thêm tham số như `--rows 1000` nếu cần. Nếu `build\classes` đã tồn tại thì lệnh `mkdir` báo lỗi nhưng không ảnh hưởng, cứ chạy tiếp các lệnh sau.

### macOS / Linux / Git Bash

```bash
./run.sh
```

### Kết quả mong đợi

```
[generate] 500 rows (92 with injected issues, 18.4%) -> data/claims_dirty.csv
[clean] Rows before: 500 | duplicates removed: 12 exact + 8 near | rows after: 480 (valid 427, flagged 53)
[clean] clean CSV -> output/claims_clean.csv
[clean] report    -> output/data_quality_report.md
```

### Các lệnh con & tham số

| Lệnh | Ý nghĩa |
|---|---|
| `./run.sh` hoặc `./run.sh all` | Sinh dữ liệu + làm sạch + báo cáo (mặc định) |
| `./run.sh generate` | Chỉ sinh `data/claims_dirty.csv` |
| `./run.sh clean` | Chỉ làm sạch file CSV có sẵn |

| Tham số | Mặc định | Ví dụ |
|---|---|---|
| `--rows` | `500` | `./run.sh generate --rows 1000` |
| `--seed` | `42` | `./run.sh all --seed 7` (seed khác → dữ liệu khác, cùng seed → luôn giống hệt) |
| `--dirty` | `data/claims_dirty.csv` | `./run.sh clean --dirty my_claims.csv` (làm sạch file CSV của bạn) |
| `--out-dir` | `output` | `./run.sh clean --out-dir result` |

> File CSV tự đưa vào chỉ cần có đủ 9 cột theo **tên** header (thứ tự cột không quan trọng).

---

## 4. Cách test

```powershell
.\test.ps1
```

```bash
./test.sh
```

CMD:

Cách 1: gọi script PowerShell.

```bat
powershell -ExecutionPolicy Bypass -File test.ps1
```

Cách 2: dùng `javac`/`java` trực tiếp.

```bat
mkdir build\test-classes
dir /s /b src\main\java\*.java src\test\java\*.java > build\test-sources.txt
javac -encoding UTF-8 -d build\test-classes @build\test-sources.txt
java -cp build\test-classes claims.TestRunner
```

Kết quả mong đợi:

```
Tests: 22 passed, 0 failed, 0 errors
[report] Excel    -> test-report/claims_test_report.xlsx
[report] evidence -> test-report/evidence
```

Exit code là 0 khi mọi test pass và báo cáo được ghi thành công. Ngược lại, exit code là 1.

Test dùng một **test runner tự viết** (`src/test/java/claims/TestRunner.java`) để không cần JUnit. Nó tự chạy mọi method `static void testXxx()`, sau đó **tự sinh file Excel test case kèm ảnh evidence**.

| Test class | ID | Kiểm tra gì |
|---|---|---|
| `CsvIOTest` | CSV-01..03 | Đọc/ghi CSV: dấu phẩy trong ngoặc kép (`"15,000"`), dấu `"` escape, xuống dòng trong ô |
| `NormalizersTest` | NORM-01..07 | Từng hàm chuẩn hóa: tên, claim_type (typo `Outpateint`, viết tắt `OP`), diagnosis (`N/A`), số tiền (âm, 0, `15,000`, `1,5`), tiền tệ (`Baht`), 3 định dạng ngày, ngày không tồn tại (`31/02/2024`), status |
| `ClaimsCleanerTest` | CLN-01..07 | Xóa trùng tuyệt đối, xóa trùng sau chuẩn hóa, claim_id xung đột, **1 dòng có nhiều lỗi cùng lúc**, cột đảo thứ tự, thiếu cột |
| `EndToEndTest` | E2E-01..05 | Sinh 500 dòng → làm sạch → đối chiếu: **mọi lỗi đã cấy đều bị phát hiện**, dòng sạch **không** bị báo lỗi sai, tỷ lệ lỗi trong 15–20%, số dòng trùng khớp, output định dạng nhất quán |

### Báo cáo test (Excel + ảnh evidence)

Mỗi lần chạy test sẽ ghi đè thư mục `test-report/`:

```
test-report/
├── claims_test_report.xlsx    # file Excel 3 sheet
└── evidence/
    ├── 00_console.png         # ảnh chụp toàn bộ output console
    ├── CSV-01.png             # 1 ảnh evidence cho mỗi test case
    └── ...
```

| Sheet | Nội dung |
|---|---|
| **Summary** | Thời điểm chạy, lệnh chạy, môi trường (Java/OS), tổng số test, Passed / Failed / Errors, tỷ lệ pass, kết quả tổng (PASS/FAIL), bảng thống kê theo từng test class, ảnh console |
| **Testcase** | Mỗi dòng là 1 test case: No, Test ID, Test Class, Test Method, Title, Test Steps / Input, Expected Result, **Actual Result**, **Status** (tô màu), Duration, Executed At. Cột Evidence là link nhảy tới ảnh tương ứng |
| **Evidence** | Với mỗi test: dòng tiêu đề (ID, tên test, Status, link "Back to Testcase") và ảnh chụp kiểu terminal. Ảnh gồm input → output → issues thực tế, số assertion, `RESULT: PASS/FAIL` |

Ý nghĩa Status:
- **PASS** (xanh): mọi assertion đúng.
- **FAIL** (đỏ): có assertion sai, tức kết quả khác kỳ vọng. Thông báo lỗi nằm ở cột Actual Result và trong ảnh.
- **ERROR** (vàng): test bị exception bất ngờ khi chạy.

**Cách đối chiếu:** ở sheet Testcase, so cột *Expected Result* với *Actual Result*, rồi bấm *View &lt;ID&gt;* để xem ảnh evidence. Trong ảnh có từng giá trị input/output thực tế.

> - Đóng file `.xlsx` trong Excel trước khi chạy test lại. Nếu file đang mở, Windows khóa file và runner sẽ báo `[report] FAILED to write the test report ...`.
> - Muốn ghi báo cáo ra thư mục khác thì truyền đường dẫn làm tham số, ví dụ `java -cp build/test-classes claims.TestRunner my-report`.
> - File Excel được tự ghi theo chuẩn Office Open XML, ảnh được vẽ bằng Java2D. Vì vậy **không cần** Apache POI hay thư viện ngoài nào. File đã được kiểm tra mở bằng Microsoft Excel 16 mà không có cảnh báo "repair".

**Thêm test mới:**
1. Tạo method `static void testTenMoTa()` trong một class test có sẵn.
2. Gắn `@TestCase(id = "...", title = "...", steps = "...", expected = "...")` để thông tin lên file Excel.
3. Gọi `log("...")` để ghi dòng evidence vào ảnh.
4. Nếu tạo class mới, thêm class đó vào mảng `TEST_CLASSES` trong `TestRunner`.

---

## 5. Luồng thực thi

```
                ┌──────────────────────┐
  run.sh/ps1 ─► │ Main (generate|clean)│
                └─────────┬────────────┘
        generate          │                 clean
   ┌──────────────────────┴──────────────────────────────┐
   ▼                                                     ▼
ClaimsGenerator(seed)                           CsvIO.read(dirty.csv)
 1. tạo 480 dòng sạch                                    │
 2. cấy 1–3 lỗi vào 15% dòng                             ▼
 3. thêm 8 near-duplicate                       ClaimsCleaner.clean()
 4. thêm 12 exact-duplicate                      ① map cột theo tên header
   │                                             ② bỏ dòng trùng tuyệt đối (raw giống hệt)
   ▼                                             ③ CleanClaim: chuẩn hóa 9 cột qua Normalizers
CsvIO.write(data/claims_dirty.csv)                  → ghi nhận Issue cho từng cột
                                                 ④ bỏ near-duplicate (giống hệt SAU chuẩn hóa)
                                                 ⑤ đánh dấu claim_id bị dùng bởi ≥2 dòng khác nhau
                                                         │
                                       ┌─────────────────┴─────────────────┐
                                       ▼                                   ▼
                          CsvIO.write(claims_clean.csv)     ClaimsReport.toMarkdown()
                                                             → data_quality_report.md
```

### Chi tiết bước ③ — chuẩn hóa từng cột (`Normalizers`)

| Cột | Đầu vào ví dụ | Đầu ra | Issue ghi nhận |
|---|---|---|---|
| `claim_id`, `policy_id` | `clm-00123 `, `` | `CLM-00123`, `NULL` | `MISSING_*` |
| `member_name` | `JOHN DOE`, `  jOHN  doe` | `John Doe` | `NAME_FORMAT` |
| `claim_type` | `outpatient` / `Outpateint`, `OP` / `XRAY` | `OUTPATIENT` / `NULL` | `CLAIM_TYPE_FORMAT` / `CLAIM_TYPE_TYPO` / `INVALID_CLAIM_TYPE` |
| `diagnosis` | `N/A`, `n/a`, `` | `NULL` | `MISSING_DIAGNOSIS` |
| `submitted_amount` | `15,000` / `-500` / `0` | `15000.00` / `-500.00` / `0.00` | `AMOUNT_FORMAT` / `NEGATIVE_AMOUNT` / `ZERO_AMOUNT` |
| `currency` | `thb` / `Baht` / `USD` | `THB` / `THB` / `NULL` | `CURRENCY_FORMAT` / `CURRENCY_ALIAS` / `INVALID_CURRENCY` |
| `submitted_date` | `15/03/2024`, `March 15, 2024` | `2024-03-15` | `DATE_FORMAT` (hoặc `INVALID_DATE`) |
| `status` | `In Review` / `APROVED` | `IN_REVIEW` / `APPROVED` | `STATUS_FORMAT` / `STATUS_TYPO` |

`claim_type`, `status`, `currency` được so với **từ điển cố định** (`Vocabulary`) theo thứ tự:
khớp đúng → bí danh (`OP`, `Baht`) → cùng chữ cái nếu bỏ hoa/thường/dấu cách/gạch nối → sửa typo (khoảng cách chỉnh sửa ≤ 2, chỉ khi có **duy nhất** 1 ứng viên) → không nhận ra thì `NULL` + flag.

### Hai mức độ lỗi

- **Fixed** (sửa được): định dạng, typo, alias. Dòng vẫn `is_valid=true`.
- **Flagged** (không tin được): tiền âm/0, thiếu ID, claim_id xung đột, ngày/giá trị không nhận ra. Dòng được **giữ lại** với `is_valid=false` để còn kiểm tra lại, không bị xóa âm thầm.

Cột `dq_issues` liệt kê **mọi** lỗi của dòng (ngăn bởi `|`). Nhờ vậy dòng có nhiều lỗi cùng lúc vẫn thấy đủ.

---

## 6. Đọc file output

`output/claims_clean.csv` có thêm 3 cột so với input:

| Cột | Ý nghĩa |
|---|---|
| `source_row` | Số dòng trong file gốc (header = dòng 1) → truy vết ngược |
| `is_valid` | `true`/`false` |
| `dq_issues` | Danh sách lỗi, ví dụ `MISSING_POLICY_ID\|NEGATIVE_AMOUNT` |

Giá trị thiếu luôn là `NULL`. Ngày luôn ở dạng `YYYY-MM-DD`. Tiền có 2 chữ số thập phân, không có dấu phẩy.

Lọc nhanh các dòng lỗi (Git Bash):

```bash
grep ",false," output/claims_clean.csv
```

---

## 7. Cấu trúc thư mục

```
challenge_02/
├── src/main/java/claims/
│   ├── Main.java              # CLI: generate | clean | all
│   ├── ClaimsGenerator.java   # sinh dữ liệu bẩn có seed
│   ├── CsvIO.java             # đọc/ghi CSV (RFC 4180)
│   ├── ClaimsCleaner.java     # pipeline làm sạch 5 bước
│   ├── CleanClaim.java        # 1 dòng đã chuẩn hóa + danh sách Issue
│   ├── Normalizers.java       # hàm chuẩn hóa từng cột
│   ├── Vocabulary.java        # khớp từ điển + sửa typo
│   ├── Issue.java             # danh mục lỗi (Fixed / Flagged)
│   └── ClaimsReport.java      # báo cáo Markdown
├── src/test/java/claims/
│   ├── TestRunner.java        # chạy test + assert + log evidence, gọi xuất báo cáo
│   ├── TestCase.java          # annotation: id, title, steps, expected
│   ├── *Test.java             # 4 class test (22 test case)
│   └── testkit/
│       ├── TestResult.java          # kết quả 1 test (PASS / FAIL / ERROR)
│       ├── EvidenceImage.java       # vẽ ảnh evidence kiểu terminal (Java2D)
│       ├── XlsxWriter.java          # ghi .xlsx không cần thư viện (zip + XML)
│       └── TestReportExporter.java  # dựng 3 sheet Summary / Testcase / Evidence
├── data/claims_dirty.csv
├── output/claims_clean.csv
├── output/data_quality_report.md
├── test-report/               # sinh ra khi chạy test: Excel + evidence/*.png
├── run.sh / run.ps1           # build + chạy
└── test.sh / test.ps1         # build + test
```

---

## 8. Quyết định thiết kế & giả định

- **Flag thay vì xóa** các dòng có số tiền không hợp lệ: không mất dữ liệu, có thể kiểm tra lại. Các dòng này bị loại khỏi phép tính trung bình.
- **Không trộn tiền tệ**: trung bình số tiền được tính theo từng cặp (loại claim, tiền tệ). Cộng THB với VND sẽ sai, và đề không cho tỷ giá.
- **Ngày dạng `xx/xx/yyyy` hiểu là ngày/tháng/năm** (theo ví dụ `15/03/2024` trong đề).
- **`1,5` bị coi là không hợp lệ**: dấu phẩy chỉ được chấp nhận làm dấu phân cách hàng nghìn, không đoán dấu thập phân.
- **Near-duplicate**: 2 dòng khác nhau ở dạng thô nhưng giống hệt sau chuẩn hóa thì giữ dòng đầu tiên.
- **Seed cố định (42)** → chạy lại luôn ra cùng dữ liệu, kết quả có thể tái lập.
- **Tên dạng `McDonald`** sẽ thành `Mcdonald` (giới hạn đã biết của Title Case đơn giản).

## 9. Timeline (ước lượng)

| Hạng mục | Thời gian |
|---|---|
| Phân tích đề, thiết kế danh mục lỗi | 20 phút |
| Bộ sinh dữ liệu bẩn | 30 phút |
| Chuẩn hóa + pipeline làm sạch | 50 phút |
| Báo cáo | 20 phút |
| Test (unit + end-to-end) | 30 phút |
| Tài liệu, review | 20 phút |
| **Tổng** | **≈ 2h50** |
