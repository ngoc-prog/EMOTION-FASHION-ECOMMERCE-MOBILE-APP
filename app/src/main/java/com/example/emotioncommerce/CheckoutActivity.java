package com.example.emotioncommerce;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.emotioncommerce.data.AddressRepository;
import com.example.emotioncommerce.data.AuthRepository;
import com.example.emotioncommerce.data.CartRepository;
import com.example.emotioncommerce.model.Address;
import java.util.ArrayList;
import java.util.List;

public class CheckoutActivity extends AppCompatActivity {

    private EditText etName, etPhone, etAddress;
    private TextView tvCityLabel;
    private View cityPicker;
    private RadioGroup rgPayment;
    private TextView tvBottomTotal;
    private RecyclerView recyclerOrderItems;

    private View layoutPickedAddress;
    private TextView tvPickedNamePhone, tvPickedAddress, tvNoAddress;

    private String selectedProvince = "";
    private String selectedAddressCity = "";
    private List<CartRepository.CartItem> checkoutItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        etName             = findViewById(R.id.et_checkout_name);
        etPhone            = findViewById(R.id.et_checkout_phone);
        etAddress          = findViewById(R.id.et_checkout_address);
        cityPicker         = findViewById(R.id.et_checkout_city);
        tvCityLabel        = findViewById(R.id.tv_city_label);
        rgPayment          = findViewById(R.id.rg_payment);
        tvBottomTotal      = findViewById(R.id.tv_checkout_total);
        recyclerOrderItems = findViewById(R.id.recycler_order_items);
        layoutPickedAddress = findViewById(R.id.layout_picked_address);
        tvPickedNamePhone   = findViewById(R.id.tv_picked_name_phone);
        tvPickedAddress     = findViewById(R.id.tv_picked_address);
        tvNoAddress         = findViewById(R.id.tv_no_address);

        findViewById(R.id.btn_change_address).setOnClickListener(v -> showAddressPicker());

        // Pre-fill from saved default address if available
        Address defaultAddr = AddressRepository.getInstance().getDefault();
        if (defaultAddr != null) {
            applyAddress(defaultAddr);
        } else if (AuthRepository.getInstance().isLoggedIn()) {
            String displayName = AuthRepository.getInstance().getDisplayName();
            if (!TextUtils.isEmpty(displayName)) etName.setText(displayName);
        }
        refreshAddressCard();

        // Province picker
        cityPicker.setOnClickListener(v -> showProvincePicker());

        // Filter by selected IDs from CartFragment
        ArrayList<Integer> selectedIds =
                getIntent().getIntegerArrayListExtra(CartFragment.EXTRA_SELECTED_IDS);
        CartRepository cart = CartRepository.getInstance();
        List<CartRepository.CartItem> allItems = cart.getItems();
        List<CartRepository.CartItem> items;
        if (selectedIds == null || selectedIds.isEmpty()) {
            items = allItems; // fallback: show all
        } else {
            items = new ArrayList<>();
            for (CartRepository.CartItem ci : allItems) {
                if (selectedIds.contains(ci.getProduct().getId())) items.add(ci);
            }
        }

        checkoutItems = items;
        recyclerOrderItems.setLayoutManager(new LinearLayoutManager(this));
        recyclerOrderItems.setNestedScrollingEnabled(false);
        recyclerOrderItems.setAdapter(new OrderItemsAdapter(items));

        // Order summary (based on selected items only)
        int selectedCount = 0;
        long subtotal = 0;
        for (CartRepository.CartItem ci : items) {
            selectedCount += ci.getQuantity();
            subtotal += ci.getEffectivePrice() * ci.getQuantity();
        }
        ((TextView) findViewById(R.id.tv_order_count))
                .setText(getString(R.string.order_with_count, selectedCount));

        long shipping = subtotal > 500_000 ? 0 : 30_000;
        long total    = subtotal + shipping;

        ((TextView) findViewById(R.id.tv_subtotal))
                .setText(getString(R.string.price_currency, subtotal));
        ((TextView) findViewById(R.id.tv_shipping_fee))
                .setText(shipping == 0 ? getString(R.string.free) : getString(R.string.price_currency, shipping));
        tvBottomTotal.setText(getString(R.string.price_currency, total));

        findViewById(R.id.btn_back_checkout).setOnClickListener(v -> finish());

        final long orderTotal = total;
        findViewById(R.id.btn_place_order).setOnClickListener(v -> {
            if (validateForm()) placeOrder(orderTotal);
        });
    }

    private void applyAddress(Address addr) {
        etName.setText(addr.getName());
        etPhone.setText(addr.getPhone());
        etAddress.setText(addr.getStreet());
        selectedProvince = addr.getCity();
        tvCityLabel.setText(selectedProvince);
        tvCityLabel.setTextColor(getResources().getColor(R.color.lume_text_primary, null));
    }

    private void refreshAddressCard() {
        Address picked = AddressRepository.getInstance().getDefault();
        if (picked != null) {
            layoutPickedAddress.setVisibility(View.VISIBLE);
            tvNoAddress.setVisibility(View.GONE);
            tvPickedNamePhone.setText(picked.getName() + "  " + picked.getPhone());
            tvPickedAddress.setText(picked.getFullAddress());
        } else {
            layoutPickedAddress.setVisibility(View.GONE);
            tvNoAddress.setVisibility(View.VISIBLE);
        }
    }

    private void showAddressPicker() {
        List<Address> saved = AddressRepository.getInstance().getAll();

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View dv = LayoutInflater.from(this).inflate(R.layout.dialog_address_picker, null);
        dialog.setContentView(dv);

        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT));
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                          ViewGroup.LayoutParams.WRAP_CONTENT);
            win.setGravity(android.view.Gravity.BOTTOM);
            win.getDecorView().setBackgroundResource(R.drawable.bg_bottom_sheet);
        }

        RecyclerView rv = dv.findViewById(R.id.rv_picker);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new AddressPickerAdapter(saved, selectedIndex -> {
            AddressRepository.getInstance().setDefault(selectedIndex);
            applyAddress(saved.get(selectedIndex));
            refreshAddressCard();
            dialog.dismiss();
        }));

        dv.findViewById(R.id.btn_dismiss).setOnClickListener(v -> dialog.dismiss());
        dv.findViewById(R.id.btn_add_new).setOnClickListener(v -> {
            dialog.dismiss();
            showAddNewAddressDialog();
        });

        dialog.show();
    }

    // ── Address picker adapter ────────────────────────────────────────────────

    private static class AddressPickerAdapter
            extends RecyclerView.Adapter<AddressPickerAdapter.VH> {

        interface OnSelect { void onSelect(int index); }

        private final List<Address> items;
        private final OnSelect listener;

        AddressPickerAdapter(List<Address> items, OnSelect listener) {
            this.items    = items;
            this.listener = listener;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_address_picker, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Address a = items.get(pos);
            h.tvName.setText(a.getName() + "  " + a.getPhone());
            h.tvPhone.setVisibility(View.GONE);
            h.tvAddress.setText(a.getFullAddress());
            h.tvDefaultBadge.setVisibility(a.isDefault() ? View.VISIBLE : View.GONE);
            h.viewRadio.setBackgroundResource(a.isDefault()
                    ? R.drawable.bg_radio_selected : R.drawable.bg_radio_unselected);
            h.itemView.setOnClickListener(v -> listener.onSelect(h.getBindingAdapterPosition()));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            View viewRadio;
            TextView tvName, tvPhone, tvAddress, tvDefaultBadge;
            VH(View v) {
                super(v);
                viewRadio      = v.findViewById(R.id.view_radio);
                tvName         = v.findViewById(R.id.tv_name);
                tvPhone        = v.findViewById(R.id.tv_phone);
                tvAddress      = v.findViewById(R.id.tv_address);
                tvDefaultBadge = v.findViewById(R.id.tv_default_badge);
            }
        }
    }

    private void showAddNewAddressDialog() {
        String[] provinces = getResources().getStringArray(R.array.vn_provinces);
        final String[] newCity = {""};

        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_address, null);
        EditText etAddrName   = dialogView.findViewById(R.id.et_addr_name);
        EditText etAddrPhone  = dialogView.findViewById(R.id.et_addr_phone);
        EditText etAddrStreet = dialogView.findViewById(R.id.et_addr_street);
        TextView tvDialogCity = dialogView.findViewById(R.id.tv_dialog_city);
        View cityPickerRow    = dialogView.findViewById(R.id.city_picker);

        cityPickerRow.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.choose_city))
                        .setItems(provinces, (d, which) -> {
                            newCity[0] = provinces[which];
                            tvDialogCity.setText(newCity[0]);
                            tvDialogCity.setTextColor(
                                    getResources().getColor(R.color.lume_text_primary, null));
                        })
                        .show());

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton(getString(R.string.save), (d, w) -> {
                    String name   = etAddrName.getText().toString().trim();
                    String phone  = etAddrPhone.getText().toString().trim();
                    String street = etAddrStreet.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(this, getString(R.string.err_name), Toast.LENGTH_SHORT).show(); return;
                    }
                    if (phone.length() != 10 || !phone.matches("[0-9]{10}")) {
                        Toast.makeText(this, getString(R.string.err_phone), Toast.LENGTH_SHORT).show(); return;
                    }
                    if (TextUtils.isEmpty(street)) {
                        Toast.makeText(this, getString(R.string.err_address), Toast.LENGTH_SHORT).show(); return;
                    }
                    if (TextUtils.isEmpty(newCity[0])) {
                        Toast.makeText(this, getString(R.string.err_city), Toast.LENGTH_SHORT).show(); return;
                    }
                    Address addr = new Address(name, phone, street, newCity[0], false);
                    AddressRepository.getInstance().add(addr);
                    // Set newly added address as default and apply
                    int lastIdx = AddressRepository.getInstance().getAll().size() - 1;
                    AddressRepository.getInstance().setDefault(lastIdx);
                    applyAddress(addr);
                    refreshAddressCard();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
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
        int payId = rgPayment.getCheckedRadioButtonId();
        if (payId == R.id.rb_bank) {
            showQRPaymentDialog(total);
        } else if (payId == R.id.rb_card) {
            showCardPaymentDialog(total);
        } else {
            doPlaceOrder(total);
        }
    }

    private void doPlaceOrder(long total) {
        List<com.example.emotioncommerce.model.Order.OrderItem> orderItems = new ArrayList<>();
        for (CartRepository.CartItem ci : checkoutItems) {
            orderItems.add(new com.example.emotioncommerce.model.Order.OrderItem(
                    ci.getProduct().getName(),
                    ci.getProduct().getImageUrl(),
                    ci.getEffectivePrice(),
                    ci.getQuantity()));
        }

        String addrStr = "";
        Address picked = com.example.emotioncommerce.data.AddressRepository.getInstance().getDefault();
        if (picked != null) {
            addrStr = picked.getName() + " · " + picked.getStreet() + ", " + picked.getCity();
        } else if (etName != null) {
            addrStr = getText(etName) + " · " + getText(etAddress) + ", " + selectedProvince;
        }

        String orderNum = generateOrderNumber();
        com.example.emotioncommerce.model.Order order = new com.example.emotioncommerce.model.Order(
                orderNum, total, System.currentTimeMillis(), addrStr, orderItems);
        com.example.emotioncommerce.data.OrderRepository.getInstance().addOrder(order);

        for (CartRepository.CartItem ci : checkoutItems) {
            CartRepository.getInstance().removeProduct(ci.getProduct().getId());
        }
        Intent intent = new Intent(this, OrderSuccessActivity.class);
        intent.putExtra(OrderSuccessActivity.EXTRA_ORDER_NUMBER, orderNum);
        intent.putExtra(OrderSuccessActivity.EXTRA_ORDER_TOTAL, total);
        startActivity(intent);
        finish();
    }

    private void showQRPaymentDialog(long total) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        View dv = LayoutInflater.from(this).inflate(R.layout.dialog_qr_payment, null);
        dialog.setContentView(dv);

        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT));
            win.setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.9f),
                          ViewGroup.LayoutParams.WRAP_CONTENT);
            win.setGravity(Gravity.CENTER);
        }

        ((TextView) dv.findViewById(R.id.tv_qr_amount))
                .setText(getString(R.string.price_currency, total));

        TextView tvCountdown = dv.findViewById(R.id.tv_qr_countdown);
        View btnConfirm = dv.findViewById(R.id.btn_qr_confirm);
        View btnCancel  = dv.findViewById(R.id.btn_qr_cancel);

        final CountDownTimer[] timerRef = new CountDownTimer[1];

        btnConfirm.setOnClickListener(v -> {
            timerRef[0].cancel();
            dialog.dismiss();
            doPlaceOrder(total);
        });
        btnCancel.setOnClickListener(v -> {
            timerRef[0].cancel();
            dialog.dismiss();
        });

        dialog.show();

        timerRef[0] = new CountDownTimer(15_000, 1_000) {
            @Override public void onTick(long ms) {
                tvCountdown.setText(getString(R.string.qr_countdown, (ms / 1000) + 1));
            }
            @Override public void onFinish() {
                if (dialog.isShowing()) { dialog.dismiss(); doPlaceOrder(total); }
            }
        }.start();
    }

    private void showCardPaymentDialog(long total) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        View dv = LayoutInflater.from(this).inflate(R.layout.dialog_card_payment, null);
        dialog.setContentView(dv);

        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT));
            win.setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.9f),
                          ViewGroup.LayoutParams.WRAP_CONTENT);
            win.setGravity(Gravity.CENTER);
        }

        TextView tvCountdown = dv.findViewById(R.id.tv_card_countdown);
        ProgressBar progress  = dv.findViewById(R.id.progress_card);
        View btnConfirm = dv.findViewById(R.id.btn_card_confirm);
        View btnCancel  = dv.findViewById(R.id.btn_card_cancel);

        final CountDownTimer[] timerRef = new CountDownTimer[1];

        btnConfirm.setOnClickListener(v -> {
            timerRef[0].cancel();
            dialog.dismiss();
            doPlaceOrder(total);
        });
        btnCancel.setOnClickListener(v -> {
            timerRef[0].cancel();
            dialog.dismiss();
        });

        dialog.show();

        timerRef[0] = new CountDownTimer(10_000, 100) {
            @Override public void onTick(long ms) {
                tvCountdown.setText(getString(R.string.card_countdown, (int)((ms / 1000) + 1)));
                progress.setProgress((int)(ms * 100 / 10_000));
            }
            @Override public void onFinish() {
                progress.setProgress(0);
                if (dialog.isShowing()) { dialog.dismiss(); doPlaceOrder(total); }
            }
        }.start();
    }

    private String generateOrderNumber() {
        String date = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
                .format(new java.util.Date());
        int seq = (int)(System.currentTimeMillis() % 9000) + 1000;
        return getString(R.string.order_number_prefix) + date + "-" + seq;
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
            h.tvQtyPrice.setText(String.format(java.util.Locale.getDefault(),
                    "x%d  ", item.getQuantity()) +
                    h.itemView.getContext().getString(R.string.price_currency,
                    item.getEffectivePrice() * item.getQuantity()));
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
