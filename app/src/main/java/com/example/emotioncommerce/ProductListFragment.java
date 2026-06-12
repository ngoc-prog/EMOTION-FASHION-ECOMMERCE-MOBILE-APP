package com.example.emotioncommerce;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.emotioncommerce.data.DummyJsonRepository;
import com.example.emotioncommerce.data.MockProductData;
import com.example.emotioncommerce.data.SessionAnalyticsRepository;
import com.example.emotioncommerce.data.WishlistRepository;
import com.example.emotioncommerce.model.Product;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductListFragment extends Fragment {

    private static final String[] CATEGORIES = {
        "Tất cả", "Thời trang", "Phụ kiện", "Đồng hồ"
    };

    private ProductAdapter adapter;
    private RecyclerView recyclerProducts;
    private ProgressBar progressLoading;
    private TextView tvProductCount;
    private TextView tvBoostHint;
    private View layoutNoResults;
    private List<Product> allProducts = new ArrayList<>();
    private String activeCategory = "Tất cả";
    private String searchQuery = "";
    private final List<TextView> chipViews = new ArrayList<>();
    private float dp;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_product_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dp = requireContext().getResources().getDisplayMetrics().density;

        Bundle args = getArguments();
        if (args != null && args.containsKey("filter_category")) {
            activeCategory = args.getString("filter_category", "Tất cả");
        }

        progressLoading  = view.findViewById(R.id.progress_loading);
        tvProductCount   = view.findViewById(R.id.tv_product_count);
        tvBoostHint      = view.findViewById(R.id.tv_boost_hint);
        layoutNoResults  = view.findViewById(R.id.layout_no_results);

        recyclerProducts = view.findViewById(R.id.recycler_products);
        recyclerProducts.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new ProductAdapter(new ArrayList<>());
        recyclerProducts.setAdapter(adapter);

        view.findViewById(R.id.btn_cart).setOnClickListener(v ->
            ((MainActivity) requireActivity()).switchToCartTab());

        setupSearch(view);
        setupFilterChips(view);
        loadProducts();
    }

    private void setupSearch(View view) {
        EditText etSearch = view.findViewById(R.id.et_search);
        ImageButton btnClear = view.findViewById(R.id.btn_clear_search);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim();
                btnClear.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                filterAndShow();
            }
        });

        btnClear.setOnClickListener(v -> {
            etSearch.setText("");
            etSearch.clearFocus();
        });
    }

    private void setupFilterChips(View view) {
        LinearLayout container = view.findViewById(R.id.filter_chips);

        for (String cat : CATEGORIES) {
            TextView chip = new TextView(requireContext());
            chip.setText(cat);
            chip.setTextSize(13f);
            updateChipStyle(chip, cat.equals(activeCategory));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd((int)(8 * dp));
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> {
                activeCategory = cat;
                for (TextView c : chipViews) {
                    updateChipStyle(c, c.getText().toString().equals(activeCategory));
                }
                filterAndShow();
            });

            chipViews.add(chip);
            container.addView(chip);
        }
    }

    private void updateChipStyle(TextView chip, boolean selected) {
        int hPad = (int)(14 * dp), vPad = (int)(7 * dp);
        chip.setPadding(hPad, vPad, hPad, vPad);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(20f * dp);
        if (selected) {
            bg.setColor(Color.parseColor("#8B6840"));
            chip.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.parseColor("#F0EAE0"));
            chip.setTextColor(Color.parseColor("#8B6840"));
        }
        chip.setBackground(bg);
    }

    private void loadProducts() {
        progressLoading.setVisibility(View.VISIBLE);
        DummyJsonRepository.fetchSkinCareProducts(requireContext(),
            new DummyJsonRepository.ProductsCallback() {
                @Override
                public void onSuccess(List<Product> products) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        progressLoading.setVisibility(View.GONE);
                        allProducts = products;
                        filterAndShow();
                    });
                }

                @Override
                public void onError(Exception e) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        progressLoading.setVisibility(View.GONE);
                        allProducts = MockProductData.getProducts();
                        filterAndShow();
                    });
                }
            });
    }

    private void filterAndShow() {
        List<Product> filtered = new ArrayList<>();
        String query = searchQuery.toLowerCase(Locale.getDefault());

        for (Product p : allProducts) {
            boolean matchCat = activeCategory.equals("Tất cả")
                    || p.getCategory().equals(activeCategory);
            boolean matchSearch = query.isEmpty()
                    || p.getName().toLowerCase(Locale.getDefault()).contains(query)
                    || p.getBrand().toLowerCase(Locale.getDefault()).contains(query);
            if (matchCat && matchSearch) filtered.add(p);
        }

        // Emotion-driven reorder: bump boosted categories to top (Tất cả view only)
        java.util.Set<String> boosted =
                SessionAnalyticsRepository.getInstance().getBoostedCategories();
        boolean reordered = !boosted.isEmpty() && activeCategory.equals("Tất cả");
        if (reordered) {
            filtered.sort((a, b) -> {
                boolean aB = boosted.contains(a.getCategory());
                boolean bB = boosted.contains(b.getCategory());
                if (aB == bB) return 0;
                return aB ? -1 : 1;
            });
        }

        updateBoostHint(reordered, boosted);

        boolean empty = filtered.isEmpty();
        recyclerProducts.setVisibility(empty ? View.GONE : View.VISIBLE);
        layoutNoResults.setVisibility(empty ? View.VISIBLE : View.GONE);

        adapter.setProducts(filtered);
        tvProductCount.setText(filtered.size() + " sản phẩm");
    }

    private void updateBoostHint(boolean active, java.util.Set<String> boosted) {
        if (!active) {
            tvBoostHint.setVisibility(View.GONE);
            return;
        }
        StringBuilder cats = new StringBuilder();
        for (String c : boosted) {
            if (cats.length() > 0) cats.append(" · ");
            cats.append(c);
        }
        tvBoostHint.setText("Gợi ý theo cảm xúc: " + cats);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(20f * dp);
        bg.setColor(Color.parseColor("#F0EAE0"));
        tvBoostHint.setBackground(bg);
        tvBoostHint.setVisibility(View.VISIBLE);
    }

    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
        private List<Product> products;

        ProductAdapter(List<Product> p) { this.products = new ArrayList<>(p); }

        void setProducts(List<Product> newProducts) {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return products.size(); }
                @Override public int getNewListSize() { return newProducts.size(); }
                @Override public boolean areItemsTheSame(int o, int n) {
                    return products.get(o).getId() == newProducts.get(n).getId();
                }
                @Override public boolean areContentsTheSame(int o, int n) {
                    return areItemsTheSame(o, n);
                }
            });
            products = new ArrayList<>(newProducts);
            result.dispatchUpdatesTo(this);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_product, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Product p = products.get(position);
            holder.tvName.setText(p.getName());
            holder.tvPrice.setText(String.format("%,dđ", p.getPrice()));
            holder.tvCategory.setText(p.getBrand());

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
            final TextView tvName, tvPrice, tvCategory, tvRating;
            final ImageButton btnWishlist;
            final View layoutRating;
            VH(View v) {
                super(v);
                ivImage      = v.findViewById(R.id.iv_product_image);
                tvName       = v.findViewById(R.id.tv_product_name);
                tvPrice      = v.findViewById(R.id.tv_product_price);
                tvCategory   = v.findViewById(R.id.tv_product_category);
                tvRating     = v.findViewById(R.id.tv_rating);
                btnWishlist  = v.findViewById(R.id.btn_wishlist);
                layoutRating = v.findViewById(R.id.layout_rating);
            }
        }
    }
}
