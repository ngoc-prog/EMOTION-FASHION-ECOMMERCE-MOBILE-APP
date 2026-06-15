package com.example.emotioncommerce;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.emotioncommerce.data.AuthRepository;
import com.example.emotioncommerce.data.CartRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CartFragment extends Fragment implements CartRepository.CartListener {

    public static final String EXTRA_SELECTED_IDS = "selected_product_ids";

    private RecyclerView recyclerCart;
    private View layoutEmptyCart;
    private View layoutCheckout;
    private TextView tvTotalPrice;
    private TextView tvCartItemCount;
    private TextView tvCartCountHeader;
    private TextView tvSelectedCount;
    private CheckBox cbSelectAll;
    private CartAdapter adapter;

    // Tracks which product IDs are checked
    private final Set<Integer> selectedIds = new HashSet<>();

    private final ActivityResultLauncher<Intent> loginLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                proceedCheckout();
            }
        });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerCart      = view.findViewById(R.id.recycler_cart);
        layoutEmptyCart   = view.findViewById(R.id.layout_empty_cart);
        layoutCheckout    = view.findViewById(R.id.layout_checkout);
        tvTotalPrice      = view.findViewById(R.id.tv_total_price);
        tvCartItemCount   = view.findViewById(R.id.tv_cart_item_count);
        tvCartCountHeader = view.findViewById(R.id.tv_cart_count_header);
        tvSelectedCount   = view.findViewById(R.id.tv_selected_count);
        cbSelectAll       = view.findViewById(R.id.cb_select_all);

        recyclerCart.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CartAdapter();
        recyclerCart.setAdapter(adapter);

        view.findViewById(R.id.btn_continue_shopping).setOnClickListener(v ->
                ((MainActivity) requireActivity()).switchToProductsTab());

        cbSelectAll.setOnClickListener(v -> {
            boolean checked = cbSelectAll.isChecked();
            for (CartRepository.CartItem item : CartRepository.getInstance().getItems()) {
                if (checked) selectedIds.add(item.getProduct().getId());
                else         selectedIds.remove(item.getProduct().getId());
            }
            adapter.notifyDataSetChanged();
            updateBottomBar();
        });

        view.findViewById(R.id.btn_checkout).setOnClickListener(v -> {
            if (selectedIds.isEmpty()) return;
            if (AuthRepository.getInstance().isLoggedIn()) {
                proceedCheckout();
            } else {
                Intent intent = new Intent(requireContext(), LoginActivity.class);
                intent.putExtra(LoginActivity.EXTRA_FROM_CHECKOUT, true);
                loginLauncher.launch(intent);
            }
        });

        refreshCart();
    }

    @Override
    public void onResume() {
        super.onResume();
        CartRepository.getInstance().addListener(this);
        refreshCart();
    }

    @Override
    public void onPause() {
        super.onPause();
        CartRepository.getInstance().removeListener(this);
    }

    @Override
    public void onCartChanged(int totalCount) {
        if (isAdded()) requireActivity().runOnUiThread(this::refreshCart);
    }

    private void proceedCheckout() {
        Intent intent = new Intent(requireContext(), CheckoutActivity.class);
        ArrayList<Integer> ids = new ArrayList<>(selectedIds);
        intent.putIntegerArrayListExtra(EXTRA_SELECTED_IDS, ids);
        startActivity(intent);
    }

    private void refreshCart() {
        CartRepository cart = CartRepository.getInstance();
        boolean empty = cart.isEmpty();

        recyclerCart.setVisibility(empty ? View.GONE : View.VISIBLE);
        layoutEmptyCart.setVisibility(empty ? View.VISIBLE : View.GONE);
        layoutCheckout.setVisibility(empty ? View.GONE : View.VISIBLE);

        if (!empty) {
            // Auto-select newly added items
            for (CartRepository.CartItem item : cart.getItems()) {
                selectedIds.add(item.getProduct().getId());
            }
            // Remove IDs no longer in cart
            Set<Integer> cartIds = new HashSet<>();
            for (CartRepository.CartItem item : cart.getItems()) cartIds.add(item.getProduct().getId());
            selectedIds.retainAll(cartIds);

            adapter.setItems(cart.getItems());
            updateBottomBar();

            int count = cart.getTotalCount();
            String countLabel = getString(R.string.products_count, count);
            tvCartCountHeader.setText(countLabel);
            tvCartCountHeader.setVisibility(View.VISIBLE);
        } else {
            selectedIds.clear();
            tvCartCountHeader.setVisibility(View.GONE);
        }
    }

    private void updateBottomBar() {
        List<CartRepository.CartItem> items = CartRepository.getInstance().getItems();

        long selectedTotal = 0;
        int  selectedCount = 0;
        for (CartRepository.CartItem item : items) {
            if (selectedIds.contains(item.getProduct().getId())) {
                selectedTotal += item.getEffectivePrice() * item.getQuantity();
                selectedCount += item.getQuantity();
            }
        }

        tvTotalPrice.setText(getString(R.string.price_currency, selectedTotal));
        tvCartItemCount.setText(getString(R.string.products_count, selectedCount));
        tvSelectedCount.setText("Đã chọn " + selectedIds.size() + "/" + items.size());

        // Sync select-all checkbox state
        cbSelectAll.setOnCheckedChangeListener(null);
        cbSelectAll.setChecked(!selectedIds.isEmpty() && selectedIds.size() == items.size());
        cbSelectAll.setOnCheckedChangeListener((btn, checked) -> {
            for (CartRepository.CartItem item : items) {
                if (checked) selectedIds.add(item.getProduct().getId());
                else         selectedIds.remove(item.getProduct().getId());
            }
            adapter.notifyDataSetChanged();
            updateBottomBar();
        });

        // Dim checkout button when nothing selected
        View btnCheckout = getView() == null ? null : getView().findViewById(R.id.btn_checkout);
        if (btnCheckout != null) btnCheckout.setAlpha(selectedIds.isEmpty() ? 0.45f : 1f);
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private class CartAdapter extends RecyclerView.Adapter<CartAdapter.VH> {
        private List<CartRepository.CartItem> items = new ArrayList<>();

        void setItems(List<CartRepository.CartItem> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_cart, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            CartRepository.CartItem item = items.get(position);
            int productId = item.getProduct().getId();

            holder.tvName.setText(item.getProduct().getName());
            holder.tvBrand.setText(item.getProduct().getBrand());
            holder.tvPrice.setText(holder.itemView.getContext().getString(
                    R.string.price_currency, item.getEffectivePrice() * item.getQuantity()));
            holder.tvQuantity.setText(String.valueOf(item.getQuantity()));

            Glide.with(holder.itemView.getContext())
                    .load(item.getProduct().getImageUrl().isEmpty() ? null : item.getProduct().getImageUrl())
                    .placeholder(R.drawable.product_placeholder)
                    .error(R.drawable.product_placeholder)
                    .centerCrop()
                    .into(holder.ivImage);

            // Checkbox — must clear listener before setting checked to avoid loop
            holder.cbSelect.setOnCheckedChangeListener(null);
            holder.cbSelect.setChecked(selectedIds.contains(productId));
            holder.cbSelect.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) selectedIds.add(productId);
                else         selectedIds.remove(productId);
                updateBottomBar();
            });

            // Tapping the card row also toggles checkbox
            holder.itemView.setOnClickListener(v -> holder.cbSelect.toggle());

            holder.btnDecrement.setOnClickListener(v ->
                    CartRepository.getInstance().decrementQuantity(productId));
            holder.btnIncrement.setOnClickListener(v ->
                    CartRepository.getInstance().incrementQuantity(productId));
            holder.btnRemove.setOnClickListener(v -> {
                selectedIds.remove(productId);
                CartRepository.getInstance().removeProduct(productId);
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final CheckBox cbSelect;
            final ImageView ivImage;
            final TextView tvName, tvBrand, tvPrice, tvQuantity;
            final View btnDecrement, btnIncrement, btnRemove;

            VH(View v) {
                super(v);
                cbSelect     = v.findViewById(R.id.cb_select);
                ivImage      = v.findViewById(R.id.iv_cart_image);
                tvName       = v.findViewById(R.id.tv_cart_name);
                tvBrand      = v.findViewById(R.id.tv_cart_brand);
                tvPrice      = v.findViewById(R.id.tv_cart_price);
                tvQuantity   = v.findViewById(R.id.tv_quantity);
                btnDecrement = v.findViewById(R.id.btn_decrement);
                btnIncrement = v.findViewById(R.id.btn_increment);
                btnRemove    = v.findViewById(R.id.btn_remove);
            }
        }
    }
}
