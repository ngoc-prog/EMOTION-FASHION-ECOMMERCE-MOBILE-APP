package com.example.emotioncommerce;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
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
import com.example.emotioncommerce.data.AppPrefs;
import com.example.emotioncommerce.data.CartRepository;
import com.example.emotioncommerce.data.DummyJsonRepository;
import com.example.emotioncommerce.data.MockProductData;
import com.example.emotioncommerce.data.SessionAnalyticsRepository;
import com.example.emotioncommerce.data.WishlistRepository;
import com.example.emotioncommerce.model.Product;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductListFragment extends Fragment implements CartRepository.CartListener {

    private TextView tvCartBadge;

    // Category chip keys: "" = all; mapped categories = parent group; raw DummyJSON slugs = sub-categories.
    // Filtering checks both product.getCategory() (mapped) and product.getRawCategory() (raw slug).
    private static final String[] CAT_KEYS = {
        "", "Thời trang", "womens-dresses", "womens-shoes",
        "Phụ kiện", "womens-bags", "womens-jewellery", "sunglasses",
        "Đồng hồ", "mens-watches", "womens-watches"
    };
    private static final String[] CAT_LABELS = {
        "Tất cả", "Thời trang", "Váy đầm", "Giày dép",
        "Phụ kiện", "Túi xách", "Trang sức", "Kính mắt",
        "Đồng hồ", "Đồng hồ nam", "Đồng hồ nữ"
    };

    // Sort options
    private static final int SORT_DEFAULT   = 0;
    private static final int SORT_PRICE_ASC = 1;
    private static final int SORT_PRICE_DESC= 2;
    private static final int SORT_RATING    = 3;

    // Price range options (min, max; -1 = no limit)
    private static final long[][] PRICE_RANGES = {
        {-1, -1},          // Tất cả
        {0, 500_000},      // Dưới 500K
        {500_000, 1_000_000},
        {1_000_000, 2_000_000},
        {2_000_000, -1},   // Trên 2 triệu
    };
    private static final String[] PRICE_LABELS = {
        "Tất cả", "Dưới 500K", "500K – 1 triệu", "1 – 2 triệu", "Trên 2 triệu"
    };

    // Rating filter: minimum stars (0 = no filter)
    private static final float[] RATING_MINS = {0f, 3f, 4f, 4.5f};
    private static final String[] RATING_LABELS = {"Tất cả", "3★ trở lên", "4★ trở lên", "4.5★ trở lên"};

    private ProductAdapter adapter;
    private RecyclerView recyclerProducts;
    private ProgressBar progressLoading;
    private TextView tvProductCount;
    private TextView tvBoostHint;
    private TextView tvFilterBadge;
    private View layoutNoResults;
    private List<Product> allProducts = new ArrayList<>();
    private String activeCategory = "";
    private String searchQuery    = "";
    private int    sortOrder      = SORT_DEFAULT;
    private int    priceRangeIdx  = 0;   // index into PRICE_RANGES
    private int    ratingIdx      = 0;   // index into RATING_MINS
    private final List<TextView> chipViews = new ArrayList<>();
    private float dp;
    private boolean mScrollRestored = false;

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
            activeCategory = args.getString("filter_category", "");
        }

        progressLoading  = view.findViewById(R.id.progress_loading);
        tvProductCount   = view.findViewById(R.id.tv_product_count);
        tvBoostHint      = view.findViewById(R.id.tv_boost_hint);
        tvFilterBadge    = view.findViewById(R.id.tv_filter_badge);
        tvCartBadge      = view.findViewById(R.id.tv_cart_badge);
        layoutNoResults  = view.findViewById(R.id.layout_no_results);

        recyclerProducts = view.findViewById(R.id.recycler_products);
        recyclerProducts.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new ProductAdapter(new ArrayList<>());
        recyclerProducts.setAdapter(adapter);
        mScrollRestored = false;

        android.widget.ImageButton btnScrollTop = view.findViewById(R.id.btn_scroll_top);
        btnScrollTop.setOnClickListener(v -> recyclerProducts.smoothScrollToPosition(0));
        recyclerProducts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            int total = 0;
            @Override
            public void onScrolled(@androidx.annotation.NonNull RecyclerView rv, int dx, int dy) {
                total += dy;
                if (total < 0) total = 0;
                if (total > 300) {
                    if (btnScrollTop.getVisibility() != android.view.View.VISIBLE) {
                        btnScrollTop.setVisibility(android.view.View.VISIBLE);
                        btnScrollTop.setAlpha(0f);
                        btnScrollTop.animate().alpha(1f).setDuration(200).start();
                    }
                } else {
                    if (btnScrollTop.getVisibility() == android.view.View.VISIBLE) {
                        btnScrollTop.animate().alpha(0f).setDuration(200)
                            .withEndAction(() -> btnScrollTop.setVisibility(android.view.View.GONE)).start();
                    }
                }
            }
        });

        view.findViewById(R.id.btn_cart).setOnClickListener(v ->
            ((MainActivity) requireActivity()).switchToCartTab());

        view.findViewById(R.id.btn_filter).setOnClickListener(v -> showFilterSheet());

        refreshCartBadge();

        setupSearch(view);
        setupFilterChips(view);
        loadProducts();
    }

    @Override
    public void onDestroyView() {
        if (recyclerProducts != null) {
            GridLayoutManager lm = (GridLayoutManager) recyclerProducts.getLayoutManager();
            if (lm != null) {
                int pos = lm.findFirstVisibleItemPosition();
                AppPrefs.saveScrollState("products", Math.max(0, pos), 0);
            }
        }
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();
        CartRepository.getInstance().addListener(this);
        refreshCartBadge();
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

        for (int i = 0; i < CAT_KEYS.length; i++) {
            String catKey = CAT_KEYS[i];
            TextView chip = new TextView(requireContext());
            chip.setText(CAT_LABELS[i]);
            chip.setTextSize(13f);
            updateChipStyle(chip, catKey.equals(activeCategory));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd((int)(8 * dp));
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> {
                activeCategory = catKey;
                for (int j = 0; j < chipViews.size(); j++) {
                    updateChipStyle(chipViews.get(j), CAT_KEYS[j].equals(activeCategory));
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

    private void showFilterSheet() {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View dv = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_product_filter, null);
        dialog.setContentView(dv);

        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT));
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                          ViewGroup.LayoutParams.WRAP_CONTENT);
            win.setGravity(Gravity.BOTTOM);
        }

        // Temp selections (don't commit until Apply)
        final int[] tempSort      = {sortOrder};
        final int[] tempPriceIdx  = {priceRangeIdx};
        final int[] tempRatingIdx = {ratingIdx};

        LinearLayout llSort   = dv.findViewById(R.id.ll_sort);
        LinearLayout llPrice  = dv.findViewById(R.id.ll_price);
        LinearLayout llRating = dv.findViewById(R.id.ll_rating);

        String[] sortLabels = {"Mặc định", "Giá thấp → cao", "Giá cao → thấp", "Đánh giá cao"};
        List<TextView> sortChips   = buildFilterChips(llSort,   sortLabels,   tempSort[0],     i -> { tempSort[0]      = i; });
        List<TextView> priceChips  = buildFilterChips(llPrice,  PRICE_LABELS, tempPriceIdx[0], i -> { tempPriceIdx[0]  = i; });
        List<TextView> ratingChips = buildFilterChips(llRating, RATING_LABELS,tempRatingIdx[0],i -> { tempRatingIdx[0] = i; });

        dv.findViewById(R.id.btn_reset).setOnClickListener(v -> {
            tempSort[0] = 0; tempPriceIdx[0] = 0; tempRatingIdx[0] = 0;
            refreshChipGroup(sortChips,   0);
            refreshChipGroup(priceChips,  0);
            refreshChipGroup(ratingChips, 0);
        });

        ((Button) dv.findViewById(R.id.btn_apply)).setOnClickListener(v -> {
            sortOrder     = tempSort[0];
            priceRangeIdx = tempPriceIdx[0];
            ratingIdx     = tempRatingIdx[0];
            updateFilterBadge();
            filterAndShow();
            dialog.dismiss();
        });

        dialog.show();
    }

    private List<TextView> buildFilterChips(LinearLayout container, String[] labels,
                                             int selectedIdx, java.util.function.IntConsumer onSelect) {
        container.removeAllViews();
        List<TextView> chips = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            TextView chip = makeFilterChip(labels[i], i == selectedIdx);
            final int idx = i;
            chip.setOnClickListener(v -> {
                onSelect.accept(idx);
                for (int j = 0; j < chips.size(); j++) {
                    setFilterChipSelected(chips.get(j), j == idx);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd((int)(8 * dp));
            lp.bottomMargin = (int)(8 * dp);
            chip.setLayoutParams(lp);
            container.addView(chip);
            chips.add(chip);
        }
        return chips;
    }

    private void refreshChipGroup(List<TextView> chips, int selectedIdx) {
        for (int i = 0; i < chips.size(); i++) setFilterChipSelected(chips.get(i), i == selectedIdx);
    }

    private TextView makeFilterChip(String label, boolean selected) {
        TextView chip = new TextView(requireContext());
        chip.setText(label);
        chip.setTextSize(13f);
        int hPad = (int)(12 * dp), vPad = (int)(7 * dp);
        chip.setPadding(hPad, vPad, hPad, vPad);
        setFilterChipSelected(chip, selected);
        return chip;
    }

    private void setFilterChipSelected(TextView chip, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(20f * dp);
        if (selected) {
            bg.setColor(Color.parseColor("#3D2B1F"));
            chip.setTextColor(Color.WHITE);
            chip.setTypeface(chip.getTypeface(), Typeface.BOLD);
        } else {
            bg.setColor(Color.parseColor("#F0EAE0"));
            chip.setTextColor(Color.parseColor("#8B6840"));
            chip.setTypeface(Typeface.DEFAULT);
        }
        chip.setBackground(bg);
    }

    private void updateFilterBadge() {
        int activeCount = (sortOrder != SORT_DEFAULT ? 1 : 0)
                        + (priceRangeIdx != 0 ? 1 : 0)
                        + (ratingIdx != 0 ? 1 : 0);
        if (tvFilterBadge == null) return;
        if (activeCount > 0) {
            tvFilterBadge.setVisibility(View.VISIBLE);
            tvFilterBadge.setText(String.valueOf(activeCount));
            // Tint filter button to show active state
        } else {
            tvFilterBadge.setVisibility(View.GONE);
        }
    }

    private void filterAndShow() {
        List<Product> filtered = new ArrayList<>();
        String query = searchQuery.toLowerCase(Locale.getDefault());
        long[] priceRange = PRICE_RANGES[priceRangeIdx];
        float  minRating  = RATING_MINS[ratingIdx];

        for (Product p : allProducts) {
            boolean matchCat    = activeCategory.isEmpty()
                    || p.getCategory().equals(activeCategory)
                    || p.getRawCategory().equals(activeCategory);
            boolean matchSearch = query.isEmpty()
                    || p.getName().toLowerCase(Locale.getDefault()).contains(query)
                    || p.getBrand().toLowerCase(Locale.getDefault()).contains(query);
            boolean matchPrice  = (priceRange[0] < 0 || p.getPrice() >= priceRange[0])
                               && (priceRange[1] < 0 || p.getPrice() <= priceRange[1]);
            boolean matchRating = minRating <= 0 || p.getRating() >= minRating;
            if (matchCat && matchSearch && matchPrice && matchRating) filtered.add(p);
        }

        // Sort
        switch (sortOrder) {
            case SORT_PRICE_ASC:  filtered.sort((a, b) -> Long.compare(a.getPrice(), b.getPrice())); break;
            case SORT_PRICE_DESC: filtered.sort((a, b) -> Long.compare(b.getPrice(), a.getPrice())); break;
            case SORT_RATING:     filtered.sort((a, b) -> Float.compare(b.getRating(), a.getRating())); break;
            default:
                // Emotion-driven reorder only when default sort
                java.util.Set<String> boosted =
                        SessionAnalyticsRepository.getInstance().getBoostedCategories();
                boolean reordered = !boosted.isEmpty() && activeCategory.isEmpty()
                        && priceRangeIdx == 0 && ratingIdx == 0;
                if (reordered) {
                    filtered.sort((a, b) -> {
                        boolean aB = boosted.contains(a.getCategory());
                        boolean bB = boosted.contains(b.getCategory());
                        if (aB == bB) return 0;
                        return aB ? -1 : 1;
                    });
                }
                updateBoostHint(reordered,
                        SessionAnalyticsRepository.getInstance().getBoostedCategories());
                break;
        }
        if (sortOrder != SORT_DEFAULT) updateBoostHint(false, new java.util.HashSet<>());

        boolean empty = filtered.isEmpty();
        recyclerProducts.setVisibility(empty ? View.GONE : View.VISIBLE);
        layoutNoResults.setVisibility(empty ? View.VISIBLE : View.GONE);

        adapter.setProducts(filtered);
        tvProductCount.setText(getString(R.string.products_count, filtered.size()));

        if (!mScrollRestored) {
            mScrollRestored = true;
            int savedPos = AppPrefs.getSavedScrollPos("products");
            if (savedPos > 0 && savedPos < filtered.size()) {
                recyclerProducts.post(() -> {
                    GridLayoutManager lm = (GridLayoutManager) recyclerProducts.getLayoutManager();
                    if (lm != null) lm.scrollToPositionWithOffset(savedPos, 0);
                });
            }
        }
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
        tvBoostHint.setText(getString(R.string.boost_hint, cats.toString()));

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
            holder.tvCategory.setText(p.getBrand());

            if (p.getDiscount() > 0) {
                holder.tvPrice.setText(getString(R.string.price_currency, p.getEffectivePrice()));
                holder.tvOriginalPrice.setText(getString(R.string.price_currency, p.getPrice()));
                holder.tvOriginalPrice.setPaintFlags(
                        holder.tvOriginalPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                holder.tvOriginalPrice.setVisibility(View.VISIBLE);
                holder.tvDiscountBadge.setText("-" + p.getDiscount() + "%");
                holder.tvDiscountBadge.setVisibility(View.VISIBLE);
            } else {
                holder.tvPrice.setText(getString(R.string.price_currency, p.getPrice()));
                holder.tvOriginalPrice.setVisibility(View.GONE);
                holder.tvDiscountBadge.setVisibility(View.GONE);
            }

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
                btn.setColorFilter(0xFFE53935); // red — universal "liked" signal
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
            final TextView tvName, tvPrice, tvCategory, tvRating, tvDiscountBadge, tvOriginalPrice;
            final ImageButton btnWishlist;
            final View layoutRating;
            VH(View v) {
                super(v);
                ivImage         = v.findViewById(R.id.iv_product_image);
                tvName          = v.findViewById(R.id.tv_product_name);
                tvPrice         = v.findViewById(R.id.tv_product_price);
                tvCategory      = v.findViewById(R.id.tv_product_category);
                tvRating        = v.findViewById(R.id.tv_rating);
                tvDiscountBadge = v.findViewById(R.id.tv_discount_badge);
                tvOriginalPrice = v.findViewById(R.id.tv_original_price);
                btnWishlist     = v.findViewById(R.id.btn_wishlist);
                layoutRating    = v.findViewById(R.id.layout_rating);
            }
        }
    }
}
