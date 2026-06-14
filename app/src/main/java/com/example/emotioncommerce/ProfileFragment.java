package com.example.emotioncommerce;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.emotioncommerce.data.AuthRepository;
import com.example.emotioncommerce.data.CartRepository;
import com.example.emotioncommerce.data.WishlistRepository;

public class ProfileFragment extends Fragment
        implements CartRepository.CartListener, WishlistRepository.WishlistListener {

    private TextView tvAvatarLetter, tvProfileName, tvProfileSubtitle;
    private TextView tvStatCart, tvStatWishlist;
    private View btnAdminAnalytics, btnResearchMode, btnProfileLogin, btnLogout;

    private final ActivityResultLauncher<Intent> loginLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                refreshProfileUI();
            }
        });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvAvatarLetter   = view.findViewById(R.id.tv_avatar_letter);
        tvProfileName    = view.findViewById(R.id.tv_profile_name);
        tvProfileSubtitle = view.findViewById(R.id.tv_profile_subtitle);
        tvStatCart       = view.findViewById(R.id.tv_stat_cart);
        tvStatWishlist   = view.findViewById(R.id.tv_stat_wishlist);
        btnAdminAnalytics = view.findViewById(R.id.btn_admin_analytics);
        btnResearchMode  = view.findViewById(R.id.btn_research_mode);
        btnProfileLogin  = view.findViewById(R.id.btn_profile_login);
        btnLogout        = view.findViewById(R.id.btn_logout);

        btnProfileLogin.setOnClickListener(v ->
            loginLauncher.launch(new Intent(requireContext(), LoginActivity.class)));

        btnAdminAnalytics.setOnClickListener(v ->
            startActivity(new Intent(requireContext(), AdminAnalyticsActivity.class)));

        btnResearchMode.setOnClickListener(v ->
            startActivity(new Intent(requireContext(), EvaluationActivity.class)));

        btnLogout.setOnClickListener(v ->
            new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.logout))
                .setMessage(getString(R.string.logout_confirm))
                .setPositiveButton(getString(R.string.logout), (d, w) -> {
                    AuthRepository.getInstance().logout();
                    Toast.makeText(requireContext(), getString(R.string.logged_out), Toast.LENGTH_SHORT).show();
                    ((MainActivity) requireActivity()).switchToHomeTab();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        );

        setupRow(view, R.id.row_orders,        R.drawable.ic_cart,     getString(R.string.row_orders));
        setupRow(view, R.id.row_wishlist,      R.drawable.ic_heart,    getString(R.string.row_wishlist));
        setupRow(view, R.id.row_address,       R.drawable.ic_location, getString(R.string.row_address));
        setupRow(view, R.id.row_notifications, R.drawable.ic_bell,     getString(R.string.row_notifications));
        setupRow(view, R.id.row_about,         R.drawable.ic_info,     getString(R.string.row_about));

        refreshProfileUI();
    }

    @Override
    public void onStart() {
        super.onStart();
        CartRepository.getInstance().addListener(this);
        WishlistRepository.getInstance().addListener(this);
        refreshProfileUI();
    }

    @Override
    public void onStop() {
        super.onStop();
        CartRepository.getInstance().removeListener(this);
        WishlistRepository.getInstance().removeListener(this);
    }

    @Override
    public void onCartChanged(int totalCount) {
        if (isAdded()) requireActivity().runOnUiThread(this::refreshStats);
    }

    @Override
    public void onWishlistChanged(int totalCount) {
        if (isAdded()) requireActivity().runOnUiThread(this::refreshStats);
    }

    private void refreshProfileUI() {
        AuthRepository auth = AuthRepository.getInstance();

        if (!auth.isLoggedIn()) {
            // Guest state
            tvAvatarLetter.setText("K");
            tvProfileName.setText(getString(R.string.guest));
            tvProfileSubtitle.setText(getString(R.string.login_for_full));
            btnProfileLogin.setVisibility(View.VISIBLE);
            btnAdminAnalytics.setVisibility(View.GONE);
            btnResearchMode.setVisibility(View.GONE);
            btnLogout.setVisibility(View.GONE);
        } else if (auth.isAdmin()) {
            // Admin state
            tvAvatarLetter.setText("A");
            tvProfileName.setText(auth.getDisplayName());
            tvProfileSubtitle.setText(getString(R.string.admin_subtitle));
            btnProfileLogin.setVisibility(View.GONE);
            btnAdminAnalytics.setVisibility(View.VISIBLE);
            btnResearchMode.setVisibility(View.VISIBLE);
            btnLogout.setVisibility(View.VISIBLE);
        } else {
            // Customer state
            tvAvatarLetter.setText(auth.getAvatarLetter());
            tvProfileName.setText(auth.getDisplayName());
            tvProfileSubtitle.setText(getString(R.string.member_subtitle));
            btnProfileLogin.setVisibility(View.GONE);
            btnAdminAnalytics.setVisibility(View.GONE);
            btnResearchMode.setVisibility(View.GONE);
            btnLogout.setVisibility(View.VISIBLE);
        }

        refreshStats();
    }

    private void refreshStats() {
        if (tvStatCart != null) {
            tvStatCart.setText(String.valueOf(CartRepository.getInstance().getTotalCount()));
        }
        if (tvStatWishlist != null) {
            tvStatWishlist.setText(String.valueOf(WishlistRepository.getInstance().getCount()));
        }
    }

    private void setupRow(View parent, int rowId, int iconRes, String title) {
        View row = parent.findViewById(rowId);
        ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.row_title)).setText(title);
        row.setOnClickListener(v -> {
            if (!AuthRepository.getInstance().isLoggedIn() && rowId == R.id.row_orders) {
                loginLauncher.launch(new Intent(requireContext(), LoginActivity.class));
            } else {
                Toast.makeText(requireContext(), title, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
