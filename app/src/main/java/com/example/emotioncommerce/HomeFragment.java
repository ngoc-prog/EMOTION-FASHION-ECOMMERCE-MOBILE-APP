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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.emotioncommerce.data.DummyJsonRepository;
import com.example.emotioncommerce.data.MockProductData;
import com.example.emotioncommerce.data.WishlistRepository;
import com.example.emotioncommerce.model.Product;
import java.util.List;

public class HomeFragment extends Fragment {

    private static final String[] CATEGORIES = {
        "Tất cả", "Thời trang", "Phụ kiện", "Đồng hồ"
    };

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

    private void setupHeader(View view) {
        view.findViewById(R.id.btn_cart).setOnClickListener(v ->
            ((MainActivity) requireActivity()).switchToCartTab());

        view.findViewById(R.id.btn_search).setOnClickListener(v ->
            ((MainActivity) requireActivity()).switchToProductsTab());

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

        for (String category : CATEGORIES) {
            TextView chip = new TextView(requireContext());
            chip.setText(category);
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
                if (category.equals("Tất cả")) {
                    main.switchToProductsTab();
                } else {
                    main.switchToProductsTab(category);
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
            holder.tvPrice.setText(String.format("%,dđ", p.getPrice()));

            // Rating row
            if (p.getRating() > 0f && holder.layoutRating != null) {
                holder.tvRating.setText(String.format("%.1f", p.getRating()));
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
                btn.setColorFilter(ContextCompat.getColor(requireContext(), R.color.lume_primary));
            } else {
                btn.setImageResource(R.drawable.ic_heart);
                btn.setColorFilter(ContextCompat.getColor(requireContext(), R.color.lume_text_secondary));
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
