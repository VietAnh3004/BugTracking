/**
 * Cổng hạ tầng (adapter ra hệ thống ngoài — object storage), gói thứ chín ngoài 8 gói của §1bis.2
 * kế hoạch (controller/service/repository/domain/dto/mapper/config/exception).
 *
 * <p>Không nhét vào {@code service}: service không được biết gì về "MinIO" hay "S3", chỉ biết
 * {@link vn.hust.datn.evidence.storage.ObjectStorageClient} (interface thuần). Không nhét vào
 * {@code config}: đây không phải cấu hình bean, mà là logic thật (retry, đóng resource, dịch lỗi
 * SDK). Nhét lẫn vào một trong hai sẽ làm hỏng đúng ranh giới mà cấu trúc phân tầng của dự án
 * đang cố giữ.
 */
package vn.hust.datn.evidence.storage;
