package com.dev.domain;

public record User(int id, int employee_id, String username, String password_hash, int role_id, UserStatus status) {};
