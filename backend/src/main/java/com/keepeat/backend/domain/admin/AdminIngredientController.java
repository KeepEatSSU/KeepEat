package com.keepeat.backend.domain.admin;

import com.keepeat.backend.domain.admin.dto.IngredientFormDto;
import com.keepeat.backend.domain.admin.dto.IngredientListItemDto;
import com.keepeat.backend.domain.common.enums.Metric;
import com.keepeat.backend.domain.common.enums.StorageType;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/ingredients")
@RequiredArgsConstructor
public class AdminIngredientController {

    private final AdminIngredientService adminIngredientService;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Long subCategoryId,
                       @RequestParam(required = false) String msg,
                       @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
                       Model model) {
        Page<IngredientListItemDto> page = adminIngredientService.findAll(keyword, subCategoryId, pageable);
        model.addAttribute("page", page);
        model.addAttribute("keyword", keyword);
        model.addAttribute("subCategoryId", subCategoryId);
        model.addAttribute("subCategories", adminIngredientService.findAllSubCategoriesForSelect());
        model.addAttribute("msg", msg);
        return "admin/ingredient-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new IngredientFormDto());
        }
        addFormSelectAttributes(model);
        model.addAttribute("mode", "new");
        return "admin/ingredient-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") IngredientFormDto form,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            addFormSelectAttributes(model);
            model.addAttribute("mode", "new");
            return "admin/ingredient-form";
        }
        try {
            adminIngredientService.create(form);
            return "redirect:/admin/ingredients?msg=created";
        } catch (KeepEatException e) {
            model.addAttribute("error", e.getMessage());
            addFormSelectAttributes(model);
            model.addAttribute("mode", "new");
            return "admin/ingredient-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", adminIngredientService.findOne(id));
        }
        addFormSelectAttributes(model);
        model.addAttribute("mode", "edit");
        model.addAttribute("ingredientId", id);
        return "admin/ingredient-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") IngredientFormDto form,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            addFormSelectAttributes(model);
            model.addAttribute("mode", "edit");
            model.addAttribute("ingredientId", id);
            return "admin/ingredient-form";
        }
        try {
            adminIngredientService.update(id, form);
            return "redirect:/admin/ingredients?msg=updated";
        } catch (KeepEatException e) {
            model.addAttribute("error", e.getMessage());
            addFormSelectAttributes(model);
            model.addAttribute("mode", "edit");
            model.addAttribute("ingredientId", id);
            return "admin/ingredient-form";
        }
    }

    private void addFormSelectAttributes(Model model) {
        model.addAttribute("subCategories", adminIngredientService.findAllSubCategoriesForSelect());
        model.addAttribute("storageTypes", StorageType.values());
        model.addAttribute("metrics", Metric.values());
    }
}
