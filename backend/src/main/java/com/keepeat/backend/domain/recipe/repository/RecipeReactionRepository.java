package com.keepeat.backend.domain.recipe.repository;

import com.keepeat.backend.domain.common.enums.ReactionType;
import com.keepeat.backend.domain.recipe.dto.ReactionCountDto;
import com.keepeat.backend.domain.recipe.entity.RecipeReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecipeReactionRepository extends JpaRepository<RecipeReaction, Long> {
    Optional<RecipeReaction> findByAppUserIdAndRecipeId(Long userId, Long recipeId);

    long countByRecipeIdAndReactionType(Long recipeId, ReactionType reactionType);

    @Query("""
        SELECT new com.keepeat.backend.domain.recipe.dto.ReactionCountDto(r.recipe.id, COUNT(r))
        FROM RecipeReaction r
        WHERE r.recipe.id IN :recipeIds
          AND r.reactionType = :reactionType
        GROUP BY r.recipe.id
    """)
    List<ReactionCountDto> countByRecipeIdsAndType(
            @Param("recipeIds") List<Long> recipeIds,
            @Param("reactionType") ReactionType reactionType
    );

    // 특정 유저의 여러 레시피에 대한 반응 한 번에 가져오기
    @Query("""
        SELECT r FROM RecipeReaction r
        WHERE r.appUser.id = :userId
          AND r.recipe.id IN :recipeIds
    """)
    List<RecipeReaction> findAllByUserIdAndRecipeIds(
            @Param("userId") Long userId,
            @Param("recipeIds") List<Long> recipeIds
    );
}
