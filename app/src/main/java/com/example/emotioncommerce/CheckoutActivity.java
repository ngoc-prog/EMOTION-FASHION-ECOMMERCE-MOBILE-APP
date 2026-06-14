package com.example.emotioncommerce;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.emotioncommerce.data.AuthRepository;
import com.example.emotioncommerce.data.CartRepository;
import java.util.List;

public class CheckoutActivity extends AppCompatActivity {

    private EditText etName, etPhone, etAddress;
    private TextView tvCityLabel;
    private View cityPicker;
    private RadioGroup rgPayment;
    private TextView tvBottomTotal;
    private RecyclerView recyclerOrderItems;

    private String selectedProvince = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        etName         = findViewById(R.id.et_checkout_name);
        etPhone        = findViewById(R.id.et_checkout_phone);
        etAddress      = findViewById(R.id.et_checkout_address);
        cityPicker     = findViewById(R.id.et_checkout_city);
        tvCityLabel    = findViewById(R.id.tv_city_label);
        rgPayment      = findViewById(R.id.rg_payment);
        tvBottomTotal  = findViewById(R.id.tv_checkout_total);
        recyclerOrderItems = findViewById(R.id.recycler_order_items);

        // Pre-fill name from profile
        String displayName = AuthRepository.getInstance().getDisplayName();
        if (!TextUtils.isEmpty(displayName) && !displayName.equals(getString(R.string.guest))) {
            etName.setText(displayName);
        }

        // Province picker
        cityPicker.setOnClickListener(v -> showProvincePicker());

        // Setup items list
        CartRepository cart = CartRepository.getInstance();
        List<CartRepository.CartItem> items = cart.getItems();

        recyclerOrderItems.setLayoutManager(new LinearLayoutManager(this));
        recyclerOrderItems.setNestedScrollingEnabled(false);
        recyclerOrderItems.setAdapter(new OrderItemsAdapter(items));

        // Order summary
        ((TextView) findViewById(R.id.tv_order_count))
                .setText(getString(R.string.order_with_count, cart.getTotalCount()));

        long subtotal = cart.getTotalPrice();
        long shipping = subtotal > 500_000 ? 0 : 30_000;
        long total    = subtotal + shipping;

        ((TextView) findViewById(R.id.tv_subtotal))
                .setText(getString(R.string.price_currency, subtotal));
        ((TextView) findViewById(R.id.tv_shipping_fee))
                .setText(shipping == 0 ? getString(R.string.free) : getString(R.string.price_currency, shipping));
        tvBottomTotal.setText(getString(R.string.total_fmt, total));

        findViewById(R.id.btn_back_checkout).setOnClickListener(v -> finish());

        final long orderTotal = total;
        findViewById(R.id.btn_place_order).setOnClickListener(v -> {
            if (validateForm()) placeOrder(orderTotal);
        });
    }

    private void showProvincePicker() {
        String[] provinces = getResources().getStringArray(R.array.vn_provinces);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.choose_city))
                .setItems(provinces, (dialog, which) -> {
                    selectedProvince = provinces[which];
                    tvCityLabel.setText(selectedProvince);
                    tvCityLabel.setTextColor(getResources().getColor(R.color.lume_text_primary, null));
                })
                .show();
    }

    private boolean validateForm() {
        String name    = getText(etName);
        String phone   = getText(etPhone);
        String address = getText(etAddress);

        if (TextUtils.isEmpty(name)) {
            etName.setError(getString(R.string.err_name));
            etName.requestFocus();
            return false;
        }
        if (phone.length() != 10 || !phone.matches("[0-9]{10}")) {
            etPhone.setError(getString(R.string.err_phone));
            etPhone.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(address)) {
            etAddress.setError(getString(R.string.err_address));
            etAddress.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(selectedProvince)) {
            Toast.makeText(this, getString(R.string.err_city), Toast.LENGTH_SHORT).show();
            cityPicker.requestFocus();
            return false;
        }
        if (rgPayment.getCheckedRadioButtonId() == -1) {
            Toast.makeText(this, getString(R.string.err_payment), Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void placeOrder(long total) {
        CartRepository.getInstance().clear();
        String orderNum = generateOrderNumber();
        Intent intent = new Intent(this, OrderSuccessActivity.class);
        intent.putExtra(OrderSuccessActivity.EXTRA_ORDER_NUMBER, orderNum);
        intent.putExtra(OrderSuccessActivity.EXTRA_ORDER_TOTAL, total);
        startActivity(intent);
        finish();
    }

    private String generateOrderNumber() {
        String date = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
                .format(new java.util.Date());
        int seq = (int)(System.currentTimeMillis() % 9000) + 1000;
        return "#LUME" + date + "-" + seq;
    }

    private static String getText(EditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    // ── Order items adapter ───────────────────────────────────────────────────

    private static class OrderItemsAdapter
            extends RecyclerView.Adapter<OrderItemsAdapter.VH> {

        private final List<CartRepository.CartItem> items;

        OrderItemsAdapter(List<CartRepository.CartItem> items) { this.items = items; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_checkout_product, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            CartRepository.CartItem item = items.get(pos);
            h.tvName.setText(item.getProduct().getName());
            h.tvBrand.setText(item.getProduct().getBrand());
            h.tvQtyPrice.setText(String.format("x%d  %,dđ",
                    item.getQuantity(), (long) item.getProduct().getPrice() * item.getQuantity()));
            Glide.with(h.itemView.getContext())
                    .load(item.getProduct().getImageUrl().isEmpty() ? null : item.getProduct().getImageUrl())
                    .placeholder(R.drawable.product_placeholder)
                    .error(R.drawable.product_placeholder)
                    .centerCrop()
                    .into(h.ivImage);
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final ImageView ivImage;
            final TextView tvName, tvBrand, tvQtyPrice;

            VH(View v) {
                super(v);
                ivImage    = v.findViewById(R.id.iv_checkout_img);
                tvName     = v.findViewById(R.id.tv_checkout_item_name);
                tvBrand    = v.findViewById(R.id.tv_checkout_item_brand);
                tvQtyPrice = v.findViewById(R.id.tv_checkout_item_qty_price);
            }
        }
    }
}
