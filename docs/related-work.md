# Khảo sát tài liệu — Chương 2 báo cáo

Khung để đọc và ghi chép, bám theo mục lục chương 2 trong đề cương.

**Cách dùng**: mỗi tài liệu đọc xong thì điền vào một khối theo template ở mục 7. Cột quan trọng
nhất là **"Khoảng trống mà ĐATN lấp"** — nó chính là phần cuối chương 2 và là chỗ hay bị hỏi nhất
khi bảo vệ. Đọc mà không điền cột đó thì coi như chưa đọc xong.

**Mục tiêu**: ~25 tài liệu, phân bổ theo 5 chủ đề dưới đây. Không cần đọc toàn văn tất cả — với
đa số, đọc kỹ abstract + introduction + kết luận là đủ; chỉ 5–7 bài cốt lõi mới cần đọc hết.

---

## Trạng thái

| Chủ đề | Mục tiêu | Đã đọc |
|---|---|---|
| 1. Static analysis và false positive | 6 | 0 |
| 2. LLM cho phân tích mã nguồn | 6 | 0 |
| 3. AI Agent trong kỹ nghệ phần mềm | 5 | 0 |
| 4. RAG và agentic retrieval | 4 | 0 |
| 5. Evidence grounding, traceability, human-in-the-loop | 4 | 0 |
| **Tổng** | **25** | **0** |

---

## 1. Static analysis và vấn đề false positive

Nền tảng cho phần 2.1–2.3 chương 2. Cần trả lời được: vì sao công cụ sinh nhiều cảnh báo sai, và
người ta đã thử giải quyết bằng cách nào trước khi có LLM.

Từ khoá tìm kiếm: `static analysis false positive`, `actionable alerts`, `alert prioritization`,
`warning classification`, `developer perception static analysis`.

Nguồn nên tìm: ICSE, FSE, ASE, TSE, EMSE; tài liệu chính thức của SpotBugs/find-sec-bugs/Semgrep.

Hướng cụ thể nên tìm:
- Nghiên cứu khảo sát vì sao lập trình viên bỏ qua cảnh báo phân tích tĩnh.
- Phương pháp xếp hạng/lọc cảnh báo bằng học máy trước thời LLM (để làm baseline so sánh).
- Thống kê tỉ lệ false positive thực tế của các công cụ phổ biến.
- Định nghĩa "actionable alert" và cách cộng đồng đo nó.

> Liên hệ với kết quả thực nghiệm đã có: xem `case-study/vulnshop/SEEDED.md` — SpotBugs và
> Semgrep bất đồng ở `ProductRepository:50`, và cả hai đều bỏ sót ba lỗi logic. Đây là dữ liệu
> của chính đồ án để đối chiếu với con số trong các bài báo.

---

## 2. LLM cho phân tích mã nguồn

Nền tảng cho phần 2.4. Cần trả lời được: LLM làm được gì với mã nguồn, và **thất bại ở đâu** khi
thiếu bằng chứng.

Từ khoá: `LLM code analysis`, `LLM vulnerability detection`, `LLM false positive triage`,
`hallucination code generation`, `LLM4SE survey`.

Hướng cụ thể nên tìm:
- Khảo sát tổng quan LLM trong kỹ nghệ phần mềm (ít nhất 1 bài survey gần đây làm khung).
- Nghiên cứu dùng LLM để phân loại true/false positive của công cụ phân tích tĩnh — **đây là
  công trình gần đề tài nhất, phải đọc kỹ và nêu rõ đồ án khác chỗ nào**.
- Bằng chứng định lượng về việc LLM đưa ra kết luận thiếu căn cứ khi ngữ cảnh không đủ.
- Ảnh hưởng của độ dài và chất lượng ngữ cảnh tới độ chính xác.

---

## 3. AI Agent trong kỹ nghệ phần mềm

Nền tảng cho phần 2.5. Cần trả lời được: agent khác gì so với gọi LLM một lần, và tool-use mang
lại gì.

Từ khoá: `LLM agent software engineering`, `tool-augmented LLM`, `ReAct`, `function calling`,
`autonomous program repair`, `SWE-bench`.

Hướng cụ thể nên tìm:
- Bài gốc về mô hình suy luận + hành động xen kẽ (nền tảng lý thuyết cho vòng lặp agent ở §3.4
  đề cương).
- Agent có khả năng gọi công cụ để tự truy xuất thông tin thay vì nhận hết trong một prompt.
- Benchmark đánh giá agent trên tác vụ phần mềm thật.
- Hạn chế đã ghi nhận: agent lặp vô hạn, gọi tool sai, chi phí token.

---

## 4. RAG và agentic retrieval

Nền tảng cho phần 2.5–2.6. Cần trả lời được: vì sao truy xuất theo nhu cầu tốt hơn nhồi hết vào
prompt, và cách kết hợp truy xuất chính xác theo khoá với truy xuất ngữ nghĩa.

Từ khoá: `retrieval-augmented generation`, `agentic RAG`, `hybrid retrieval`,
`context budget LLM`, `code retrieval embedding`.

Hướng cụ thể nên tìm:
- Bài gốc về RAG.
- So sánh truy xuất theo từ khoá và truy xuất theo embedding trên dữ liệu mã nguồn.
- Kỹ thuật xếp hạng lại và cắt theo ngân sách ngữ cảnh.
- Hiện tượng chất lượng giảm khi prompt bị pha loãng bởi thông tin không liên quan.

---

## 5. Evidence grounding, traceability và human-in-the-loop

Nền tảng cho phần 2.6–2.8, và là phần **đặc thù nhất của đề tài** — cũng là chỗ khoảng trống
nghiên cứu rõ nhất.

Từ khoá: `attribution LLM`, `citation generation`, `groundedness evaluation`,
`faithfulness metric`, `traceability software engineering`, `human-in-the-loop AI`.

Hướng cụ thể nên tìm:
- Cách đo mức độ một câu trả lời được bằng chứng chống lưng (trực tiếp liên quan tới chỉ số
  Unsupported Claim Rate và Evidence Citation Accuracy ở §5.2 đề cương).
- Kỹ thuật buộc mô hình trích dẫn nguồn và kiểm tra lại trích dẫn sau khi sinh.
- Truy vết trong kỹ nghệ phần mềm (liên kết yêu cầu ↔ mã ↔ test) — nền lý thuyết cho Unified
  Evidence Model.
- Thiết kế giao diện để con người kiểm tra và phản bác kết luận của AI.

---

## 6. Khoảng trống nghiên cứu (viết sau khi đọc xong)

Phần này là kết luận của chương 2. Điền dần khi đọc, đừng để tới cuối mới viết.

**Luận điểm dự kiến** (cần tài liệu chống lưng, chưa được coi là đã chứng minh):

1. Các công trình dùng LLM phân loại cảnh báo tĩnh chủ yếu đưa **mã nguồn + nội dung cảnh báo**
   vào prompt, ít công trình bổ sung **bằng chứng kiểm thử** (kết quả test, stack trace, độ phủ).
2. Khi có dùng nhiều nguồn thông tin, chúng thường được ghép thành văn bản phẳng trong một prompt
   chứ không được **mô hình hoá có cấu trúc và định danh** để trích dẫn lại được.
3. Việc **hậu kiểm trích dẫn độc lập với LLM** ít khi được hiện thực — đa số dừng ở chỗ yêu cầu
   mô hình tự trích dẫn mà không đối chiếu lại.

Nếu tìm được công trình đã làm đúng những điều trên thì phải điều chỉnh phạm vi đóng góp cho
trung thực, chứ không được lờ đi. Ghi lại ở đây khi gặp.

---

## 7. Template ghi chép

Sao chép khối này cho mỗi tài liệu. Đặt các khối theo chủ đề ở mục 8.

```markdown
### [Mã] Tên bài — Tác giả, Hội nghị/Tạp chí, Năm

- **Chủ đề**: 1 | 2 | 3 | 4 | 5
- **Mức độ đọc**: đọc kỹ toàn văn | đọc abstract + kết luận
- **Bài toán**: (1–2 câu — họ giải quyết vấn đề gì)
- **Phương pháp**: (2–3 câu — làm thế nào)
- **Dữ liệu và kết quả**: (số liệu cụ thể, không viết chung chung "kết quả tốt")
- **Hạn chế tác giả tự nêu**:
- **Khoảng trống mà ĐATN lấp**: ← QUAN TRỌNG NHẤT
- **Trích dẫn ở mục nào của báo cáo**:
- **Link/DOI**:
```

Hai lưu ý khi ghi:

- **Ghi số liệu cụ thể**, không viết "cải thiện đáng kể". Lúc viết chương 2 cần con số để so
  sánh, và lúc bảo vệ có thể bị hỏi thẳng.
- **Ghi hạn chế tác giả tự nêu.** Đây là nguồn tốt nhất cho phần khoảng trống nghiên cứu, vì
  chính tác giả đã chỉ ra chỗ chưa làm được.

---

## 8. Các tài liệu đã đọc

*(chưa có — thêm dần theo template ở mục 7)*
