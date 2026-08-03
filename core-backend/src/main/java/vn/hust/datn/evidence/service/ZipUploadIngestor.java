package vn.hust.datn.evidence.service;

import java.util.List;

import org.springframework.stereotype.Component;

import vn.hust.datn.evidence.config.properties.UploadProperties;
import vn.hust.datn.evidence.domain.SnapshotSourceType;
import vn.hust.datn.evidence.exception.InvalidArchiveException;

@Component
public class ZipUploadIngestor implements SnapshotSourceIngestor {

    private final ZipExtractor zipExtractor;

    public ZipUploadIngestor(ZipExtractor zipExtractor) {
        this.zipExtractor = zipExtractor;
    }

    @Override
    public SnapshotSourceType supportedType() {
        return SnapshotSourceType.ZIP_UPLOAD;
    }

    @Override
    public void validateFormat(byte[] rawContent) {
        if (!zipExtractor.looksLikeZip(rawContent)) {
            throw new InvalidArchiveException("File tải lên không phải archive zip (magic bytes không khớp)");
        }
    }

    @Override
    public List<ExtractedEntry> extractEntries(byte[] rawContent, UploadProperties limits) {
        return zipExtractor.extract(rawContent, limits);
    }
}
