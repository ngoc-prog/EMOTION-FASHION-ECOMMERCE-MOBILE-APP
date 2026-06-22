package com.example.emotioncommerce.data;

import com.example.emotioncommerce.model.Product;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CartRepository {

    public static class CartItem {
        private final Product product;
        private final long effectivePrice;
        private final String selectedSize;
        private int quantity;

        CartItem(Product product, long effectivePrice, String selectedSize) {
            this.product       = product;
            this.effectivePrice = effectivePrice;
            this.selectedSize  = selectedSize == null ? "" : selectedSize;
            this.quantity      = 1;
        }

        public static CartItem fromPrefs(Product product, long effectivePrice, int quantity, String selectedSize) {
            CartItem item = new CartItem(product, effectivePrice, selectedSize);
            item.quantity = quantity;
            return item;
        }

        public Product getProduct()        { return product; }
        public int     getQuantity()       { return quantity; }
        public long    getEffectivePrice() { return effectivePrice; }
        public String  getSelectedSize()   { return selectedSize; }
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
    private String userKey = "guest";

    private CartRepository() {
        String email = AppPrefs.getLoggedInEmail();
        userKey = email.isEmpty() ? "guest" : email;
        for (CartItem item : AppPrefs.loadCart(userKey)) {
            items.put(item.getProduct().getId(), item);
        }
    }

    /** Switch to another user's cart. Called by AuthRepository on login/logout. */
    public synchronized void reload(String email) {
        userKey = email.isEmpty() ? "guest" : email;
        items.clear();
        for (CartItem item : AppPrefs.loadCart(userKey)) {
            items.put(item.getProduct().getId(), item);
        }
        notifyListeners();
    }

    public void addProduct(Product product) {
        addProduct(product, product.getEffectivePrice(), "");
    }

    public void addProduct(Product product, long effectivePrice) {
        addProduct(product, effectivePrice, "");
    }

    public void addProduct(Product product, long effectivePrice, String selectedSize) {
        CartItem existing = items.get(product.getId());
        if (existing != null) {
            existing.quantity++;
        } else {
            items.put(product.getId(), new CartItem(product, effectivePrice,
                    selectedSize == null ? "" : selectedSize));
        }
        persist();
        notifyListeners();
    }

    public void removeProduct(int productId) {
        items.remove(productId);
        persist();
        notifyListeners();
    }

    public void incrementQuantity(int productId) {
        CartItem item = items.get(productId);
        if (item != null) {
            item.quantity++;
            persist();
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
        persist();
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
        for (CartItem i : items.values()) total += i.effectivePrice * i.quantity;
        return total;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void clear() {
        items.clear();
        AppPrefs.clearCart(userKey);
        notifyListeners();
    }

    public void addListener(CartListener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(CartListener l) {
        listeners.remove(l);
    }

    private void persist() {
        AppPrefs.saveCart(userKey, new ArrayList<>(items.values()));
    }

    private void notifyListeners() {
        int count = getTotalCount();
        for (CartListener l : listeners) l.onCartChanged(count);
    }
}
