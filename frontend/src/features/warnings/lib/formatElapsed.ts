/** `m:ss`, hoặc `h:mm:ss` từ 1 giờ trở lên — trước đây ra `6166:13` cho một run treo 4 ngày. */
export function formatElapsed(startedAt: string, now = Date.now()): string {
  const total = Math.max(0, Math.floor((now - new Date(startedAt).getTime()) / 1000));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = String(total % 60).padStart(2, '0');
  return hours > 0
    ? `${String(hours)}:${String(minutes).padStart(2, '0')}:${seconds}`
    : `${String(minutes)}:${seconds}`;
}
