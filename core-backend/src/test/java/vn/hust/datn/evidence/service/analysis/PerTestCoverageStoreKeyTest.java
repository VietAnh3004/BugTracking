package vn.hust.datn.evidence.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tên method gốc dùng để ghép Surefire ({@code test_case}) với listener độ phủ từng test. */
class PerTestCoverageStoreKeyTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "ghi                         | ghi",
                "ghi(Path)                   | ghi",
                "lap(int)[2]                 | lap",
                "lap(int, String)[10]        | lap",
                "lap[1]                      | lap",
                "store_ghiRoiDocLai(Path)    | store_ghiRoiDocLai",
            })
    void boChuKyThamSoVaChiSoLanChay(String surefireName, String expected) {
        assertThat(PerTestCoverageStore.baseMethodName(surefireName.strip())).isEqualTo(expected.strip());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"app.FooTest | ghi(Path) | app.FooTest#ghi", "app.FooTest | ghi | app.FooTest#ghi"})
    void haiNguonCungRaMotKhoa(String className, String methodName, String expected) {
        assertThat(PerTestCoverageStore.testKey(className.strip(), methodName.strip())).isEqualTo(expected.strip());
    }
}
