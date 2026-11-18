import { INPUT } from '@/shared/ui/styles';

/**
 * Ô nhập có nhãn. Thuần trình bày: không biết form nào đang dùng nó, không gọi API.
 *
 * Chuyển từ `features/auth/components/AuthForm.tsx` ra đây khi form tạo dự án cần đúng thứ này —
 * gom trước khi có bản chép thứ hai, thay vì sau.
 *
 * `htmlFor`/`id` bắt buộc đi cặp: thiếu thì `getByLabelText` trong test không tìm ra ô nhập, và
 * quan trọng hơn là trình đọc màn hình không đọc được nhãn.
 */

interface TextFieldProps {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  autoComplete?: string;
  required?: boolean;
  placeholder?: string;
  /** `id` của phần mô tả đi kèm (vd danh sách yêu cầu mật khẩu) — gắn vào `aria-describedby`. */
  describedBy?: string;
}

export function TextField({
  id,
  label,
  value,
  onChange,
  type = 'text',
  autoComplete,
  required = true,
  placeholder,
  describedBy,
}: TextFieldProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-ink text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        type={type}
        value={value}
        required={required}
        autoComplete={autoComplete}
        placeholder={placeholder}
        aria-describedby={describedBy}
        onChange={(event) => onChange(event.target.value)}
        className={INPUT}
      />
    </div>
  );
}
