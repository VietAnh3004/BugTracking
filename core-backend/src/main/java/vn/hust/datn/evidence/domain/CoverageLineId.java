package vn.hust.datn.evidence.domain;

import java.io.Serializable;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Khoá chính tổ hợp của {@link CoverageLine}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CoverageLineId implements Serializable {

    private UUID evidenceRecordId;

    private int lineNumber;
}
