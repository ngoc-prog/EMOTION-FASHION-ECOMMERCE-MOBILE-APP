package com.example.emotioncommerce;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.emotioncommerce.data.DummyJsonRepository;
import com.example.emotioncommerce.data.MockProductData;
import com.example.emotioncommerce.model.Product;
import java.util.ArrayList;
import java.util.List;

public class ProductListActivity extends AppCompatActivity {

    private static final String[] CATEGORIES = {
        "Tất cả", "Thời trang", "Phụ kiện", "Đồng hồ"
    };

    private ProductAdapter adapter;
    private ProgressBar progressLoading;
    private TextView tvProductCount;
    private List<Product> allProducts = new ArrayList<>();
    private String activeCategory = "Tất cả";
    private final List<TextView> chipViews = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_list);

        progressLoading = findViewById(R.id.progress_loading);
        tvProductCount  = findViewById(R.id.tv_product_count);

        RecyclerView recyclerView = findViewById(R.id.recycler_products);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new ProductAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        String filterFromHome = getIntent().getStringExtra("filter_category");
        if (filterFromHome != null) activeCategory = filterFromHome;

        setupHeader();
        setupFilterChips();
        loadProducts();
    }

    private void setupHeader() {
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        ImageButton btnCart = findViewById(R.id.btn_cart);
        btnCart.setOnClickListener(v ->
                startActivity(new Intent(this, CartActivity.class)));
    }

    private void setupFilterChips() {
        LinearLayout container = findViewById(R.id.filter_chips);
        float dp = getResources().getDisplayMetrics().density;

        for (String cat : CATEGORIES) {
            TextView chip = new TextView(this);
            chip.setText(cat);
            chip.setTextSize(13f);
            updateChipStyle(chip, cat.equals(activeCategory), dp);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd((int)(8 * dp));
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> {
                activeCategory = cat;
                for (TextView c : chipViews) {
                    updateChipStyle(c, c.getText().toString().equals(activeCategory), dp);
                }
                filterAndShow();
            });

            chipViews.add(chip);
            container.addView(chip);
        }
    }

    private void updateChipStyle(TextView chip, boolean selected, float dp) {
        int hPad = (int)(14 * dp);
        int vPad = (int)(7 * dp);
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
        DummyJsonRepository.fetchSkinCareProducts(this,
            new DummyJsonRepository.ProductsCallback() {
                @Override
                public void onSuccess(List<Product> products) {
                    runOnUiThread(() -> {
                        progressLoading.setVisibility(View.GONE);
                        allProducts = products;
                        filterAndShow();
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        progressLoading.setVisibility(View.GONE);
                        allProducts = MockProductData.getProducts();
                        filterAndShow();
                    });
                }
            });
    }

    private void filterAndShow() {
        List<Product> filtered;
        if (activeCategory.equals("Tất cả")) {
            filtered = allProducts;
        } else {
            filtered = new ArrayList<>();
            for (Product p : allProducts) {
                if (p.getCategory().equals(activeCategory)
                        || p.getBrand().equalsIgnoreCase(activeCategory)) {
                    filtered.add(p);
                }
            }
            if (filtered.isEmpty()) filtered = allProducts;
        }
        adapter.setProducts(filtered);
        tvProductCount.setText(filtered.size() + " sản phẩm");
    }

    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {
        private List<Product> products;

        ProductAdapter(List<Product> products) { this.products = products; }

        void setProducts(List<Product> products) {
            this.products = products;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_product, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Product product = products.get(position);
            holder.tvName.setText(product.getName());
            holder.tvPrice.setText(String.format("%,dđ", product.getPrice()));
            holder.tvCategory.setText(product.getBrand());

            if (!product.getImageUrl().isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(product.getImageUrl())
                        .placeholder(R.drawable.product_placeholder)
                        .centerCrop()
                        .into(holder.ivImage);
            } else {
                Glide.with(holder.itemView.getContext()).clear(holder.ivImage);
                holder.ivImage.setBackgroundResource(R.drawable.product_placeholder);
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ProductListActivity.this, ProductDetailActivity.class);
                intent.putExtra("product", product);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() { return products.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivImage;
            final TextView tvName, tvPrice, tvCategory;

            ViewHolder(View itemView) {
                super(itemView);
                ivImage    = itemView.findViewById(R.id.iv_product_image);
                tvName     = itemView.findViewById(R.id.tv_product_name);
                tvPrice    = itemView.findViewById(R.id.tv_product_price);
                tvCategory = itemView.findViewById(R.id.tv_product_category);
            }
        }
    }
}
