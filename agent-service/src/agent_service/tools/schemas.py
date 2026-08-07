"""Khai báo 5 evidence tool theo JSON Schema, dùng chung cho MỌI provider.

<p>Tên trường giữ nguyên snake_case của Evidence Tool API (ADR 0005): tool call mà LLM sinh ra
được forward NGUYÊN VĂN sang core-backend, không có tầng dịch tên nào ở giữa. Thêm một tầng
dịch là thêm một chỗ để sai, và làm hỏng chính luận điểm "5 tool là ranh giới kiến trúc thật,
đo được" của kế hoạch §1.

<p>``analysis_run_id`` KHÔNG nằm trong schema: nó do agent loop điền từ phạm vi hội thoại, không
do LLM chọn. Để LLM tự điền nghĩa là cho nó tự chọn phạm vi tra cứu bằng chứng — mà phạm vi đó
chính là thứ quyết định một Evidence ID có nghĩa hay không.
"""

from typing import Any, Final

# Mô tả tool là thứ LLM đọc để quyết định GỌI HAY KHÔNG. Mỗi mô tả nói rõ khi nào nên dùng và
# tool đó KHÔNG trả về gì — thiếu vế thứ hai thì model gọi lại nhiều lần để tìm thứ không có.
GET_WARNING: Final[dict[str, Any]] = {
    "name": "get_warning",
    "description": (
        "Lấy chi tiết một cảnh báo phân tích tĩnh theo Evidence ID: đường dẫn file, dòng, "
        "quy tắc, mức nghiêm trọng và câu mô tả gốc của công cụ. Gọi đầu tiên khi cần biết "
        "cảnh báo đang bàn tới là gì. KHÔNG trả về mã nguồn, test hay độ phủ — dùng các tool "
        "khác cho những thứ đó."
    ),
    "input_schema": {
        "type": "object",
        "properties": {
            "evidence_id": {
                "type": "string",
                "description": "Evidence ID dạng EV-XXXX. Không nhận UUID.",
            }
        },
        "required": ["evidence_id"],
    },
}

GET_CODE_CONTEXT: Final[dict[str, Any]] = {
    "name": "get_code_context",
    "description": (
        "Lấy đoạn mã nguồn bao quanh vị trí một bằng chứng — thường là method chứa dòng bị "
        "cảnh báo. Gọi khi cần đọc mã thật để phán đoán cảnh báo đúng hay sai. Trả về mảng "
        "rỗng nghĩa là KHÔNG có ngữ cảnh mã liên kết được, không phải là mã không tồn tại."
    ),
    "input_schema": {
        "type": "object",
        "properties": {
            "evidence_id": {"type": "string", "description": "Evidence ID dạng EV-XXXX."},
            "context_lines": {
                "type": "integer",
                "minimum": 0,
                "maximum": 50,
                "description": "Số dòng mở rộng thêm mỗi phía. Mặc định 5.",
            },
        },
        "required": ["evidence_id"],
    },
}

GET_RELATED_TESTS: Final[dict[str, Any]] = {
    "name": "get_related_tests",
    "description": (
        "Lấy các test liên quan tới một bằng chứng, kèm quan hệ (SUPPORTS = có bằng chứng ủng "
        "hộ cảnh báo, CONTRADICTS = có test xanh đi qua dòng đó) và độ tin cậy của liên kết. "
        "CẢNH BÁO: CONTRADICTS KHÔNG chứng minh cảnh báo sai — một test đường-đi-bình-thường "
        "vẫn xanh trên mã có lỗ hổng. Đọc cả `method` và `weight` trước khi kết luận."
    ),
    "input_schema": {
        "type": "object",
        "properties": {
            "evidence_id": {"type": "string", "description": "Evidence ID dạng EV-XXXX."},
            "relations": {
                "type": "array",
                "items": {
                    "type": "string",
                    "enum": ["SUPPORTS", "CONTRADICTS", "COVERS", "LOCATED_IN"],
                },
                "description": "Lọc theo quan hệ. Bỏ trống để lấy tất cả.",
            },
        },
        "required": ["evidence_id"],
    },
}

GET_COVERAGE: Final[dict[str, Any]] = {
    "name": "get_coverage",
    "description": (
        "Lấy thông tin độ phủ kiểm thử tại vị trí một bằng chứng: dòng đó có được chạy không, "
        "và chạy bao nhiêu lần. Gọi khi cần biết mã bị cảnh báo có thực sự được test đi qua "
        "hay không. Dòng KHÔNG được phủ không có nghĩa cảnh báo đúng, chỉ nghĩa là chưa ai kiểm."
    ),
    "input_schema": {
        "type": "object",
        "properties": {
            "evidence_id": {"type": "string", "description": "Evidence ID dạng EV-XXXX."}
        },
        "required": ["evidence_id"],
    },
}

SEARCH_EVIDENCE: Final[dict[str, Any]] = {
    "name": "search_evidence",
    "description": (
        "Tìm bằng chứng trong run theo từ khoá (khớp trên câu tóm tắt, đường dẫn file và tên "
        "symbol). Gọi khi chưa biết Evidence ID cần tra, hoặc khi muốn tìm cảnh báo tương tự ở "
        "chỗ khác. Đây là tìm kiếm theo chuỗi, KHÔNG phải tìm theo ngữ nghĩa."
    ),
    "input_schema": {
        "type": "object",
        "properties": {
            "query": {"type": "string", "description": "Từ khoá tìm kiếm."},
            "types": {
                "type": "array",
                "items": {
                    "type": "string",
                    "enum": [
                        "STATIC_WARNING",
                        "CODE_CONTEXT",
                        "TEST_RESULT",
                        "FAILED_TEST",
                        "STACK_TRACE",
                        "COVERAGE_INFORMATION",
                    ],
                },
                "description": "Lọc theo loại bằng chứng. Bỏ trống để lấy tất cả.",
            },
            "file_path": {"type": "string", "description": "Giới hạn trong một file."},
            "limit": {
                "type": "integer",
                "minimum": 1,
                "maximum": 50,
                "description": "Mặc định 20.",
            },
        },
        "required": ["query"],
    },
}

ALL_TOOLS: Final[list[dict[str, Any]]] = [
    GET_WARNING,
    GET_CODE_CONTEXT,
    GET_RELATED_TESTS,
    GET_COVERAGE,
    SEARCH_EVIDENCE,
]

TOOL_NAMES: Final[frozenset[str]] = frozenset(tool["name"] for tool in ALL_TOOLS)
