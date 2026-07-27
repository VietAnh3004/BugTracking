package vn.hust.datn.casestudy.vulnshop.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaginationHelperTest {

    @Test
    @DisplayName("So trang le duoc lam tron len")
    void totalPages_soLuongLe_lamTronLen() {
        assertEquals(3, PaginationHelper.totalPages(25, 10));
    }

    /**
     * TEST NAY CO TINH HONG.
     *
     * <p>20 phan tu, moi trang 10 -> dung ra la 2 trang, nhung cong thuc sai cho ra 3.
     *
     * <p>Sinh ra FAILED_TEST kem STACK_TRACE tro ve PaginationHelper.totalPages - bang chung
     * runtime cho mot loi ma phan tich tinh khong bat duoc.
     */
    @Test
    @DisplayName("So luong chia het khong duoc sinh them trang rong")
    void totalPages_chiaHet_khongCoTrangRong() {
        assertEquals(2, PaginationHelper.totalPages(20, 10));
    }

    @Test
    void totalPages_pageSizeKhongHopLe_nemNgoaiLe() {
        assertThrows(IllegalArgumentException.class, () -> PaginationHelper.totalPages(10, 0));
    }

    @Test
    void firstIndexOfPage_trangDauTien_batDauTuKhong() {
        assertEquals(0, PaginationHelper.firstIndexOfPage(1, 10));
        assertEquals(10, PaginationHelper.firstIndexOfPage(2, 10));
    }
}
