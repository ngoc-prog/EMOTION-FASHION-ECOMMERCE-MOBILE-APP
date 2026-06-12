package com.example.emotioncommerce;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.emotioncommerce.data.AuthRepository;
import com.example.emotioncommerce.data.CartRepository;

public class MainActivity extends AppCompatActivity implements CartRepository.CartListener {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_navigation);

        // Restore remembered session so user stays logged in across app restarts
        SharedPreferences prefs = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(LoginActivity.KEY_REMEMBER, false)
                && !AuthRepository.getInstance().isLoggedIn()) {
            String email = prefs.getString(LoginActivity.KEY_EMAIL, "");
            String pass  = prefs.getString(LoginActivity.KEY_PASS, "");
            if (!email.isEmpty() && !pass.isEmpty()) {
                AuthRepository.getInstance().login(email, pass);
            }
        }

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                loadFragment(new HomeFragment());
            } else if (id == R.id.nav_products) {
                loadFragment(new ProductListFragment());
            } else if (id == R.id.nav_cart) {
                loadFragment(new CartFragment());
            } else if (id == R.id.nav_profile) {
                loadFragment(new ProfileFragment());
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        CartRepository.getInstance().addListener(this);
        refreshCartBadge();
    }

    @Override
    protected void onPause() {
        super.onPause();
        CartRepository.getInstance().removeListener(this);
    }

    @Override
    public void onCartChanged(int totalCount) {
        runOnUiThread(this::refreshCartBadge);
    }

    private void refreshCartBadge() {
        BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.nav_cart);
        int count = CartRepository.getInstance().getTotalCount();
        if (count > 0) {
            badge.setNumber(count);
            badge.setVisible(true);
        } else {
            badge.setVisible(false);
        }
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    public void switchToProductsTab() {
        bottomNav.setSelectedItemId(R.id.nav_products);
    }

    public void switchToProductsTab(String category) {
        Bundle args = new Bundle();
        args.putString("filter_category", category);
        ProductListFragment fragment = new ProductListFragment();
        fragment.setArguments(args);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        bottomNav.setSelectedItemId(R.id.nav_products);
    }

    public void switchToHomeTab() {
        bottomNav.setSelectedItemId(R.id.nav_home);
    }

    public void switchToCartTab() {
        bottomNav.setSelectedItemId(R.id.nav_cart);
    }
}
