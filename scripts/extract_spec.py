"""按文档顺序导出《需求规格说明书》的段落与表格，供追溯矩阵核对。

用法：
    python scripts/extract_spec.py [docx路径] [输出路径]

默认读取仓库根目录的《ICPC实验室管理系统需求规格说明书.docx》，
输出到 .cache/spec_dump.txt。该脚本只读 docx，不修改源文件。
"""

from __future__ import annotations

import sys
from pathlib import Path

from docx import Document
from docx.table import Table
from docx.text.paragraph import Paragraph


def iter_block_items(parent):
    """按 body 中的真实顺序产出段落与表格。"""
    from docx.oxml.ns import qn

    body = parent.element.body
    for child in body.iterchildren():
        if child.tag == qn("w:p"):
            yield Paragraph(child, parent)
        elif child.tag == qn("w:tbl"):
            yield Table(child, parent)


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else root / "ICPC实验室管理系统需求规格说明书.docx"
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else root / ".cache" / "spec_dump.txt"

    if not src.exists():
        print(f"源文件不存在: {src}", file=sys.stderr)
        return 1

    out.parent.mkdir(parents=True, exist_ok=True)
    doc = Document(str(src))
    lines: list[str] = []

    for block in iter_block_items(doc):
        if isinstance(block, Paragraph):
            text = block.text.strip()
            if not text:
                continue
            style = block.style.name if block.style is not None else ""
            prefix = f"[{style}] " if style and style != "Normal" else ""
            lines.append(f"{prefix}{text}")
        else:
            lines.append("[TABLE]")
            for row in block.rows:
                cells = [" ".join(c.text.split()) for c in row.cells]
                lines.append(" | ".join(cells))
            lines.append("[/TABLE]")

    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"已导出 {len(lines)} 行 -> {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())