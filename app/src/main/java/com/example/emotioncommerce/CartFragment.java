package com.example.emotioncommerce;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.List;

public class CartFragment extends Fragment implements CartRepository.CartListener {

    private RecyclerView recyclerCart;
    private View layoutEmptyCart;
    private View layoutCheckout;
    private TextView tvTotalPrice;
    private CartAdapter adapter;

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

        recyclerCart   = view.findViewById(R.id.recycler_cart);
        layoutEmptyCart = view.findViewById(R.id.layout_empty_cart);
        layoutCheckout  = view.findViewById(R.id.layout_checkout);
        tvTotalPrice    = view.findViewById(R.id.tv_total_price);

        recyclerCart.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CartAdapter();
        recyclerCart.setAdapter(adapter);

        view.findViewById(R.id.btn_continue_shopping).setOnClickListener(v ->
                ((MainActivity) requireActivity()).switchToProductsTab());

        view.findViewById(R.id.btn_checkout).setOnClickListener(v -> {
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
        startActivity(new Intent(requireContext(), CheckoutActivity.class));
    }

    private void refreshCart() {
        CartRepository cart = CartRepository.getInstance();
        boolean empty = cart.isEmpty();

        recyclerCart.setVisibility(empty ? View.GONE : View.VISIBLE);
        layoutEmptyCart.setVisibility(empty ? View.VISIBLE : View.GONE);
        layoutCheckout.setVisibility(empty ? View.GONE : View.VISIBLE);

        if (!empty) {
            adapter.setItems(cart.getItems());
            tvTotalPrice.setText(String.format("%,dđ", cart.getTotalPrice()));
        }
    }

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
            holder.tvName.setText(item.product.getName());
            holder.tvBrand.setText(item.product.getBrand());
            holder.tvPrice.setText(String.format("%,dđ", item.product.getPrice() * item.quantity));
            holder.tvQuantity.setText(String.valueOf(item.quantity));

            Glide.with(holder.itemView.getContext())
                    .load(item.product.getImageUrl().isEmpty() ? null : item.product.getImageUrl())
                    .placeholder(R.drawable.product_placeholder)
                    .error(R.drawable.product_placeholder)
                    .centerCrop()
                    .into(holder.ivImage);

            int productId = item.product.getId();
            holder.btnDecrement.setOnClickListener(v ->
                    CartRepository.getInstance().decrementQuantity(productId));
            holder.btnIncrement.setOnClickListener(v ->
                    CartRepository.getInstance().incrementQuantity(productId));
            holder.btnRemove.setOnClickListener(v ->
                    CartRepository.getInstance().removeProduct(productId));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final ImageView ivImage;
            final TextView tvName, tvBrand, tvPrice, tvQuantity;
            final View btnDecrement, btnIncrement, btnRemove;

            VH(View v) {
                super(v);
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
