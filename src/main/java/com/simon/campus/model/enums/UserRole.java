package com.simon.campus.model.enums;

public enum UserRole {
    STUDENT, TEACHER, ADMIN;

    public static boolean isValid(String role) {
        if (role == null) return false;
        for (UserRole r : values()) {
            if (r.name().equalsIgnoreCase(role)) return true;
        }
        return false;
    }
}
