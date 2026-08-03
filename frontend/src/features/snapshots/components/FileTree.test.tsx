/**
 * Canh hai hành vi mà `buildFileTree.test.ts` không chạm tới được vì chúng thuộc về component:
 * gập/mở thư mục, và TỰ BUNG tới file đang mở khi vào bằng deep link.
 *
 * Hành vi thứ hai quan trọng với tiêu chí nghiệm thu tuần 5-7: mở
 * `/snapshots/x/files/src/main/App.java` ở tab mới mà cây vẫn đóng kín thì người dùng thấy file
 * đúng nhưng không biết nó nằm ở đâu, và không có đường bấm sang file bên cạnh.
 */

import { describe, expect, it } from 'vitest';

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

import { FileTree } from '@/features/snapshots/components/FileTree';
import { buildFileTree, type SnapshotFile } from '@/features/snapshots/lib/buildFileTree';

function file(path: string, overrides: Partial<SnapshotFile> = {}): SnapshotFile {
  const lastSlash = path.lastIndexOf('/');
  return {
    path,
    parentPath: lastSlash < 0 ? '' : path.slice(0, lastSlash),
    name: lastSlash < 0 ? path : path.slice(lastSlash + 1),
    entryType: 'FILE',
    sizeBytes: 10,
    lineCount: 1,
    binary: false,
    stored: true,
    ...overrides,
  };
}

function renderTree(entries: SnapshotFile[], activePath = '') {
  const nodes = buildFileTree(entries, { collapseSingleChildDirs: false });
  return render(
    <MemoryRouter>
      <FileTree nodes={nodes} snapshotId="s1" activePath={activePath} />
    </MemoryRouter>,
  );
}

describe('FileTree', () => {
  it('thư mục đóng mặc định, không hiện file bên trong', () => {
    renderTree([file('src/App.java')]);

    expect(screen.getByRole('button', { name: /src/ })).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByText('App.java')).not.toBeInTheDocument();
  });

  it('bấm vào thư mục thì bung ra', async () => {
    renderTree([file('src/App.java')]);

    await userEvent.click(screen.getByRole('button', { name: /src/ }));

    expect(screen.getByText('App.java')).toBeInTheDocument();
  });

  it('bấm lần nữa thì gập lại', async () => {
    renderTree([file('src/App.java')]);
    const dir = screen.getByRole('button', { name: /src/ });

    await userEvent.click(dir);
    await userEvent.click(dir);

    expect(screen.queryByText('App.java')).not.toBeInTheDocument();
  });

  /** Deep link: mọi thư mục cha của file đang mở phải tự bung. */
  it('tự bung toàn bộ đường dẫn tới file đang mở', () => {
    renderTree([file('src/main/java/App.java')], 'src/main/java/App.java');

    expect(screen.getByText('App.java')).toBeInTheDocument();
  });

  it('không bung thư mục nằm ngoài đường dẫn của file đang mở', () => {
    renderTree([file('src/App.java'), file('docs/README.md')], 'src/App.java');

    expect(screen.getByText('App.java')).toBeInTheDocument();
    expect(screen.queryByText('README.md')).not.toBeInTheDocument();
  });

  it('link file trỏ tới URL đã mã hoá theo từng đoạn', async () => {
    renderTree([file('tài liệu/ghi chú.md')]);

    await userEvent.click(screen.getByRole('button', { name: /tài liệu/ }));

    expect(screen.getByRole('link', { name: 'ghi chú.md' })).toHaveAttribute(
      'href',
      '/snapshots/s1/files/t%C3%A0i%20li%E1%BB%87u/ghi%20ch%C3%BA.md',
    );
  });

  /** Đánh dấu ngay trên cây để người dùng biết trước khi bấm vào một file không đọc được. */
  it('đánh dấu file nhị phân', () => {
    renderTree([file('app.bin', { binary: true, lineCount: undefined })]);

    expect(screen.getByRole('link', { name: /app.bin.*nhị phân/ })).toBeInTheDocument();
  });

  it('snapshot rỗng hiện thông báo thay vì cây trống', () => {
    renderTree([]);

    expect(screen.getByText('Snapshot này không có file nào.')).toBeInTheDocument();
  });
});
