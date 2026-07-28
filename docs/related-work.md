# Khảo sát tài liệu — Chương 2 báo cáo

Khung để đọc và ghi chép, bám theo mục lục chương 2 trong đề cương.

**Cách dùng**: mỗi tài liệu đọc xong thì điền vào một khối theo template ở mục 7. Cột quan trọng
nhất là **"Khoảng trống mà ĐATN lấp"** — nó chính là phần cuối chương 2 và là chỗ hay bị hỏi nhất
khi bảo vệ. Đọc mà không điền cột đó thì coi như chưa đọc xong.

**Mục tiêu**: ~25 tài liệu, phân bổ theo 5 chủ đề dưới đây. Không cần đọc toàn văn tất cả — với
đa số, đọc kỹ abstract + introduction + kết luận là đủ; chỉ 5–7 bài cốt lõi mới cần đọc hết.

---

## Trạng thái

| Chủ đề | Mục tiêu | Đã tìm thấy (chưa đọc) | Đã đọc |
|---|---|---|---|
| 1. Static analysis và false positive | 6 | 3 | 0 |
| 2. LLM cho phân tích mã nguồn | 6 | 4 | 0 |
| 3. AI Agent trong kỹ nghệ phần mềm | 5 | 3 | 0 |
| 4. RAG và agentic retrieval | 4 | 3 | 0 |
| 5. Evidence grounding, traceability, human-in-the-loop | 4 | 3 | 0 |
| **Tổng** | **25** | **16** | **0** |

**16 tài liệu đã tìm và xác minh tồn tại thật** (link/DOI kiểm tra được qua tìm kiếm), liệt kê ở
mục 8. Còn thiếu ~9 bài để đủ mục tiêu — chủ yếu ở chủ đề 1 (baseline ML xếp hạng cảnh báo trước
LLM) và các bài mở rộng cho chủ đề 3–5. Việc **đọc và điền template** (bài toán, phương pháp, số
liệu, khoảng trống) vẫn là việc của bạn — xem lưu ý ở cuối mục 8.

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

## 8. Tài liệu đã tìm thấy

**Cách đọc mục này**: mỗi bài dưới đây đã được xác minh tồn tại thật qua tìm kiếm (có link
truy cập được), nhưng **chưa đọc và chưa điền template**. Trường "Vì sao liên quan" là tóm tắt
từ kết quả tìm kiếm, không thay thế việc đọc — dùng nó để quyết định thứ tự đọc, không dùng để
trích dẫn thẳng vào báo cáo. Khi đọc xong, thay khối này bằng khối đầy đủ theo template ở mục 7.

Thứ tự ưu tiên đọc: nhóm ⭐ trước — đó là các bài nền tảng hoặc gần đề tài nhất.

### Chủ đề 1 — Static analysis và false positive

**[1-A] ⭐ Why don't software developers use static analysis tools to find bugs?**
Johnson, Song, Murphy-Hill, Bowdidge — ICSE 2013.
Vì sao liên quan: bài kinh điển nhất về rào cản tiếp nhận công cụ phân tích tĩnh — phỏng vấn
20 lập trình viên, chỉ ra false positive và cách trình bày cảnh báo là rào cản chính.
Link: https://cs.gmu.edu/~johnsonb/docs/icse2013.pdf

**[1-B] Tricorder: Building a Program Analysis Ecosystem**
Sadowski, van Gogh, Jaspan, Söderberg, Winter — ICSE 2015 (Google).
Vì sao liên quan: hệ thống phân tích tĩnh công nghiệp thật, tích hợp vào code review; có nút
"NOT USEFUL" để đo tỉ lệ false positive liên tục — mô hình tham khảo cho việc đo actionability.
Link: https://research.google.com/pubs/archive/43322.pdf

**[1-C] Mitigating False Positive Static Analysis Warnings: Progress, Challenges, and Opportunities**
IEEE Transactions on Software Engineering, 2023.
Vì sao liên quan: khảo sát tổng quan (survey) các hướng giảm false positive — dùng làm khung
liệt kê baseline trước-LLM.
Link: https://liu906.github.io/papers/Mitigating_False_Positive_Static_Analysis_Warnings_Progress_Challenges_and_Opportunities.pdf

*Còn thiếu ~3 bài: một bài học máy xếp hạng cảnh báo cụ thể (ví dụ dòng nghiên cứu Z-Ranking/
Tian et al.), thống kê tỉ lệ FP thực tế của SpotBugs/Semgrep cụ thể, và định nghĩa "actionable
alert" gốc.*

### Chủ đề 2 — LLM cho phân tích mã nguồn

**[2-A] ⭐ Large Language Models for Software Engineering: Survey and Open Problems**
Fan, Gokkaya, Harman, Lyubarskiy, Sengupta, Yoo, Zhang — ICSE-FoSE 2023.
Vì sao liên quan: khảo sát tổng quan LLM4SE, dùng làm khung mở đầu phần 2.4. Nêu rõ nhu cầu kỹ
thuật "hybrid" (SE truyền thống + LLM) để lọc hallucination — liên hệ trực tiếp luận điểm đồ án.
Link: https://arxiv.org/abs/2310.03533

**[2-B] ⭐⭐ Using LLMs to Adjudicate Static-Analysis Alerts**
Kang, Lieber và cộng sự — CMU (Software Engineering Institute).
Vì sao liên quan: **công trình gần đề tài nhất tìm được** — dùng LLM phân xử cảnh báo phân tích
tĩnh true/false positive. Cần đọc kỹ nhất và so sánh trực diện: họ có dùng bằng chứng kiểm thử
không, có cơ chế hậu kiểm trích dẫn độc lập không.
Link: https://www.andrew.cmu.edu/user/wklieber/LLMs-to-Adjudicate-Static-Analysis-Alerts.pdf

**[2-C] Effectiveness of ChatGPT for Static Analysis: How Far Are We?**
ACM International Conference on AI-Powered Software (AIware) 2024.
Vì sao liên quan: đánh giá thực nghiệm ChatGPT trên phát hiện lỗi tĩnh (Null Dereference,
Resource Leak) và loại bỏ cảnh báo sai — có số liệu cụ thể để so sánh.
Link: https://dl.acm.org/doi/10.1145/3664646.3664777

**[2-D] Sifting the Noise: A Comparative Study of LLM Agents in Vulnerability False Positive Filtering**
arXiv 2601.22952 (2026).
Vì sao liên quan: so sánh nhiều LLM agent trong việc lọc false positive lỗ hổng bảo mật — có số
liệu true/false positive rate cụ thể (ví dụ baseline Semgrep: TP 80.49%, FP 39.09%) để đối chiếu
với kết quả thực nghiệm của chính đồ án trên vulnshop.
Link: https://arxiv.org/pdf/2601.22952

*Còn thiếu ~2 bài: một bài đo trực tiếp hiện tượng "kết luận thiếu căn cứ khi ngữ cảnh không đủ"
(có thể tìm trong hướng hallucination-in-code-generation), và một bài về ảnh hưởng độ dài ngữ
cảnh tới độ chính xác phân loại — có thể dùng chung với [4-C] ở chủ đề 4.*

### Chủ đề 3 — AI Agent trong kỹ nghệ phần mềm

**[3-A] ⭐ ReAct: Synergizing Reasoning and Acting in Language Models**
Yao, Zhao, Yu, Du, Shafran, Narasimhan, Cao — ICLR 2023.
Vì sao liên quan: nền tảng lý thuyết trực tiếp cho vòng lặp agent ở §3.4 đề cương — mô hình suy
luận và hành động xen kẽ, đúng cấu trúc "gọi tool → quan sát → suy luận tiếp".
Link: https://arxiv.org/abs/2210.03629

**[3-B] SWE-bench: Can Language Models Resolve Real-World GitHub Issues?**
Jimenez, Yang và cộng sự — Princeton, ICLR 2024.
Vì sao liên quan: benchmark đánh giá agent trên tác vụ phần mềm thật, quy mô 2294 issue GitHub
thật. Kết quả gốc cho thấy giới hạn thật của agent (Claude 2 chỉ giải được 1.96% issue) — dữ
liệu cụ thể cho phần hạn chế của agent trong chương 2.
Link: https://arxiv.org/abs/2310.06770

**[3-C] Beyond the Leaderboard: A Synthesis of Tool-Use, Planning, and Reasoning Failures in Large Language Model Agents**
arXiv 2607.05775 (2026).
Vì sao liên quan: tổng hợp các dạng lỗi thật của agent khi dùng tool (gọi sai tham số, lặp vô
hạn, hallucinate tên hàm) — trực tiếp liên quan tới `max_tool_iterations` và thiết kế
CitationValidator độc lập với LLM của đồ án.
Link: https://arxiv.org/pdf/2607.05775

*Còn thiếu ~2 bài: bài gốc về function calling/tool-use (ví dụ Toolformer hoặc tài liệu chính
thức của Anthropic/OpenAI về tool-use), và một case study agent áp dụng cụ thể cho kỹ nghệ phần
mềm (code review agent, tự động sửa lỗi).*

### Chủ đề 4 — RAG và agentic retrieval

**[4-A] ⭐ Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks**
Lewis, Perez, Piktus, Petroni, Karpukhin, Goyal, Küttler, Lewis, Yih, Rocktäschel, Riedel, Kiela
— NeurIPS 2020.
Vì sao liên quan: bài gốc định nghĩa RAG — nền tảng lý thuyết cho toàn bộ kiến trúc Evidence
Retriever của đồ án.
Link: https://proceedings.neurips.cc/paper_files/paper/2020/file/6b493230205f780e1bc26945df7481e5-Paper.pdf

**[4-B] ⭐ Lost in the Middle: How Language Models Use Long Contexts**
Liu, Lin, Hewitt, Paranjape, Bevilacqua, Petroni, Liang — TACL 2024 (arXiv 2307.03172, 2023).
Vì sao liên quan: chứng minh thực nghiệm hiện tượng pha loãng ngữ cảnh — luận cứ trực tiếp cho
quyết định thiết kế "lọc và xếp hạng bằng chứng theo context budget" ở §3.4 đề cương thay vì
nhồi hết bằng chứng vào một prompt.
Link: https://arxiv.org/abs/2307.03172

**[4-C] CodeBERT: A Pre-Trained Model for Programming and Natural Languages**
Feng, Guo, Tang, Duan, Feng, Gong, Shou, Qin, Liu, Jiang, Zhou — Findings of EMNLP 2020
(Microsoft Research).
Vì sao liên quan: mô hình nền tảng cho truy xuất ngữ nghĩa trên mã nguồn (embedding code↔text) —
liên quan trực tiếp tới thiết kế `search_evidence` và bảng `evidence_embedding`.
Link: https://arxiv.org/abs/2002.08155

*Còn thiếu ~1 bài: một bài cụ thể về hybrid retrieval (kết hợp BM25 + dense) trên dữ liệu mã
nguồn — tìm kiếm ban đầu chỉ ra được nguồn blog/công nghiệp (Pinecone, v.v.), cần tìm thêm nguồn
học thuật (ví dụ dòng nghiên cứu về Reciprocal Rank Fusion gốc, Cormack et al. 2009).*

### Chủ đề 5 — Evidence grounding, traceability, human-in-the-loop

**[5-A] ⭐⭐ Enabling Large Language Models to Generate Text with Citations**
Gao, Yen, Yu, Chen (Princeton) — EMNLP 2023. Benchmark ALCE.
Vì sao liên quan: **liên quan trực tiếp nhất tới §3.4.2 và §5.2 đề cương** — đo độ chính xác
trích dẫn của LLM bằng ba chiều fluency/correctness/citation quality. Số liệu cụ thể: ngay cả
mô hình tốt nhất cũng thiếu trích dẫn hỗ trợ đầy đủ 50% thời gian trên ELI5 — điểm tham chiếu
định lượng trực tiếp cho Unsupported Claim Rate của đồ án.
Link: https://arxiv.org/abs/2305.14627

**[5-B] ⭐ Software Traceability: Trends and Future Directions**
Cleland-Huang — ICSE-FOSE 2014.
Vì sao liên quan: nền lý thuyết cho Unified Evidence Model — liên kết yêu cầu↔mã↔kiểm thử, và
nhận định traceability phải "nhúng vào quy trình phát triển chứ không phải tài liệu hậu kỳ".
Link: http://selab.netlab.uky.edu/homepage/publications/2014-ICSE-FOSE.pdf

**[5-C] A Survey of Human-in-the-loop for Machine Learning**
Wu, Xiao, Sun, Zhang, Ma, He — Future Generation Computer Systems, 2022.
Vì sao liên quan: phân loại các cách con người tham gia vào vòng lặp AI — dùng làm khung lý
thuyết cho thiết kế Human Review Module (Accept/Reject/Uncertain/Insufficient Evidence).
Link: https://arxiv.org/abs/2108.00941

*Còn thiếu ~1 bài: một bài cụ thể hơn về "faithfulness evaluation" (đo mức độ câu trả lời được
bằng chứng chống lưng) — tìm kiếm ban đầu chỉ ra được survey tổng quan
(arxiv.org/pdf/2508.15396, "Attribution, Citation, and Quotation") nhưng chưa xác minh kỹ, cần
đọc và kiểm tra lại trước khi đưa vào danh sách chính thức.*

---

**Lưu ý quan trọng khi dùng danh sách này**: các trường "Vì sao liên quan" ở trên do tìm kiếm và
tổng hợp, **không phải do đọc toàn văn**. Trước khi trích dẫn bất kỳ bài nào vào báo cáo, phải:

1. Mở link, xác nhận tiêu đề/tác giả/năm khớp với những gì ghi ở đây.
2. Đọc ít nhất abstract + kết luận, điền đầy đủ template ở mục 7 (đặc biệt là "Hạn chế tác giả
   tự nêu" và "Khoảng trống mà ĐATN lấp").
3. Với các bài đánh dấu ⭐⭐ ([2-B], [5-A]) — đọc toàn văn, vì đây là công trình gần đề tài nhất
   và nhiều khả năng bị hỏi kỹ nhất khi bảo vệ.
