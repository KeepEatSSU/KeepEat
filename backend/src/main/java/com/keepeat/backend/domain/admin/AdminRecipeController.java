package com.keepeat.backend.domain.admin;

import com.keepeat.backend.domain.admin.dto.AdminRecipeListItemDto;
import com.keepeat.backend.domain.common.enums.AdminRecipeSearchSort;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;


@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/recipes")
public class AdminRecipeController {

    private final AdminRecipeService adminRecipeService;

    @GetMapping
    public String list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AdminRecipeSearchSort sort,
            @RequestParam(required = false, defaultValue = "false") boolean onlyDeletable,
            @RequestParam(required = false) String msg,
            @PageableDefault(value = 20) Pageable pageable,
            Model model
    ){
        Page<AdminRecipeListItemDto> page =
                adminRecipeService.getRecipeListForAdmin(keyword, sort, onlyDeletable, pageable);

        model.addAttribute("page", page);
        model.addAttribute("keyword", keyword);
        model.addAttribute("sort", sort == null ? AdminRecipeSearchSort.LATEST : sort);
        model.addAttribute("onlyDeletable", onlyDeletable);
        model.addAttribute("sortOptions", AdminRecipeSearchSort.values());
        model.addAttribute("msg", msg);

        return "admin/recipe-list";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        try {
            adminRecipeService.deleteRecipe(id);
            return "redirect:/admin/recipes?msg=deleted";
        } catch (KeepEatException e) {
            if (e.getErrorCode() == ErrorCode.RECIPE_HAS_BOOKMARK) {
                return "redirect:/admin/recipes?msg=delete_blocked";
            }
            return "redirect:/admin/recipes?msg=delete_failed";
        }
    }
}
