package vn.hust.datn.evidence.domain;

import java.io.Serializable;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Khoá chính tổ hợp của {@link StackFrame}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class StackFrameId implements Serializable {

    private UUID evidenceRecordId;

    private int ordinal;
}
