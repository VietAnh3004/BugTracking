/**
 * Cây file của snapshot. Trạng thái "thư mục nào đang mở" nằm ở component này, và component này
 * được render bởi LAYOUT cha (`SnapshotBrowserLayout`) — nhờ vậy đổi file không unmount cây, giữ
 * nguyên thư mục đang mở lẫn vị trí cuộn. Đó là lý do kỹ thuật để cây nằm ở layout chứ không nằm
 * trong từng trang.
 */

import { useEffect, useState } from 'react';
import { NavLink } from 'react-router-dom';

import { toFilePathUrl } from '@/features/snapshots/lib/filePathUrl';

import type { TreeNode } from '@/features/snapshots/lib/buildFileTree';

interface FileTreeProps {
  nodes: TreeNode[];
  snapshotId: string;
  /** Đường dẫn file đang mở — dùng để tự bung các thư mục cha khi mở deep link ở tab mới. */
  activePath: string;
}

export function FileTree({ nodes, snapshotId, activePath }: FileTreeProps) {
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  // Bung sẵn đường dẫn tới file đang mở. CHỈ THÊM, không bao giờ xoá khỏi tập — nếu tính lại toàn
  // bộ theo `activePath` thì thao tác gập/mở của người dùng bị ghi đè mỗi lần đổi file.
  useEffect(() => {
    if (activePath === '') {
      return;
    }
    setExpanded((current) => {
      const next = new Set(current);
      const segments = activePath.split('/');
      for (let i = 1; i < segments.length; i++) {
        next.add(segments.slice(0, i).join('/'));
      }
      return next;
    });
  }, [activePath]);

  function toggle(path: string) {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(path)) {
        next.delete(path);
      } else {
        next.add(path);
      }
      return next;
    });
  }

  if (nodes.length === 0) {
    return <p className="text-ink-muted text-sm">Snapshot này không có file nào.</p>;
  }

  return (
    <ul className="m-0 list-none p-0">
      {nodes.map((node) => (
        <TreeItem
          key={node.path}
          node={node}
          snapshotId={snapshotId}
          depth={0}
          expanded={expanded}
          onToggle={toggle}
        />
      ))}
    </ul>
  );
}

interface TreeItemProps {
  node: TreeNode;
  snapshotId: string;
  depth: number;
  expanded: Set<string>;
  onToggle: (path: string) => void;
}

function TreeItem({ node, snapshotId, depth, expanded, onToggle }: TreeItemProps) {
  // Thụt lề bằng padding nội tuyến chứ không phải class Tailwind ghép động (`pl-${n}`): class ghép
  // chuỗi không được scanner sinh ra, xem `src/index.css`.
  const indent = { paddingLeft: `${String(depth * 12)}px` };

  if (node.type === 'DIRECTORY') {
    const isOpen = expanded.has(node.path);
    return (
      <li>
        <button
          type="button"
          onClick={() => onToggle(node.path)}
          aria-expanded={isOpen}
          className="hover:bg-surface-muted flex w-full cursor-pointer items-center gap-1 rounded px-1 py-0.5 text-left text-sm"
          style={indent}
        >
          <span aria-hidden="true" className="text-ink-muted">
            {isOpen ? '▾' : '▸'}
          </span>
          <span className="truncate font-medium">{node.name}</span>
        </button>

        {isOpen ? (
          <ul className="m-0 list-none p-0">
            {node.children.map((child) => (
              <TreeItem
                key={child.path}
                node={child}
                snapshotId={snapshotId}
                depth={depth + 1}
                expanded={expanded}
                onToggle={onToggle}
              />
            ))}
          </ul>
        ) : null}
      </li>
    );
  }

  return (
    <li>
      <NavLink
        to={`/snapshots/${snapshotId}/files/${toFilePathUrl(node.path)}`}
        style={indent}
        className={({ isActive }) =>
          `block truncate rounded px-1 py-0.5 text-sm ${
            isActive ? 'bg-brand-subtle text-brand font-medium' : 'hover:bg-surface-muted'
          }`
        }
      >
        {/* Đánh dấu file không đọc được ngay trên cây để người dùng biết trước khi bấm. */}
        {node.name}
        {node.file?.binary === true ? <span className="text-ink-muted"> (nhị phân)</span> : null}
      </NavLink>
    </li>
  );
}
