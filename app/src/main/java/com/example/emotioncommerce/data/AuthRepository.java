package com.example.emotioncommerce.data;

import java.util.ArrayList;
import java.util.List;

public class AuthRepository {

    public enum Role { CUSTOMER, ADMIN }

    public enum LoginResult { SUCCESS_CUSTOMER, SUCCESS_ADMIN, WRONG_PASSWORD, NOT_FOUND }

    public enum RegisterResult { SUCCESS, EMAIL_EXISTS, WEAK_PASSWORD }

    // Public for serialisation in AppPrefs
    public static class UserRecord {
        public final String name;
        public final String email;
        public final String password;
        public final Role role;

        public UserRecord(String name, String email, String password, Role role) {
            this.name     = name;
            this.email    = email;
            this.password = password;
            this.role     = role;
        }

        boolean checkPassword(String pwd) { return password.equals(pwd); }
    }

    // Keep internal alias for existing code that used "User"
    static class User extends UserRecord {
        User(String name, String email, String password, Role role) {
            super(name, email, password, role);
        }
    }

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static AuthRepository instance;

    public static AuthRepository getInstance() {
        if (instance == null) instance = new AuthRepository();
        return instance;
    }

    private final List<UserRecord> users = new ArrayList<>();
    private UserRecord currentUser = null;

    private AuthRepository() {
        // Hardcoded demo accounts
        users.add(new UserRecord("Admin ÉLAN",      "admin@elan.vn", "elan@2025", Role.ADMIN));
        users.add(new UserRecord("Khách hàng Demo", "khach@elan.vn", "123456",    Role.CUSTOMER));

        // Load extra registered users from prefs
        List<UserRecord> saved = AppPrefs.loadRegisteredUsers();
        for (UserRecord u : saved) {
            boolean exists = false;
            for (UserRecord existing : users) {
                if (existing.email.equalsIgnoreCase(u.email)) { exists = true; break; }
            }
            if (!exists) users.add(u);
        }

        // Restore login session
        String savedEmail = AppPrefs.getLoggedInEmail();
        if (!savedEmail.isEmpty()) {
            for (UserRecord u : users) {
                if (u.email.equalsIgnoreCase(savedEmail)) { currentUser = u; break; }
            }
        }
    }

    // ── Auth operations ───────────────────────────────────────────────────────

    public LoginResult login(String email, String password) {
        String trimEmail = email.trim().toLowerCase();
        for (UserRecord u : users) {
            if (u.email.toLowerCase().equals(trimEmail)) {
                if (u.checkPassword(password)) {
                    currentUser = u;
                    AppPrefs.saveLoginSession(u.email);
                    CartRepository.getInstance().reload(u.email);
                    WishlistRepository.getInstance().reload(u.email);
                    OrderRepository.getInstance().reload(u.email);
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
        for (UserRecord u : users) {
            if (u.email.toLowerCase().equals(trimEmail)) return RegisterResult.EMAIL_EXISTS;
        }
        UserRecord newUser = new UserRecord(name.trim(), trimEmail, password, Role.CUSTOMER);
        users.add(newUser);
        currentUser = newUser;
        persistNonDefaultUsers();
        AppPrefs.saveLoginSession(newUser.email);
        CartRepository.getInstance().reload(newUser.email);
        WishlistRepository.getInstance().reload(newUser.email);
        OrderRepository.getInstance().reload(newUser.email);
        return RegisterResult.SUCCESS;
    }

    public void logout() {
        currentUser = null;
        AppPrefs.clearLoginSession();
        CartRepository.getInstance().reload("");
        WishlistRepository.getInstance().reload("");
        OrderRepository.getInstance().reload("");
    }

    // ── State queries ─────────────────────────────────────────────────────────

    public boolean isLoggedIn()  { return currentUser != null; }
    public boolean isAdmin()     { return currentUser != null && currentUser.role == Role.ADMIN; }
    public UserRecord getCurrentUser() { return currentUser; }

    public String getDisplayName() {
        return currentUser != null ? currentUser.name : "";
    }

    public String getAvatarLetter() {
        if (currentUser == null || currentUser.name.isEmpty()) return "K";
        return String.valueOf(currentUser.name.charAt(0)).toUpperCase();
    }

    public List<UserRecord> getUsers() {
        return new ArrayList<>(users);
    }

    // Save only non-hardcoded users (index 2+)
    private void persistNonDefaultUsers() {
        List<UserRecord> extra = new ArrayList<>();
        for (int i = 2; i < users.size(); i++) extra.add(users.get(i));
        AppPrefs.saveRegisteredUsers(extra);
    }
}
