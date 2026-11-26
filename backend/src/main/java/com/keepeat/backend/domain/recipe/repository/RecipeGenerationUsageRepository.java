package com.keepeat.backend.domain.recipe.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import com.keepeat.backend.domain.recipe.entity.RecipeGenerationUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface RecipeGenerationUsageRepository extends JpaRepository<RecipeGenerationUsage, Long> {

    boolean existsByUserIdAndUsageDate(Long userId, LocalDate usageDate);

    // 회원 탈퇴 시 생성 기록 삭제 (#58)
    @Modifying
    @Query("DELETE FROM RecipeGenerationUsage u WHERE u.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
