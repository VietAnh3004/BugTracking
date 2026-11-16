/**
 * Ảnh đại diện: ảnh Google hoặc Gravatar do backend chọn (`avatarUrl`), lỗi tải thì hiện chữ cái
 * đầu của tên.
 *
 * Gravatar được gọi với `d=404` nên người CHƯA đăng ký ảnh ở Gravatar nhận lỗi 404 — đó là đường
 * bình thường, không phải sự cố, và `onError` bên dưới chính là nhánh xử lý nó.
 *
 * `referrerPolicy="no-referrer"`: ảnh của Google (`lh3.googleusercontent.com`) có lúc trả 403 khi
 * request mang header Referer từ một trang lạ — và cũng không có lý do gì để báo cho Google hay
 * Gravatar biết người dùng đang mở trang nào của ứng dụng.
 */

import { useState } from 'react';

interface AvatarProps {
  displayName: string;
  avatarUrl?: string;
  size?: 'sm' | 'lg';
}

const SIZES = {
  sm: 'size-8 text-xs',
  lg: 'size-16 text-lg',
} as const;

export function Avatar({ displayName, avatarUrl, size = 'sm' }: AvatarProps) {
  // Ghi nhớ URL ĐÃ hỏng chứ không phải một cờ boolean: đổi sang URL khác (vừa liên kết Google)
  // thì phải thử tải lại, không được kẹt mãi ở chữ cái đầu.
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const showImage = avatarUrl !== undefined && avatarUrl !== '' && failedUrl !== avatarUrl;

  if (showImage) {
    return (
      <img
        src={avatarUrl}
        alt=""
        aria-hidden="true"
        referrerPolicy="no-referrer"
        onError={() => setFailedUrl(avatarUrl)}
        className={`${SIZES[size]} shrink-0 rounded-full object-cover`}
      />
    );
  }

  return (
    <span
      aria-hidden="true"
      className={`${SIZES[size]} bg-brand-subtle text-brand grid shrink-0 place-items-center rounded-full font-semibold`}
    >
      {initialsOf(displayName)}
    </span>
  );
}

/** "Nguyễn Việt Anh" → "VA": hai chữ cái cuối, đúng cách người Việt gọi tên nhau. */
function initialsOf(displayName: string): string {
  const words = displayName
    .trim()
    .split(/\s+/)
    .filter((word) => word !== '');
  const picked = words.length <= 2 ? words : words.slice(-2);
  return picked
    .map((word) => word.charAt(0).toUpperCase())
    .join('')
    .slice(0, 2);
}
