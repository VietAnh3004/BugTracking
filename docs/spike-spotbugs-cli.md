# Spike: SpotBugs CLI độc lập thay cho `mvn spotbugs:spotbugs`

**Ngày**: 05/08/2026 · **Giai đoạn**: tuần 8-9, mở đầu giai đoạn D (Docker sandbox)
**Câu hỏi**: chạy SpotBugs bằng CLI độc lập trên `target/classes` có cho ra **cùng kết quả** với
`mvn spotbugs:spotbugs` không?

Kế hoạch xếp spike này ở ngày đầu của giai đoạn D vì nó là **rủi ro cao nhất** của cả giai đoạn:
nếu trượt, kiến trúc sandbox phải quay về gọi Maven plugin và mất tính tổng quát.

## Vì sao câu hỏi này quan trọng

`case-study/run-analysis.sh` chạy được là vì `pom.xml` của vulnshop **khai sẵn** plugin
`spotbugs-maven-plugin` kèm `find-sec-bugs`, `effort=Max`, `threshold=Low`.

Repo người dùng upload nói chung **không khai gì cả**. Khi đó `mvn spotbugs:spotbugs` vẫn chạy,
vẫn "thành công", nhưng với effort mặc định, threshold mặc định và **không có find-sec-bugs** —
tức mất toàn bộ SQL injection, weak crypto, path traversal. Đây chính xác là lớp hỏng-im-lặng mà
cả đồ án chống. Không chữa được bằng cờ `-D`: `<plugins><plugin>` là tham số kiểu phức hợp, dòng
lệnh không truyền được.

## Cách chạy

```bash
# Classpath của chính SpotBugs (không dùng jar trần trong .m2 — nó cần asm, dom4j, commons-*)
mvn -f spotbugs-cli-pom.xml dependency:build-classpath -Dmdep.outputFile=sb-cp.txt

# Classpath phụ trợ = phụ thuộc biên dịch của DỰ ÁN ĐƯỢC QUÉT
cd case-study/vulnshop
mvn dependency:build-classpath -Dmdep.outputFile=vulnshop-cp.txt

java -cp "$(cat sb-cp.txt)" edu.umd.cs.findbugs.LaunchAppropriateUI -textui \
  -effort:max -low -xml:withMessages -output spotbugs-cli.xml \
  -pluginList findsecbugs-plugin-1.13.0.jar \
  -auxclasspath "$(cat vulnshop-cp.txt)" \
  target/classes
```

`-low` là dạng CLI của `threshold=Low`; `-effort:max` khớp `effort=Max`.

## Kết quả — trùng khít

| Chỉ số | CLI độc lập | `mvn spotbugs:spotbugs` |
|---|---|---|
| Số `BugInstance` | **28** | **28** |
| Bộ `(type, vị trí)` | **giống hệt** — so từng phần tử | **giống hệt** |
| `BugCollection/@version` | 4.9.3 | 4.9.3 |
| `FindBugsSummary/@java_version` | 21.0.12 | 21.0.12 |
| `@total_classes` | 20 | 20 |
| `Plugin/@id` đã nạp | `com.h3xstream.findsecbugs` | `com.h3xstream.findsecbugs` |
| `Errors/@errors` | 0 | 0 |
| `Errors/@missingClasses` | 6 | 6 |

**Kết luận: đi tiếp với SpotBugs CLI độc lập.** Không cần phương án lùi.

## Phát hiện phụ — và nó quan trọng hơn kết quả chính

Chạy lại **bỏ `-auxclasspath`**, giữ nguyên mọi thứ khác:

| | có `-auxclasspath` | KHÔNG có |
|---|---|---|
| `BugInstance` | 28 | **25** |
| `missingClasses` | 6 | **13** |
| `total_classes` | 20 | 20 |
| Exit code | 0 | **0** |

Ba cảnh báo biến mất: `EI_EXPOSE_REP2` ×2, `RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE` ×1.

**Không có gì báo lỗi.** Exit 0, XML hợp lệ, `total_classes` vẫn đúng 20 — tức tầng kiểm "analyzer
có đọc được class của người dùng không" **vẫn xanh**. Chỉ có `missingClasses` nhích lên, và
`missingClasses` thì không đặt ngưỡng tuyệt đối được (6 đã là sàn của chính dự án này — mọi
ngưỡng cứng đều là báo động giả cho repo khác).

### Hệ quả bắt buộc cho thiết kế sandbox

1. **Dựng auxclasspath là bước BẮT BUỘC, không phải tối ưu.** Sandbox phải chạy
   `mvn dependency:build-classpath` trên repo người dùng trước khi gọi SpotBugs.
2. **Bước đó hỏng ⇒ run `FAILED`, không được chạy tiếp.** Một lần quét thiếu auxclasspath cho ra
   kết quả "trông đầy đủ nhưng thiếu ~10%", và không chỉ số nào trong artifact phát hiện được.
   Đây là lý do tầng kiểm số 1 (exit code + artifact tồn tại) phải bao gồm cả các lệnh **chuẩn
   bị**, không chỉ lệnh analyzer.
3. **`missingClasses` vẫn KHÔNG được đặt ngưỡng** — đúng như kế hoạch đã chốt. Nó là tín hiệu
   tương đối, chỉ có nghĩa khi so hai lần chạy của cùng một repo, không có nghĩa như một hằng số.
4. Canary (tầng 3) **không cứu được ca này**: mã canary nằm trong image, không phụ thuộc
   auxclasspath của người dùng, nên nó vẫn cho đủ finding. Đây là bằng chứng cụ thể cho việc bốn
   tầng kiểm không thay thế được nhau.

## Ghi chú tái lập

- JDK 21 bắt buộc (ADR 0003). Đo trên `jdk-21.0.12`.
- `target/classes` phải được biên dịch **trước** — SpotBugs đọc bytecode, không đọc `.java`.
- Trong image, SpotBugs sẽ cài từ bản phân phối chính thức (có sẵn `lib/`), không dựng classpath
  bằng Maven như spike này — spike làm vậy chỉ để trả lời câu hỏi nhanh trên máy phát triển.
