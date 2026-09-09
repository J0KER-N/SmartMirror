package com.wardrobe.backend.controller;

import com.wardrobe.backend.common.Result;
import com.wardrobe.backend.entity.Clothing;
import com.wardrobe.backend.service.ClothingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clothes")
public class ClothingController {

    @Autowired
    private ClothingService clothingService;

    // 获取用户的衣橱列表
    @GetMapping("/user/{userId}")
    public Result<List<Clothing>> getClothes(@PathVariable Long userId) {
        List<Clothing> clothes = clothingService.getUserClothes(userId);
        return Result.success(clothes);
    }

    // 根据 ID 获取单件衣物
    @GetMapping("/{id}")
    public Result<Clothing> getClothingById(@PathVariable Long id) {
        Clothing clothing = clothingService.getClothingById(id);
        if (clothing == null) {
            return Result.error("衣物不存在");
        }
        return Result.success(clothing);
    }

    // 添加新衣物
    @PostMapping
    public Result<Clothing> addClothing(@RequestBody Clothing clothing) {
        Clothing savedClothing = clothingService.addClothing(clothing);
        return Result.success(savedClothing);
    }

    // 更新衣物
    @PutMapping("/{id}")
    public Result<Clothing> updateClothing(@PathVariable Long id, @RequestBody Clothing clothing) {
        Clothing updated = clothingService.updateClothing(id, clothing);
        if (updated == null) {
            return Result.error("衣物不存在");
        }
        return Result.success(updated);
    }

    // 删除衣物
    @DeleteMapping("/{id}")
    public Result<String> deleteClothing(@PathVariable Long id) {
        clothingService.deleteClothing(id);
        return Result.success("删除成功");
    }
}
