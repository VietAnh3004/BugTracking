package vn.hust.datn.casestudy.vulnshop.domain;

/** Tài khoản người dùng. */
public record User(long id, String username, String email, String passwordHash, String role) {}
