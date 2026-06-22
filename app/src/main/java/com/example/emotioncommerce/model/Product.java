package com.example.emotioncommerce.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Product implements Serializable {
    private final int id;
    private final String name;
    private final String description;
    private final long price;
    private final int discount;
    private final int imageResId;
    private final String category;
    private final String rawCategory;
    private final String imageUrl;
    private final String brand;
    private final ArrayList<String> images;
    private final float rating;
    private final int reviewCount;

    // Primary constructor (with discount + rawCategory)
    public Product(int id, String name, String description, long price, int discount,
                   int imageResId, String category, String rawCategory, String imageUrl, String brand,
                   ArrayList<String> images, float rating, int reviewCount) {
        this.id          = id;
        this.name        = name;
        this.description = description;
        this.price       = price;
        this.discount    = Math.max(0, Math.min(99, discount));
        this.imageResId  = imageResId;
        this.category    = category;
        this.rawCategory = rawCategory != null ? rawCategory : "";
        this.imageUrl    = imageUrl != null ? imageUrl : "";
        this.brand       = brand != null ? brand : "ELAN";
        this.images      = images != null ? images : new ArrayList<>();
        this.rating      = rating;
        this.reviewCount = reviewCount;
    }

    // Legacy — no discount, with rawCategory
    public Product(int id, String name, String description, long price,
                   int imageResId, String category, String rawCategory, String imageUrl, String brand,
                   ArrayList<String> images, float rating, int reviewCount) {
        this(id, name, description, price, 0, imageResId, category, rawCategory, imageUrl, brand, images, rating, reviewCount);
    }

    // Legacy — no discount, no rawCategory
    public Product(int id, String name, String description, long price,
                   int imageResId, String category, String imageUrl, String brand,
                   ArrayList<String> images, float rating, int reviewCount) {
        this(id, name, description, price, 0, imageResId, category, "", imageUrl, brand, images, rating, reviewCount);
    }

    public Product(int id, String name, String description, long price,
                   int imageResId, String category, String imageUrl, String brand,
                   ArrayList<String> images) {
        this(id, name, description, price, 0, imageResId, category, "", imageUrl, brand, images, 0f, 0);
    }

    public Product(int id, String name, String description, long price,
                   int imageResId, String category, String imageUrl, String brand) {
        this(id, name, description, price, 0, imageResId, category, "", imageUrl, brand, null, 0f, 0);
    }

    public Product(int id, String name, String description, long price,
                   int imageResId, String category) {
        this(id, name, description, price, 0, imageResId, category, "", "", "ELAN", null, 0f, 0);
    }

    public int getId()             { return id; }
    public String getName()        { return name; }
    public String getDescription() { return description; }
    public long getPrice()         { return price; }
    public int getDiscount()       { return discount; }
    public long getEffectivePrice(){ return discount > 0 ? price * (100 - discount) / 100 : price; }
    public int getImageResId()     { return imageResId; }
    public String getCategory()    { return category; }
    public String getRawCategory() { return rawCategory; }
    public String getImageUrl()    { return imageUrl; }
    public String getBrand()       { return brand; }
    public List<String> getImages(){ return images; }
    public float getRating()       { return rating; }
    public int getReviewCount()    { return reviewCount; }
}
