package vn.hust.datn.casestudy.vulnshop.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Ca hai phuong thuc deu duoc test va deu xanh.
 *
 * <p>SpotBugs bao DM_CONVERT_CASE tai normalizeStatusCode. Test xanh o day la bang chung MAU THUAN
 * yeu: no cho thay voi locale mac dinh cua may chay test thi ket qua dung, nhung khong chung minh
 * duoc dieu do voi moi locale. AI Agent can nhan ra gioi han nay thay vi ket luan chac chan.
 */
class TextNormalizerTest {

    @Test
    void normalizeStatusCode_maAscii_chuyenThanhChuThuong() {
        assertEquals("shipped", TextNormalizer.normalizeStatusCode("SHIPPED"));
        assertEquals("cancelled", TextNormalizer.normalizeStatusCode("CANCELLED"));
    }

    @Test
    void normalizeUserInput_catKhoangTrangVaChuyenChuThuong() {
        assertEquals("nguyen van a", TextNormalizer.normalizeUserInput("  Nguyen Van A  "));
    }
}
