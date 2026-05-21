package com.keepeat.backend.domain.recipe;

import com.keepeat.backend.domain.common.enums.ReactionType;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.recipe.entity.Recipe;
import com.keepeat.backend.domain.recipe.entity.RecipeReaction;
import com.keepeat.backend.domain.recipe.repository.RecipeReactionRepository;
import com.keepeat.backend.domain.recipe.repository.RecipeRepository;
import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecipeReactionService {
    private final RecipeReactionRepository reactionRepository;
    private final RecipeRepository recipeRepository;
    private final AppUserRepository appUserRepository;

    @Transactional
    public void react(Long userId, Long recipeId, ReactionType newType){
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new KeepEatException(ErrorCode.RECIPE_NOT_FOUND));


        Optional<RecipeReaction> existing = reactionRepository.findByAppUserIdAndRecipeId(userId, recipeId);

        if(existing.isEmpty()){
            AppUser user = appUserRepository.findById(userId)
                            .orElseThrow(() -> new KeepEatException(ErrorCode.USER_NOT_FOUND));

            try{
                reactionRepository.save(
                        RecipeReaction.builder()
                                .recipe(recipe)
                                .reactionType(newType)
                                .appUser(user)
                                .createdAt(LocalDate.now())
                                .build()
                );
            }catch (DataIntegrityViolationException e){
                //
            }

        }else {
            RecipeReaction reaction = existing.get();
            if(reaction.getReactionType() != newType){
                reaction.changeType(newType);
            }else{
                reactionRepository.delete(reaction);
            }
        }
    }
}
