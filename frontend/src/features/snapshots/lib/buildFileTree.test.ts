import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  buildFileTree,
  type SnapshotFile,
  type TreeNode,
} from '@/features/snapshots/lib/buildFileTree';

/**
 * `noUncheckedIndexedAccess` đang bật nên `tree[0]` có kiểu `TreeNode | undefined`. Dùng `!` sẽ
 * làm test đỏ với "Cannot read property of undefined" thay vì một thông điệp nói rõ chỗ sai —
 * helper này hỏng ồn ào và chỉ đúng chỉ số bị thiếu.
 */
function nodeAt(nodes: TreeNode[], index: number): TreeNode {
  const node = nodes[index];
  if (node === undefined) {
    throw new Error(`Cây không có node ở vị trí ${String(index)} (chỉ có ${String(nodes.length)})`);
  }
  return node;
}

function file(path: string): SnapshotFile {
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
  };
}

function directory(path: string): SnapshotFile {
  const lastSlash = path.lastIndexOf('/');
  return {
    path,
    parentPath: lastSlash < 0 ? '' : path.slice(0, lastSlash),
    name: lastSlash < 0 ? path : path.slice(lastSlash + 1),
    entryType: 'DIRECTORY',
    sizeBytes: 0,
    binary: false,
    stored: false,
  };
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('buildFileTree', () => {
  it('dựng cây từ danh sách phẳng', () => {
    const tree = buildFileTree([directory('src'), file('src/App.java'), file('README.md')], {
      collapseSingleChildDirs: false,
    });

    expect(tree.map((n) => n.name)).toEqual(['src', 'README.md']);
    expect(nodeAt(tree, 0).children.map((n) => n.name)).toEqual(['App.java']);
  });

  it('tự sinh thư mục cha còn thiếu trong danh sách', () => {
    // Nguồn dữ liệu chỉ đưa file, không có hàng thư mục nào.
    const tree = buildFileTree([file('a/b/c.txt')], { collapseSingleChildDirs: false });

    expect(tree).toHaveLength(1);
    expect(nodeAt(tree, 0).path).toBe('a');
    expect(nodeAt(nodeAt(tree, 0).children, 0).path).toBe('a/b');
    expect(nodeAt(nodeAt(nodeAt(tree, 0).children, 0).children, 0).path).toBe('a/b/c.txt');
  });

  it('xếp thư mục trước file', () => {
    const tree = buildFileTree([file('a-file.txt'), directory('z-dir')], {
      collapseSingleChildDirs: false,
    });

    expect(tree.map((n) => n.name)).toEqual(['z-dir', 'a-file.txt']);
  });

  it('so sánh tên có hiểu số: v2 đứng trước v10', () => {
    const tree = buildFileTree([file('v10.txt'), file('v2.txt'), file('v1.txt')], {
      collapseSingleChildDirs: false,
    });

    expect(tree.map((n) => n.name)).toEqual(['v1.txt', 'v2.txt', 'v10.txt']);
  });

  /**
   * Repo Java điển hình: `src/main/java/vn/hust/datn` là 5 tầng thư mục rỗng chỉ vì quy ước
   * package. Không gộp thì mã nguồn thật bị đẩy xuống dưới màn hình.
   */
  it('gộp chuỗi thư mục chỉ có một thư mục con', () => {
    const tree = buildFileTree([file('src/main/java/vn/hust/datn/App.java')]);

    expect(tree).toHaveLength(1);
    expect(nodeAt(tree, 0).name).toBe('src/main/java/vn/hust/datn');
    // `path` vẫn là đường dẫn THẬT của thư mục sâu nhất, không phải nhãn hiển thị.
    expect(nodeAt(tree, 0).path).toBe('src/main/java/vn/hust/datn');
    expect(nodeAt(tree, 0).children.map((n) => n.name)).toEqual(['App.java']);
  });

  it('KHÔNG gộp khi con duy nhất là file', () => {
    // Gộp ở đây sẽ làm mất chỗ bấm vào chính thư mục `config`.
    const tree = buildFileTree([file('config/app.yml')]);

    expect(nodeAt(tree, 0).name).toBe('config');
    expect(nodeAt(tree, 0).children.map((n) => n.name)).toEqual(['app.yml']);
  });

  it('dừng gộp khi gặp thư mục có nhiều hơn một con', () => {
    const tree = buildFileTree([file('a/b/c/one.txt'), file('a/b/d/two.txt')]);

    expect(nodeAt(tree, 0).name).toBe('a/b');
    expect(nodeAt(tree, 0).children.map((n) => n.name)).toEqual(['c', 'd']);
  });

  /** Lớp phòng thủ thứ ba sau ZipExtractor và CHECK constraint của DB. */
  it('bỏ qua đường dẫn zip-slip và cảnh báo, không ném lỗi', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);

    const tree = buildFileTree([file('../../etc/passwd'), file('a/../b.txt'), file('hop-le.txt')], {
      collapseSingleChildDirs: false,
    });

    expect(tree.map((n) => n.name)).toEqual(['hop-le.txt']);
    expect(warn).toHaveBeenCalledTimes(2);
  });

  it('bỏ qua đường dẫn tuyệt đối', () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);

    const tree = buildFileTree([file('/etc/passwd'), file('ok.txt')], {
      collapseSingleChildDirs: false,
    });

    expect(tree.map((n) => n.name)).toEqual(['ok.txt']);
  });

  it('giữ metadata của file để cây hiện được số dòng và cờ nhị phân', () => {
    const binaryFile: SnapshotFile = { ...file('app.bin'), binary: true, lineCount: undefined };

    const tree = buildFileTree([binaryFile], { collapseSingleChildDirs: false });

    expect(nodeAt(tree, 0).file?.binary).toBe(true);
    expect(nodeAt(tree, 0).file?.lineCount).toBeUndefined();
  });

  it('danh sách rỗng cho cây rỗng, không ném lỗi', () => {
    expect(buildFileTree([])).toEqual([]);
  });
});
