package com.example.emotioncommerce.data;

import com.example.emotioncommerce.model.Product;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CartRepository {

    public static class CartItem {
        public final Product product;
        public int quantity;

        CartItem(Product product) {
            this.product = product;
            this.quantity = 1;
        }
    }

    public interface CartListener {
        void onCartChanged(int totalCount);
    }

    private static CartRepository instance;

    public static CartRepository getInstance() {
        if (instance == null) instance = new CartRepository();
        return instance;
    }

    private final Map<Integer, CartItem> items = new LinkedHashMap<>();
    private final List<CartListener> listeners = new ArrayList<>();

    private CartRepository() {}

    public void addProduct(Product product) {
        CartItem existing = items.get(product.getId());
        if (existing != null) {
            existing.quantity++;
        } else {
            items.put(product.getId(), new CartItem(product));
        }
        notifyListeners();
    }

    public void removeProduct(int productId) {
        items.remove(productId);
        notifyListeners();
    }

    public void incrementQuantity(int productId) {
        CartItem item = items.get(productId);
        if (item != null) {
            item.quantity++;
            notifyListeners();
        }
    }

    public void decrementQuantity(int productId) {
        CartItem item = items.get(productId);
        if (item == null) return;
        if (item.quantity <= 1) {
            items.remove(productId);
        } else {
            item.quantity--;
        }
        notifyListeners();
    }

    public List<CartItem> getItems() {
        return new ArrayList<>(items.values());
    }

    public int getTotalCount() {
        int n = 0;
        for (CartItem i : items.values()) n += i.quantity;
        return n;
    }

    public long getTotalPrice() {
        long total = 0;
        for (CartItem i : items.values()) total += i.product.getPrice() * i.quantity;
        return total;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void clear() {
        items.clear();
        notifyListeners();
    }

    public void addListener(CartListener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(CartListener l) {
        listeners.remove(l);
    }

    private void notifyListeners() {
        int count = getTotalCount();
        for (CartListener l : listeners) l.onCartChanged(count);
    }
}
