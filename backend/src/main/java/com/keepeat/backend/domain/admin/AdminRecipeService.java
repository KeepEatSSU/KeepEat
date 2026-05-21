package com.keepeat.backend.domain.admin;

import com.keepeat.backend.domain.admin.dto.AdminRecipeListItemDto;
import com.keepeat.backend.domain.common.enums.AdminRecipeSearchSort;
import com.keepeat.backend.domain.common.enums.ReactionType;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.recipe.dto.ReactionCountDto;
import com.keepeat.backend.domain.recipe.entity.Recipe;
import com.keepeat.backend.domain.recipe.repository.RecipeReactionRepository;
import com.keepeat.backend.domain.recipe.repository.RecipeRepository;
import com.keepeat.backend.domain.recipe.repository.UserRecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminRecipeService {

    private final RecipeRepository recipeRepository;
    private final UserRecipeRepository userRecipeRepository;
    private final RecipeReactionRepository recipeReactionRepository;


    @Transactional(readOnly = true)
    public Page<AdminRecipeListItemDto> getRecipeListForAdmin(
            String keyword,
            AdminRecipeSearchSort sort,
            boolean onlyDeletable,
            Pageable pageable
    ){

        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        AdminRecipeSearchSort normalizedSort = (sort == null) ? AdminRecipeSearchSort.LATEST : sort;

        Page<AdminRecipeListItemDto> page = switch (normalizedSort) {
            case LATEST         -> recipeRepository.searchForAdmin(normalizedKeyword, onlyDeletable, pageable);
            case LIKES_DESC     -> recipeRepository.searchForAdminOrderByLikes(normalizedKeyword, onlyDeletable, pageable);
            case DISLIKES_DESC  -> recipeRepository.searchForAdminOrderByDislikes(normalizedKeyword, onlyDeletable, pageable);
            case BOOKMARKED_ASC -> recipeRepository.searchForAdminOrderByBookmarks(normalizedKeyword, onlyDeletable, pageable);
        };

        if(page.isEmpty()){
            return page;
        }

        List<Long> recipeIds = page.getContent().stream()
                .map(AdminRecipeListItemDto::recipeId)
                .toList();

        Map<Long, Long> likeMap = convertReactionCountDtoListToMap(recipeReactionRepository.countByRecipeIdsAndType(recipeIds, ReactionType.LIKE));
        Map<Long, Long> dislikeMap = convertReactionCountDtoListToMap(recipeReactionRepository.countByRecipeIdsAndType(recipeIds, ReactionType.DISLIKE));
        Map<Long, Long> bookmarkMap = convertReactionCountDtoListToMap(userRecipeRepository.countByRecipeIds(recipeIds));

        return page.map(item -> new AdminRecipeListItemDto(
                item.recipeId(),
                item.recipeName(),
                item.createdAt(),
                likeMap.getOrDefault(item.recipeId(), 0L),
                dislikeMap.getOrDefault(item.recipeId(), 0L),
                bookmarkMap.getOrDefault(item.recipeId(), 0L)
        ));
    }

    private Map<Long, Long> convertReactionCountDtoListToMap(List<ReactionCountDto> countList){
        return countList.stream()
                .collect(Collectors.toMap(ReactionCountDto::recipeId, ReactionCountDto::count));

    }

    @Transactional
    public void deleteRecipe(Long recipeId){

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new KeepEatException(ErrorCode.RECIPE_NOT_FOUND));

        long bookmarkCount = userRecipeRepository.countByRecipeId(recipeId);
        if(bookmarkCount > 0){
            throw new KeepEatException(ErrorCode.RECIPE_HAS_BOOKMARK);
        }

        recipeReactionRepository.deleteAllByRecipeId(recipeId);
        recipeRepository.delete(recipe);

    }
}
