CREATE INDEX idx_recipe_ingredient_recipe_id ON recipe_ingredient USING btree (recipe_id);
CREATE INDEX idx_recipe_ingredient_ingredient_id ON recipe_ingredient USING btree (ingredient_id);
CREATE INDEX idx_recipe_created_at_id ON recipe USING btree (created_at DESC, id DESC);
