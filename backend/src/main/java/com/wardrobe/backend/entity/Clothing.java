package com.wardrobe.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "clothing")
public class Clothing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String name;
    private String brand;
    private String category;
    private String material;

    @Column(name = "size_json")
    private String sizeJson;       // JSON 数组，如 ["S","M","L"]

    @Column(name = "description", length = 1000)
    private String description;

    private Integer price;

    @Column(precision = 3, scale = 1)
    private BigDecimal rating;

    @Column(name = "review_count")
    private Integer reviewCount;

    @Column(name = "colors_json", length = 1000)
    private String colorsJson;     // JSON 数组，如 [{"name":"白色","hex":"#FFFFFF"}]

    @Column(name = "is_new")
    private Boolean isNew;

    @Column(name = "tags_json", length = 500)
    private String tagsJson;       // JSON 数组，如 ["基础款","百搭"]

    @Column(name = "care_instructions", length = 500)
    private String careInstructions;

    private String imageUrl;
    private String season;
    private LocalDate purchaseDate;

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public String getSizeJson() { return sizeJson; }
    public void setSizeJson(String sizeJson) { this.sizeJson = sizeJson; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getPrice() { return price; }
    public void setPrice(Integer price) { this.price = price; }

    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }

    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }

    public String getColorsJson() { return colorsJson; }
    public void setColorsJson(String colorsJson) { this.colorsJson = colorsJson; }

    public Boolean getIsNew() { return isNew; }
    public void setIsNew(Boolean isNew) { this.isNew = isNew; }

    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }

    public String getCareInstructions() { return careInstructions; }
    public void setCareInstructions(String careInstructions) { this.careInstructions = careInstructions; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }

    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
}
