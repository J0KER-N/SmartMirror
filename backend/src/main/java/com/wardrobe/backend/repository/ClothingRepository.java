package com.wardrobe.backend.repository; // ⚠️ 确保包名正确

import com.wardrobe.backend.entity.Clothing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

// JpaRepository 提供了基础的增删改查，不需要写 SQL
@Repository
public interface ClothingRepository extends JpaRepository<Clothing, Long> {

    // Spring Data JPA 会自动根据方法名生成 SQL 语句
    // 根据用户ID查询所有衣物
    List<Clothing> findByUserId(Long userId);

    // 根据用户ID和分类查询
    List<Clothing> findByUserIdAndCategory(Long userId, String category);
}
