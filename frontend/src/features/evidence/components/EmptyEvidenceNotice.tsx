/**
 * Trạng thái "không có bằng chứng liên quan nào" — một KẾT QUẢ, không phải một lỗi.
 *
 * Đây là cơ sở của verdict `INSUFFICIENT_EVIDENCE`, một trong ba verdict của hệ thống. Hiển thị nó
 * như một khoảng trống mờ nhạt là làm mất đúng thông tin quan trọng nhất mà màn hình này có thể
 * nói ra.
 *
 * Ba quy tắc, và cả ba đều có lý do:
 * 1. Viền ĐỨT NÉT, chữ mực thường — không xám nhạt (đọc như "không có gì đáng chú ý"), không đỏ
 *    (đọc như lỗi).
 * 2. Nói VÌ SAO khi biết vì sao: chuẩn hoá đường dẫn hỏng và "linker đã chạy và không tìm thấy" là
 *    hai tình huống khác hẳn nhau, cần hai câu khác nhau.
 * 3. Nói luôn HỆ QUẢ, để người dùng biết agent sẽ kết luận gì ở tuần 12-13.
 */

export function EmptyEvidenceNotice({ locationResolved }: { locationResolved: boolean }) {
  return (
    <div className="border-border text-ink rounded border border-dashed p-4">
      <p className="mt-0 mb-2 font-semibold">
        Không có bằng chứng nào liên kết tới bằng chứng này.
      </p>

      {locationResolved ? (
        <p className="mt-0 mb-2 text-sm">
          Lần phân tích đã chạy xong và EvidenceLinker không tìm được liên kết nào. Đây là một{' '}
          <strong>kết quả</strong>, không phải lỗi tải dữ liệu.
        </p>
      ) : (
        <p className="mt-0 mb-2 text-sm">
          Vị trí mà công cụ khai không đối chiếu được với mã nguồn đã tải lên, nên cả ba tầng liên
          kết (vị trí · độ phủ · stack trace) đều không có điểm neo để chạy.
        </p>
      )}

      <p className="text-ink-muted m-0 text-sm">
        AI Agent sẽ kết luận <code className="font-mono">INSUFFICIENT_EVIDENCE</code> cho trường hợp
        này.
      </p>
    </div>
  );
}
