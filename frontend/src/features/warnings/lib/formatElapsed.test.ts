import { describe, expect, it } from 'vitest';

import { formatElapsed } from '@/features/warnings/lib/formatElapsed';

describe('formatElapsed', () => {
  const start = '2026-10-30T11:24:24Z';
  const at = (seconds: number) => new Date(start).getTime() + seconds * 1000;

  it('dưới 1 giờ thì m:ss', () => {
    expect(formatElapsed(start, at(171))).toBe('2:51');
  });

  /** Trước đây ra `6166:13` cho một run treo 4 ngày — đọc không ra là bao lâu. */
  it('từ 1 giờ trở lên thì h:mm:ss', () => {
    expect(formatElapsed(start, at(6166 * 60 + 13))).toBe('102:46:13');
  });
});
