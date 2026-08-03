/**
 * Dựng cây thư mục từ danh sách file PHẲNG mà API trả về. Hàm THUẦN, không import React — nhờ vậy
 * test được trực tiếp trên dữ liệu, không phải render component nào.
 *
 * Backend trả danh sách phẳng có chủ đích (xem kế hoạch tuần 5-7): repo cỡ đồ án vài trăm file,
 * JSON phẳng nhỏ hơn cây lồng và cho phép FE giữ nguyên trạng thái thư mục đang mở khi đổi file.
 */

import type { components } from '@/shared/lib/api-types';

export type SnapshotFile = components['schemas']['SnapshotFileResponse'];

export interface TreeNode {
  /** Đường dẫn thật, dùng để gọi API và dựng URL. Với thư mục đã gộp, đây là đường dẫn của node SÂU NHẤT. */
  path: string;
  /** Nhãn hiển thị. Với thư mục đã gộp thì là chuỗi ghép, vd `main/java/vn/hust/datn`. */
  name: string;
  type: 'FILE' | 'DIRECTORY';
  children: TreeNode[];
  /** Chỉ có ở node FILE — để cây hiện được kích thước, số dòng, cờ nhị phân mà không phải tra lại. */
  file?: SnapshotFile;
}

/**
 * @param entries danh sách phẳng từ `GET /snapshots/{id}/files`
 * @param options `collapseSingleChildDirs` mặc định bật — xem `collapseChain`
 */
export function buildFileTree(
  entries: SnapshotFile[],
  options: { collapseSingleChildDirs?: boolean } = {},
): TreeNode[] {
  const collapse = options.collapseSingleChildDirs ?? true;

  const nodesByPath = new Map<string, TreeNode>();
  const rootChildren: TreeNode[] = [];

  function ensureDirectory(path: string): TreeNode {
    const existing = nodesByPath.get(path);
    if (existing !== undefined) {
      return existing;
    }

    const lastSlash = path.lastIndexOf('/');
    const node: TreeNode = {
      path,
      name: lastSlash < 0 ? path : path.slice(lastSlash + 1),
      type: 'DIRECTORY',
      children: [],
    };
    nodesByPath.set(path, node);

    if (lastSlash < 0) {
      rootChildren.push(node);
    } else {
      // Thư mục cha có thể chưa xuất hiện trong danh sách (backend có tạo hàng thư mục, nhưng cây
      // vẫn phải dựng đúng nếu một ngày nào đó nguồn dữ liệu chỉ đưa file) — tự sinh đệ quy.
      ensureDirectory(path.slice(0, lastSlash)).children.push(node);
    }
    return node;
  }

  for (const entry of entries) {
    if (!isSafePath(entry.path)) {
      // Phòng thủ theo tầng: backend đã chặn zip-slip ở ZipExtractor VÀ ở CHECK constraint của DB.
      // Đây là lớp thứ ba. Bỏ qua kèm cảnh báo thay vì ném lỗi — một entry hỏng không nên làm
      // người dùng không xem được toàn bộ snapshot, nhưng cũng không được im lặng.
      console.warn('buildFileTree: bỏ qua đường dẫn không an toàn', entry.path);
      continue;
    }

    if (entry.entryType === 'DIRECTORY') {
      ensureDirectory(entry.path);
      continue;
    }

    const lastSlash = entry.path.lastIndexOf('/');
    const node: TreeNode = {
      path: entry.path,
      name: entry.name,
      type: 'FILE',
      children: [],
      file: entry,
    };
    nodesByPath.set(entry.path, node);

    if (lastSlash < 0) {
      rootChildren.push(node);
    } else {
      ensureDirectory(entry.path.slice(0, lastSlash)).children.push(node);
    }
  }

  const sorted = sortNodes(rootChildren);
  return collapse ? sorted.map(collapseChain) : sorted;
}

/** Thư mục trước file, rồi theo tên với so sánh có hiểu số (`v2` đứng trước `v10`). */
function sortNodes(nodes: TreeNode[]): TreeNode[] {
  for (const node of nodes) {
    node.children = sortNodes(node.children);
  }
  return [...nodes].sort((a, b) => {
    if (a.type !== b.type) {
      return a.type === 'DIRECTORY' ? -1 : 1;
    }
    return a.name.localeCompare(b.name, undefined, { numeric: true });
  });
}

/**
 * Gộp chuỗi thư mục chỉ có ĐÚNG MỘT thư mục con thành một dòng: `src/main/java/vn/hust/datn`.
 *
 * Không phải trang trí — repo Java điển hình có 5-6 tầng thư mục rỗng chỉ vì quy ước package, và
 * hiển thị mỗi tầng một dòng đẩy toàn bộ mã nguồn thật xuống dưới màn hình. Đây là bước HẬU XỬ LÝ
 * riêng, chạy sau khi cây đã dựng và sắp xong, để logic dựng cây không bị trộn với logic hiển thị.
 *
 * Chỉ gộp khi con duy nhất là THƯ MỤC: thư mục chứa đúng một file (`config/app.yml`) phải giữ
 * nguyên hai cấp, nếu không người dùng mất chỗ để bấm vào chính thư mục đó.
 */
function collapseChain(node: TreeNode): TreeNode {
  if (node.type !== 'DIRECTORY') {
    return node;
  }

  let current = node;
  const nameParts = [node.name];

  // `noUncheckedIndexedAccess` đang bật nên `children[0]` là `TreeNode | undefined` — lấy ra biến
  // riêng thay vì index hai lần, vừa thoả kiểu vừa khỏi đọc phần tử đầu tới hai lần mỗi vòng.
  let onlyChild = current.children.length === 1 ? current.children[0] : undefined;
  while (onlyChild !== undefined && onlyChild.type === 'DIRECTORY') {
    current = onlyChild;
    nameParts.push(current.name);
    onlyChild = current.children.length === 1 ? current.children[0] : undefined;
  }

  return {
    ...current,
    name: nameParts.join('/'),
    children: current.children.map(collapseChain),
  };
}

/** Đường dẫn phải là POSIX tương đối: không rỗng, không `/` đầu, không đoạn `..` hay rỗng. */
function isSafePath(path: string): boolean {
  if (path === '' || path.startsWith('/') || path.includes('\\')) {
    return false;
  }
  return path.split('/').every((segment) => segment !== '' && segment !== '..');
}
