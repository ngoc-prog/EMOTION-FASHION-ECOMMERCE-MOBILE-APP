package com.example.emotioncommerce.data;

import com.example.emotioncommerce.model.Product;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WishlistRepository {

    public interface WishlistListener {
        void onWishlistChanged(int totalCount);
    }

    private static WishlistRepository instance;

    public static WishlistRepository getInstance() {
        if (instance == null) instance = new WishlistRepository();
        return instance;
    }

    private final Set<Integer>        wishlisted = new HashSet<>();
    private final List<Product>       products   = new ArrayList<>();
    private final List<WishlistListener> listeners = new ArrayList<>();

    private WishlistRepository() {
        // Restore persisted wishlist
        for (Product p : AppPrefs.loadWishlist()) {
            wishlisted.add(p.getId());
            products.add(p);
        }
    }

    public void toggle(Product product) {
        if (wishlisted.contains(product.getId())) {
            wishlisted.remove(product.getId());
            products.removeIf(p -> p.getId() == product.getId());
        } else {
            wishlisted.add(product.getId());
            products.add(product);
        }
        AppPrefs.saveWishlist(new ArrayList<>(products));
        notifyListeners();
    }

    public boolean isWishlisted(int productId) {
        return wishlisted.contains(productId);
    }

    public List<Product> getProducts() {
        return new ArrayList<>(products);
    }

    public int getCount() {
        return wishlisted.size();
    }

    public void addListener(WishlistListener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(WishlistListener l) {
        listeners.remove(l);
    }

    private void notifyListeners() {
        int count = wishlisted.size();
        for (WishlistListener l : listeners) l.onWishlistChanged(count);
    }
}
