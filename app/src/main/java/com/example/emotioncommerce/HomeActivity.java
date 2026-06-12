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
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.emotioncommerce.data.MockProductData;
import com.example.emotioncommerce.model.Product;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private static final String[] CATEGORIES = {
        "Tất cả", "Thời trang", "Phụ kiện", "Đồng hồ"
    };

    private List<Product> allProducts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        allProducts = MockProductData.getProducts();

        setupHeader();
        setupCategoryChips();
        setupFeaturedSection();
        setupNewArrivalsSection();
    }

    private void setupHeader() {
        ImageButton btnCart = findViewById(R.id.btn_cart);
        btnCart.setOnClickListener(v ->
                startActivity(new Intent(this, CartActivity.class)));

        ImageButton btnSearch = findViewById(R.id.btn_search);
        btnSearch.setOnClickListener(v ->
                startActivity(new Intent(this, ProductListActivity.class)));

        findViewById(R.id.btn_hero_explore).setOnClickListener(v ->
                startActivity(new Intent(this, ProductListActivity.class)));

        findViewById(R.id.btn_see_all).setOnClickListener(v ->
                startActivity(new Intent(this, ProductListActivity.class)));

        findViewById(R.id.btn_see_all_new).setOnClickListener(v ->
                startActivity(new Intent(this, ProductListActivity.class)));
    }

    private void setupCategoryChips() {
        LinearLayout chipsContainer = findViewById(R.id.category_chips);
        float dp = getResources().getDisplayMetrics().density;

        for (String category : CATEGORIES) {
            TextView chip = new TextView(this);
            chip.setText(category);
            chip.setTextSize(13f);
            chip.setTextColor(Color.parseColor("#8B6840"));

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(20f * dp);
            bg.setColor(Color.parseColor("#F0EAE0"));
            chip.setBackground(bg);

            int hPad = (int)(16 * dp);
            int vPad = (int)(8 * dp);
            chip.setPadding(hPad, vPad, hPad, vPad);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd((int)(8 * dp));
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> {
                Intent intent = new Intent(this, ProductListActivity.class);
                if (!category.equals("Tất cả")) {
                    intent.putExtra("filter_category", category);
                }
                startActivity(intent);
            });

            chipsContainer.addView(chip);
        }
    }

    private void setupFeaturedSection() {
        List<Product> featured = allProducts.subList(0, Math.min(6, allProducts.size()));
        RecyclerView recycler = findViewById(R.id.recycler_featured);
        recycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recycler.setAdapter(new HorizontalProductAdapter(featured));
    }

    private void setupNewArrivalsSection() {
        int start = Math.min(6, allProducts.size());
        int end = Math.min(12, allProducts.size());
        List<Product> newArrivals = allProducts.subList(start, end);
        RecyclerView recycler = findViewById(R.id.recycler_new);
        recycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recycler.setAdapter(new HorizontalProductAdapter(newArrivals));
    }

    private class HorizontalProductAdapter
            extends RecyclerView.Adapter<HorizontalProductAdapter.VH> {

        private final List<Product> products;

        HorizontalProductAdapter(List<Product> products) {
            this.products = products;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_product_horizontal, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Product product = products.get(position);
            holder.tvName.setText(product.getName());
            holder.tvBrand.setText(product.getBrand());
            holder.tvPrice.setText(String.format("%,dđ", product.getPrice()));

            if (!product.getImageUrl().isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(product.getImageUrl())
                        .placeholder(R.drawable.product_placeholder)
                        .centerCrop()
                        .into(holder.ivImage);
            } else {
                Glide.with(holder.itemView.getContext()).clear(holder.ivImage);
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, ProductDetailActivity.class);
                intent.putExtra("product", product);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() { return products.size(); }

        class VH extends RecyclerView.ViewHolder {
            final ImageView ivImage;
            final TextView tvName, tvBrand, tvPrice;

            VH(View itemView) {
                super(itemView);
                ivImage  = itemView.findViewById(R.id.iv_product_image);
                tvName   = itemView.findViewById(R.id.tv_product_name);
                tvBrand  = itemView.findViewById(R.id.tv_product_brand);
                tvPrice  = itemView.findViewById(R.id.tv_product_price);
            }
        }
    }
}
