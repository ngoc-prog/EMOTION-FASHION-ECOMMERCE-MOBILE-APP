package com.example.emotioncommerce.data;

import android.content.Context;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.emotioncommerce.model.Product;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DummyJsonRepository {

    public interface ProductsCallback {
        void onSuccess(List<Product> products);
        void onError(Exception e);
    }

    private static final int MIN_PRODUCTS = 30;

    // Verified Unsplash fashion/accessory photos — fallback if DummyJSON returns < 30.
    private static final String[] SUPPLEMENT_IMAGES = {
        "https://images.unsplash.com/photo-1556228578-8c89e6adf883?w=400&h=500&fit=crop",
        "https://images.unsplash.com/photo-1608248543803-ba4f8c70ae0b?w=400&h=500&fit=crop",
        "https://images.unsplash.com/photo-1617897903246-719242758050?w=400&h=500&fit=crop",
        "https://images.unsplash.com/photo-1598440947619-2c35fc9aa908?w=400&h=500&fit=crop",
        "https://images.unsplash.com/photo-1620916566398-39f1143ab7be?w=400&h=500&fit=crop",
        "https://images.unsplash.com/photo-1556228453-efd6c1ff04f6?w=400&h=500&fit=crop",
        "https://images.unsplash.com/photo-1571019614242-c5c5dee9f50b?w=400&h=500&fit=crop",
        "https://images.unsplash.com/photo-1612817288484-6f916006741a?w=400&h=500&fit=crop",
        "https://images.unsplash.com/photo-1519014816548-bf5fe059798b?w=400&h=500&fit=crop",
        "https://images.unsplash.com/photo-1607006344380-b6775a0824a7?w=400&h=500&fit=crop",
        "https://images.unsplash.com/photo-1516975080664-ed2fc6a32937?w=400&h=500&fit=crop",
        "https://images.unsplash.com/photo-1530103862676-de8c9debad1d?w=400&h=500&fit=crop",
        "https://images.unsplash.com/photo-1532413992378-f169ac26fff0?w=400&h=500&fit=crop",
        "https://images.unsplash.com/photo-1515377905703-c4788e51af15?w=400&h=500&fit=crop",
        "https://images.unsplash.com/photo-1616394584738-fc6e612e71b9?w=400&h=500&fit=crop",
        "https://images.unsplash.com/photo-1529810313688-44ea1c2d81d3?w=400&h=500&fit=crop",
        "https://images.unsplash.com/photo-1556228720-195a672e8a03?w=400&h=500&fit=crop",
    };

    private static volatile List<Product> sCache = null;
    private static RequestQueue sQueue = null;

    public static void clearCache() { sCache = null; }

    private static RequestQueue getQueue(Context context) {
        if (sQueue == null) sQueue = Volley.newRequestQueue(context.getApplicationContext());
        return sQueue;
    }

    public static void fetchSkinCareProducts(Context context, ProductsCallback callback) {
        if (sCache != null) { callback.onSuccess(sCache); return; }
        RequestQueue queue = getQueue(context);
        List<Product> combined = new ArrayList<>();
        Set<Integer> seenIds   = new HashSet<>();
        // 7 fashion categories: bags(5) + mens-watches(6) + womens-watches(5)
        //   + sunglasses(5) + dresses(5) + shoes(5) + jewellery(3) = 34 products
        int[] pending = {7};
        fetchCategory(queue, "womens-bags",      combined, seenIds, pending, callback);
        fetchCategory(queue, "mens-watches",      combined, seenIds, pending, callback);
        fetchCategory(queue, "womens-watches",    combined, seenIds, pending, callback);
        fetchCategory(queue, "sunglasses",        combined, seenIds, pending, callback);
        fetchCategory(queue, "womens-dresses",    combined, seenIds, pending, callback);
        fetchCategory(queue, "womens-shoes",      combined, seenIds, pending, callback);
        fetchCategory(queue, "womens-jewellery",  combined, seenIds, pending, callback);
    }

    private static void fetchCategory(RequestQueue queue, String category,
                                       List<Product> combined, Set<Integer> seenIds,
                                       int[] pending, ProductsCallback callback) {
        String url = "https://dummyjson.com/products/category/" + category + "?limit=100";
        queue.add(new JsonObjectRequest(Request.Method.GET, url, null,
            response -> {
                try {
                    JSONArray arr = response.getJSONArray("products");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject p = arr.getJSONObject(i);
                        int id = p.getInt("id");
                        if (seenIds.contains(id)) continue;
                        seenIds.add(id);

                        long price    = Math.round(p.getDouble("price") * 25000 / 1000.0) * 1000;
                        double rawDisc = p.optDouble("discountPercentage", 0.0);
                        int discount  = rawDisc >= 15.0 ? (int) Math.round(rawDisc) : 0;
                        String thumb  = p.optString("thumbnail", "");
                        ArrayList<String> imgs = new ArrayList<>();
                        JSONArray raw = p.optJSONArray("images");
                        if (raw != null) for (int j = 0; j < raw.length(); j++) imgs.add(raw.getString(j));
                        if (imgs.isEmpty() && !thumb.isEmpty()) imgs.add(thumb);

                        float rating = (float) p.optDouble("rating", 0.0);
                        JSONArray rev = p.optJSONArray("reviews");

                        combined.add(new Product(id, p.getString("title"),
                            p.getString("description"), price, discount,
                            0, mapCategory(category), category, thumb,
                            p.optString("brand", "ELAN"), imgs,
                            rating, rev != null ? rev.length() : 0));
                    }
                } catch (JSONException ignored) {}
                if (--pending[0] == 0) deliver(combined, callback);
            },
            error -> { if (--pending[0] == 0) deliver(combined, callback); }
        ));
    }

    private static void deliver(List<Product> combined, ProductsCallback callback) {
        if (combined.isEmpty()) {
            sCache = new ArrayList<>(MockProductData.getProducts());
            callback.onSuccess(sCache);
            return;
        }

        // Safety net: if some categories failed and total < 30, supplement with mock names
        if (combined.size() < MIN_PRODUCTS) {
            List<Product> mocks = MockProductData.getProducts();
            int mockIdx = 0;
            int supIdx  = 0;
            for (Product m : mocks) {
                if (combined.size() >= MIN_PRODUCTS) break;
                String imgUrl = supIdx < SUPPLEMENT_IMAGES.length
                    ? SUPPLEMENT_IMAGES[supIdx++]
                    : m.getImageUrl();
                ArrayList<String> gallery = new ArrayList<>();
                gallery.add(imgUrl);
                combined.add(new Product(1000 + (++mockIdx),
                    m.getName(), m.getDescription(), m.getPrice(),
                    0, "Thời trang", imgUrl, "ÉLAN",
                    gallery, m.getRating(), m.getReviewCount()));
            }
        }

        sCache = new ArrayList<>(combined);
        callback.onSuccess(sCache);
    }

    private static String mapCategory(String cat) {
        switch (cat) {
            case "womens-bags":      return "Phụ kiện";
            case "womens-jewellery": return "Phụ kiện";
            case "sunglasses":       return "Phụ kiện";
            case "mens-watches":     return "Đồng hồ";
            case "womens-watches":   return "Đồng hồ";
            case "womens-dresses":   return "Thời trang";
            case "womens-shoes":     return "Thời trang";
            default:                 return "Thời trang";
        }
    }
}
