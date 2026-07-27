package vn.hust.datn.casestudy.vulnshop.order;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderStatusCheckerTest {

    /**
     * Test nay XANH du ham co loi - va do chinh la diem dang chu y.
     *
     * <p>Chuoi hang duoc JVM gop chung tham chieu, nen `==` vo tinh cho ket qua dung. Bang chung
     * kiem thu o day KHONG bac bo duoc canh bao cua SpotBugs; no chi cho thay duong di happy path
     * chua bi vo.
     *
     * <p>Truong hop nay quan trong voi de tai: no cho thay "co test phu va test xanh" KHONG dong
     * nghia voi "canh bao la duong tinh gia". AI Agent phai phan biet duoc hai dieu do, thay vi
     * ket luan false positive chi vi thay test mau xanh.
     */
    @Test
    @DisplayName("Chuoi hang: == vo tinh chay dung nho co che gop chuoi cua JVM")
    void isShipped_chuoiHang_traVeDung() {
        assertTrue(OrderStatusChecker.isShipped("SHIPPED"));
    }

    /**
     * TEST NAY CO TINH HONG.
     *
     * <p>Chuoi duoc tao luc chay (khong phai chuoi hang) nen khac tham chieu -> `==` tra ve false.
     * Day dung la tinh huong xay ra khi trang thai doc tu co so du lieu.
     *
     * <p>Sinh ra FAILED_TEST tro ve OrderStatusChecker.isShipped - bang chung ung ho canh bao
     * ES_COMPARING_STRINGS_WITH_EQ cua SpotBugs.
     */
    @Test
    @DisplayName("Chuoi tao luc chay: == that bai")
    void isShipped_chuoiTaoLucChay_vanPhaiTraVeDung() {
        String statusFromDatabase = new StringBuilder("SHIP").append("PED").toString();

        assertTrue(OrderStatusChecker.isShipped(statusFromDatabase));
    }

    @Test
    void isCancelled_soSanhDungCach() {
        String statusFromDatabase = new StringBuilder("CANCEL").append("LED").toString();

        assertTrue(OrderStatusChecker.isCancelled(statusFromDatabase));
        assertFalse(OrderStatusChecker.isCancelled("SHIPPED"));
    }
}
