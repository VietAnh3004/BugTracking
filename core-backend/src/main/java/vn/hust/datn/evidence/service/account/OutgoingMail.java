package vn.hust.datn.evidence.service.account;

/** Một email văn bản thuần. Không HTML: không có gì để chèn mã, và đọc được trên mọi trình mail. */
public record OutgoingMail(String to, String subject, String body) {}
