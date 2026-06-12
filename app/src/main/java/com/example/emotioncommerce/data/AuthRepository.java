package com.example.emotioncommerce.data;

import java.util.ArrayList;
import java.util.List;

public class AuthRepository {

    public enum Role { CUSTOMER, ADMIN }

    public enum LoginResult { SUCCESS_CUSTOMER, SUCCESS_ADMIN, WRONG_PASSWORD, NOT_FOUND }

    public enum RegisterResult { SUCCESS, EMAIL_EXISTS, WEAK_PASSWORD }

    public static class User {
        public final String name;
        public final String email;
        private final String password;
        public final Role role;

        User(String name, String email, String password, Role role) {
            this.name     = name;
            this.email    = email;
            this.password = password;
            this.role     = role;
        }

        boolean checkPassword(String pwd) { return password.equals(pwd); }
    }

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static AuthRepository instance;

    public static AuthRepository getInstance() {
        if (instance == null) instance = new AuthRepository();
        return instance;
    }

    private final List<User> users = new ArrayList<>();
    private User currentUser = null;

    private AuthRepository() {
        // Hardcoded demo accounts
        users.add(new User("Admin ÉLAN",       "admin@elan.vn",  "elan@2025", Role.ADMIN));
        users.add(new User("Khách hàng Demo",  "khach@elan.vn",  "123456",    Role.CUSTOMER));
    }

    // ── Auth operations ───────────────────────────────────────────────────────

    public LoginResult login(String email, String password) {
        String trimEmail = email.trim().toLowerCase();
        for (User u : users) {
            if (u.email.toLowerCase().equals(trimEmail)) {
                if (u.checkPassword(password)) {
                    currentUser = u;
                    return u.role == Role.ADMIN ? LoginResult.SUCCESS_ADMIN
                                                : LoginResult.SUCCESS_CUSTOMER;
                } else {
                    return LoginResult.WRONG_PASSWORD;
                }
            }
        }
        return LoginResult.NOT_FOUND;
    }

    public RegisterResult register(String name, String email, String password) {
        if (password.length() < 6) return RegisterResult.WEAK_PASSWORD;
        String trimEmail = email.trim().toLowerCase();
        for (User u : users) {
            if (u.email.toLowerCase().equals(trimEmail)) return RegisterResult.EMAIL_EXISTS;
        }
        User newUser = new User(name.trim(), trimEmail, password, Role.CUSTOMER);
        users.add(newUser);
        currentUser = newUser;
        return RegisterResult.SUCCESS;
    }

    public void logout() {
        currentUser = null;
    }

    // ── State queries ─────────────────────────────────────────────────────────

    public boolean isLoggedIn()  { return currentUser != null; }
    public boolean isAdmin()     { return currentUser != null && currentUser.role == Role.ADMIN; }
    public User getCurrentUser() { return currentUser; }

    public String getDisplayName() {
        return currentUser != null ? currentUser.name : "Khách hàng";
    }

    public String getAvatarLetter() {
        if (currentUser == null || currentUser.name.isEmpty()) return "K";
        return String.valueOf(currentUser.name.charAt(0)).toUpperCase();
    }
}
