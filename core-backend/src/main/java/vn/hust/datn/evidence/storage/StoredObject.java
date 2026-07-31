package vn.hust.datn.evidence.storage;

/** Kết quả đọc một object: nội dung + metadata tối thiểu cần cho tầng gọi. */
public record StoredObject(byte[] content, String contentType) {}
