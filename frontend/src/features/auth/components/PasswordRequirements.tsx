/**
 * Danh sách yêu cầu mật khẩu dưới ô nhập: yêu cầu nào đạt rồi chuyển chữ xanh lá kèm dấu tích,
 * cập nhật theo từng phím gõ. Quy tắc lấy từ `passwordRules.ts`, không tự định nghĩa ở đây.
 *
 * Màu không phải tín hiệu duy nhất: icon đổi từ chấm tròn sang dấu tích, và mỗi dòng có thêm chữ
 * ẩn "đã đạt"/"chưa đạt" cho trình đọc màn hình (icon đều `aria-hidden`).
 */

import { PASSWORD_RULES } from '@/features/auth/lib/passwordRules';
import { IconCheck } from '@/shared/ui/icons';

interface PasswordRequirementsProps {
  /** Đi cặp với `describedBy` của ô mật khẩu để trình đọc màn hình đọc kèm danh sách. */
  id: string;
  password: string;
  /** `true` sau khi người dùng bấm gửi mà còn thiếu yêu cầu: dòng chưa đạt chuyển đỏ thay xám. */
  flagUnmet?: boolean;
}

export function PasswordRequirements({
  id,
  password,
  flagUnmet = false,
}: PasswordRequirementsProps) {
  return (
    <ul id={id} aria-label="Yêu cầu mật khẩu" className="m-0 flex list-none flex-col gap-1 p-0">
      {PASSWORD_RULES.map((rule) => {
        const met = rule.test(password);
        return (
          <li
            key={rule.id}
            data-met={met}
            className={`flex items-center gap-2 text-xs transition-colors ${
              met ? 'font-medium text-emerald-600' : flagUnmet ? 'text-red-600' : 'text-ink-muted'
            }`}
          >
            {met ? (
              <IconCheck className="size-3.5" strokeWidth={2.4} />
            ) : (
              <span aria-hidden="true" className="grid size-3.5 shrink-0 place-items-center">
                <span
                  className={`size-1.5 rounded-full border-[1.5px] ${flagUnmet ? 'border-red-500' : 'border-border-strong'}`}
                />
              </span>
            )}
            <span>
              {rule.label}
              <span className="sr-only">{met ? ' (đã đạt)' : ' (chưa đạt)'}</span>
            </span>
          </li>
        );
      })}
    </ul>
  );
}
