package com.example.emotioncommerce;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.emotioncommerce.data.CartRepository;
import com.example.emotioncommerce.data.DummyJsonRepository;
import com.example.emotioncommerce.data.MockProductData;
import com.example.emotioncommerce.data.WishlistRepository;
import com.example.emotioncommerce.model.Product;
import java.util.List;

public class HomeFragment extends Fragment implements CartRepository.CartListener {

    private TextView tvCartBadge;

    // Internal category keys ("" = all). Matched against product.getCategory().
    // CAT_LABEL_RES must stay in sync with CAT_KEYS — add/remove entries together.
    private static final String[] CAT_KEYS      = {"", "Thời trang", "Phụ kiện", "Đồng hồ"};
    private static final int[]    CAT_LABEL_RES = {R.string.cat_all, R.string.cat_fashion, R.string.cat_accessories, R.string.cat_watches};

    private List<Product> allProducts;
    private View rootView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootView = view;

        setupHeader(view);
        setupCategoryChips(view);

        NestedScrollView sv = view.findViewById(R.id.home_scroll_content);
        ScrollTopHelper.attach(requireActivity(), sv, 72);

        DummyJsonRepository.fetchSkinCareProducts(requireContext(), new DummyJsonRepository.ProductsCallback() {
            @Override
            public void onSuccess(List<Product> products) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    allProducts = products;
                    setupFeaturedSection(rootView);
                    setupNewArrivalsSection(rootView);
                    rootView.findViewById(R.id.pb_home_loading).setVisibility(View.GONE);
                    rootView.findViewById(R.id.home_scroll_content).setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    allProducts = MockProductData.getProducts();
                    setupFeaturedSection(rootView);
                    setupNewArrivalsSection(rootView);
                    rootView.findViewById(R.id.pb_home_loading).setVisibility(View.GONE);
                    rootView.findViewById(R.id.home_scroll_content).setVisibility(View.VISIBLE);
                });
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        CartRepository.getInstance().addListener(this);
        refreshCartBadge();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ScrollTopHelper.detach(requireActivity());
    }

    @Override
    public void onStop() {
        super.onStop();
        CartRepository.getInstance().removeListener(this);
    }

    @Override
    public void onCartChanged(int totalCount) {
        if (isAdded()) requireActivity().runOnUiThread(this::refreshCartBadge);
    }

    private void refreshCartBadge() {
        if (tvCartBadge == null) return;
        int count = CartRepository.getInstance().getTotalCount();
        if (count > 0) {
            tvCartBadge.setVisibility(View.VISIBLE);
            tvCartBadge.setText(count > 99 ? "99+" : String.valueOf(count));
        } else {
            tvCartBadge.setVisibility(View.GONE);
        }
    }

    private void setupHeader(View view) {
        tvCartBadge = view.findViewById(R.id.tv_cart_badge);

        view.findViewById(R.id.btn_cart).setOnClickListener(v ->
            ((MainActivity) requireActivity()).switchToCartTab());

        view.findViewById(R.id.btn_search).setOnClickListener(v ->
            ((MainActivity) requireActivity()).switchToProductsTab());

        view.findViewById(R.id.btn_notifications).setOnClickListener(v ->
            startActivity(new Intent(requireContext(), NotificationsActivity.class)));

        view.findViewById(R.id.btn_hero_explore).setOnClickListener(v ->
            ((MainActivity) requireActivity()).switchToProductsTab());

        view.findViewById(R.id.btn_see_all).setOnClickListener(v ->
            ((MainActivity) requireActivity()).switchToProductsTab());

        view.findViewById(R.id.btn_see_all_new).setOnClickListener(v ->
            ((MainActivity) requireActivity()).switchToProductsTab());
    }

    private void setupCategoryChips(View view) {
        LinearLayout container = view.findViewById(R.id.category_chips);
        float dp = requireContext().getResources().getDisplayMetrics().density;
        for (int i = 0; i < CAT_KEYS.length; i++) {
            String catKey = CAT_KEYS[i];
            TextView chip = new TextView(requireContext());
            chip.setText(getString(CAT_LABEL_RES[i]));
            chip.setTextSize(13f);
            chip.setTextColor(Color.parseColor("#8B6840"));

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(20f * dp);
            bg.setColor(Color.parseColor("#F0EAE0"));
            chip.setBackground(bg);

            int hPad = (int)(16 * dp), vPad = (int)(8 * dp);
            chip.setPadding(hPad, vPad, hPad, vPad);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd((int)(8 * dp));
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> {
                MainActivity main = (MainActivity) requireActivity();
                if (catKey.isEmpty()) {
                    main.switchToProductsTab();
                } else {
                    main.switchToProductsTab(catKey);
                }
            });

            container.addView(chip);
        }
    }

    private void setupFeaturedSection(View view) {
        List<Product> featured = allProducts.subList(0, Math.min(6, allProducts.size()));
        RecyclerView recycler = view.findViewById(R.id.recycler_featured);
        recycler.setLayoutManager(
            new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        recycler.setAdapter(new HorizontalAdapter(featured));
    }

    private void setupNewArrivalsSection(View view) {
        int start = Math.min(6, allProducts.size());
        int end   = Math.min(12, allProducts.size());
        RecyclerView recycler = view.findViewById(R.id.recycler_new);
        recycler.setLayoutManager(
            new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        recycler.setAdapter(new HorizontalAdapter(allProducts.subList(start, end)));
    }

    private class HorizontalAdapter extends RecyclerView.Adapter<HorizontalAdapter.VH> {
        private final List<Product> products;

        HorizontalAdapter(List<Product> products) { this.products = products; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_product_horizontal, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Product p = products.get(position);
            holder.tvName.setText(p.getName());
            holder.tvBrand.setText(p.getBrand());
            holder.tvPrice.setText(getString(R.string.price_currency, p.getPrice()));

            // Rating row
            if (p.getRating() > 0f && holder.layoutRating != null) {
                holder.tvRating.setText(String.format(java.util.Locale.getDefault(), "%.1f", p.getRating()));
                holder.layoutRating.setVisibility(View.VISIBLE);
            } else if (holder.layoutRating != null) {
                holder.layoutRating.setVisibility(View.GONE);
            }

            Glide.with(holder.itemView.getContext())
                    .load(p.getImageUrl().isEmpty() ? null : p.getImageUrl())
                    .placeholder(R.drawable.product_placeholder)
                    .error(R.drawable.product_placeholder)
                    .centerCrop()
                    .into(holder.ivImage);

            // Wishlist button (A1)
            bindWishlistBtn(holder.btnWishlist, p);
            holder.btnWishlist.setOnClickListener(v -> {
                WishlistRepository.getInstance().toggle(p);
                bindWishlistBtn(holder.btnWishlist, p);
            });

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
                intent.putExtra("product", p);
                startActivity(intent);
            });
        }

        private void bindWishlistBtn(ImageButton btn, Product p) {
            boolean wished = WishlistRepository.getInstance().isWishlisted(p.getId());
            if (wished) {
                btn.setImageResource(R.drawable.ic_heart_filled);
                btn.setColorFilter(0xFFE53935);
                btn.setBackgroundResource(R.drawable.bg_wishlist_active);
            } else {
                btn.setImageResource(R.drawable.ic_heart);
                btn.setColorFilter(0xFFBBAA99);
                btn.setBackgroundResource(R.drawable.bg_circle_white);
            }
        }

        @Override
        public int getItemCount() { return products.size(); }

        class VH extends RecyclerView.ViewHolder {
            final ImageView ivImage;
            final TextView tvName, tvBrand, tvPrice, tvRating;
            final ImageButton btnWishlist;
            final View layoutRating;
            VH(View v) {
                super(v);
                ivImage     = v.findViewById(R.id.iv_product_image);
                tvName      = v.findViewById(R.id.tv_product_name);
                tvBrand     = v.findViewById(R.id.tv_product_brand);
                tvPrice     = v.findViewById(R.id.tv_product_price);
                tvRating    = v.findViewById(R.id.tv_rating);
                btnWishlist = v.findViewById(R.id.btn_wishlist);
                layoutRating = v.findViewById(R.id.layout_rating);
            }
        }
    }
}
