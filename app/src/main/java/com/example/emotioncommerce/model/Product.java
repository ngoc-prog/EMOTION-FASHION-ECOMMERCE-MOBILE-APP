package com.example.emotioncommerce.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Product implements Serializable {
    private final int id;
    private final String name;
    private final String description;
    private final long price;
    private final int imageResId;
    private final String category;
    private final String imageUrl;
    private final String brand;
    private final ArrayList<String> images;
    private final float rating;
    private final int reviewCount;

    // Full constructor
    public Product(int id, String name, String description, long price,
                   int imageResId, String category, String imageUrl, String brand,
                   ArrayList<String> images, float rating, int reviewCount) {
        this.id          = id;
        this.name        = name;
        this.description = description;
        this.price       = price;
        this.imageResId  = imageResId;
        this.category    = category;
        this.imageUrl    = imageUrl != null ? imageUrl : "";
        this.brand       = brand != null ? brand : "ÉLAN";
        this.images      = images != null ? images : new ArrayList<>();
        this.rating      = rating;
        this.reviewCount = reviewCount;
    }

    public Product(int id, String name, String description, long price,
                   int imageResId, String category, String imageUrl, String brand,
                   ArrayList<String> images) {
        this(id, name, description, price, imageResId, category, imageUrl, brand, images, 0f, 0);
    }

    public Product(int id, String name, String description, long price,
                   int imageResId, String category, String imageUrl, String brand) {
        this(id, name, description, price, imageResId, category, imageUrl, brand, null, 0f, 0);
    }

    public Product(int id, String name, String description, long price,
                   int imageResId, String category) {
        this(id, name, description, price, imageResId, category, "", "ÉLAN", null, 0f, 0);
    }

    public int getId()            { return id; }
    public String getName()       { return name; }
    public String getDescription(){ return description; }
    public long getPrice()        { return price; }
    public int getImageResId()    { return imageResId; }
    public String getCategory()   { return category; }
    public String getImageUrl()   { return imageUrl; }
    public String getBrand()      { return brand; }
    public List<String> getImages(){ return images; }
    public float getRating()      { return rating; }
    public int getReviewCount()   { return reviewCount; }
}
