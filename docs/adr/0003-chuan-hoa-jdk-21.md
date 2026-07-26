# ADR 0003 — Chuẩn hoá toolchain trên JDK 21

- **Trạng thái**: Chấp nhận
- **Ngày**: 26/07/2026

## Bối cảnh

Máy phát triển đang cài JDK 25. Spike tuần 1 phát hiện hai công cụ thiết yếu không chạy được
trên phiên bản này:

| Công cụ | Triệu chứng |
|---|---|
| SpotBugs 4.8.6 và 4.9.3 | `Error scanning java/lang/Object for referenced classes`, phân tích ra 0 cảnh báo |
| palantir-java-format (qua Spotless) | `NoSuchMethodError: Log$DeferredDiagnosticHandler.getDiagnostics()` |

Cả hai đều đọc class file và gọi API nội bộ của javac, nên tụt lại sau mỗi phiên bản JDK mới.

JaCoCo 0.8.12 chạy được nhưng có cảnh báo khi instrument lớp nội bộ của JDK.

## Quyết định

1. **JDK 21 (LTS) là phiên bản chuẩn** cho cả phát triển lẫn CI. `maven.compiler.release` đặt
   21; CI dùng `temurin 21`. Cần cài JDK 21 trên máy phát triển.
2. **Không dùng palantir-java-format hay google-java-format.** Spotless chỉ chạy các bước dựa
   trên JavaParser: sắp xếp import, xoá import thừa, cắt khoảng trắng thừa. Chúng độc lập với
   phiên bản JDK nên không vỡ lại về sau.
3. **Công cụ phân tích tĩnh chạy trong container**, không phụ thuộc JDK của máy chủ. Điều này
   vốn đã nằm trong thiết kế (analyzer chạy trong sandbox) — spike chỉ làm rõ thêm rằng đó cũng
   là biện pháp cách ly phiên bản.

## Lý do

Cảnh báo phân tích tĩnh là bằng chứng gốc của toàn hệ thống. Một analyzer im lặng trả về 0 kết
quả nguy hiểm hơn nhiều so với một analyzer báo lỗi: pipeline vẫn "chạy thành công" nhưng Evidence
Store rỗng, và sai sót chỉ lộ ra khi đã chạy thực nghiệm.

## Hệ quả

- Cần cài JDK 21 trước khi làm tiếp tuần 2.
- Adapter phân tích tĩnh phải **báo lỗi rõ ràng khi analyzer trả về 0 cảnh báo trên một
  repository được kỳ vọng có cảnh báo**, thay vì lặng lẽ ghi một `analysis_run` rỗng.
