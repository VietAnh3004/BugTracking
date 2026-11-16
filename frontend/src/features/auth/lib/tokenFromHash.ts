/**
 * Đọc token từ FRAGMENT của link trong email (`/verify-email#token=...`).
 *
 * Backend cố ý đặt token ở fragment chứ không ở query string: trình duyệt không bao giờ gửi
 * fragment lên máy chủ, nên token không nằm trong access log của nginx, và không lọt ra ngoài qua
 * header `Referer`.
 */
export function tokenFromHash(hash: string): string | null {
  const params = new URLSearchParams(hash.startsWith('#') ? hash.slice(1) : hash);
  const token = params.get('token');
  return token === null || token === '' ? null : token;
}
