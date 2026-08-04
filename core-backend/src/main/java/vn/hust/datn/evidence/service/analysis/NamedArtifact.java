package vn.hust.datn.evidence.service.analysis;

/**
 * Một artifact analyzer kèm tên gốc, chưa nhận dạng định dạng.
 *
 * <p>{@code name} CHỈ để đưa vào thông điệp lỗi cho người vận hành truy ngược — việc chọn parser
 * hoàn toàn dựa vào NỘI DUNG ({@link AnalyzerRegistry#detect}). Tin vào tên file thì người dùng
 * đặt nhầm tên hai phần multipart sẽ khiến SpotBugs được đưa cho parser Semgrep, và hỏng ở một
 * chỗ chẳng liên quan gì tới nguyên nhân.
 */
public record NamedArtifact(String name, byte[] content) {}
