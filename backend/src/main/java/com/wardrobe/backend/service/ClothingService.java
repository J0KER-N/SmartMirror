package com.wardrobe.backend.service;

import com.wardrobe.backend.entity.Clothing;
import com.wardrobe.backend.repository.ClothingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class ClothingService {

    @Autowired
    private ClothingRepository clothingRepository;

    public List<Clothing> getUserClothes(Long userId) {
        return clothingRepository.findByUserId(userId);
    }

    public Clothing getClothingById(Long id) {
        Optional<Clothing> optional = clothingRepository.findById(id);
        return optional.orElse(null);
    }

    public Clothing addClothing(Clothing clothing) {
        return clothingRepository.save(clothing);
    }

    public Clothing updateClothing(Long id, Clothing clothing) {
        Optional<Clothing> optional = clothingRepository.findById(id);
        if (optional.isEmpty()) {
            return null;
        }
        Clothing existing = optional.get();
        existing.setName(clothing.getName());
        existing.setBrand(clothing.getBrand());
        existing.setCategory(clothing.getCategory());
        existing.setMaterial(clothing.getMaterial());
        existing.setSizeJson(clothing.getSizeJson());
        existing.setDescription(clothing.getDescription());
        existing.setPrice(clothing.getPrice());
        existing.setRating(clothing.getRating());
        existing.setReviewCount(clothing.getReviewCount());
        existing.setColorsJson(clothing.getColorsJson());
        existing.setIsNew(clothing.getIsNew());
        existing.setTagsJson(clothing.getTagsJson());
        existing.setCareInstructions(clothing.getCareInstructions());
        existing.setImageUrl(clothing.getImageUrl());
        existing.setSeason(clothing.getSeason());
        existing.setPurchaseDate(clothing.getPurchaseDate());
        return clothingRepository.save(existing);
    }

    public void deleteClothing(Long id) {
        clothingRepository.deleteById(id);
    }
}
