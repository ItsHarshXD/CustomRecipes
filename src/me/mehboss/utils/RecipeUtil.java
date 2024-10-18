package me.mehboss.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import io.github.bananapuncher714.nbteditor.NBTEditor;
import me.mehboss.recipe.Main;

public class RecipeUtil {
	private HashMap<String, Recipe> recipes = new HashMap<>();
	private ArrayList<String> keyList = new ArrayList<>();

	/**
	 * Adds a finished Recipe object to the API
	 * 
	 * @param recipe the Recipe object
	 * @throws InvalidRecipeException if the recipe is null or no NamespacedKey has been set
	 * @throws InvalidRecipeException if the crafting recipe does not have 9 ingredients
	 * @throws InvalidRecipeException if the furnace or stone cutter has more than 1 ingredient
	 * @throws InvalidRecipeException if the result is null or air
	 * @throws InvalidRecipeException if the row shape of the shaped recipe is null
	 */
	public void createRecipe(Recipe recipe) {
		if (recipe == null || !recipe.hasKey()) {
			String errorMessage = "[CRAPI] Could not add recipe: " + recipe.getName()
					+ ". Recipe is null or does not have a NameSpacedKey set. Key: " + recipe.getKey();
			throw new InvalidRecipeException(errorMessage);
		}

		if (recipe.getType() != RecipeUtil.Recipe.RecipeType.SHAPED
				&& recipe.getType() != RecipeUtil.Recipe.RecipeType.SHAPELESS && recipe.getIngredientSize() != 9) {
			String errorMessage = "[CRAPI] Could not add recipe: " + recipe.getName() + ". Recipe is "
					+ recipe.getType() + " and does not have 9 ingredients! Ingredients: " + recipe.getIngredientSize();
			throw new InvalidRecipeException(errorMessage);
		}

		if ((recipe.getType() == RecipeUtil.Recipe.RecipeType.FURNACE
				|| recipe.getType() == RecipeUtil.Recipe.RecipeType.STONECUTTER) && recipe.getIngredientSize() != 1) {
			String errorMessage = "[CRAPI] Could not add recipe: " + recipe.getName() + ". Recipe is "
					+ recipe.getType() + " and has more than 1 ingredient! Ingredients: " + recipe.getIngredientSize();
			throw new InvalidRecipeException(errorMessage);
		}

		if (recipe.getResult() == null || recipe.getResult().getType() == Material.AIR) {
			String errorMessage = "[CRAPI] Could not add recipe: " + recipe.getName()
					+ ". The recipe result was null or not set.";
			throw new InvalidRecipeException(errorMessage);
		}

		if (recipe.getType() != RecipeUtil.Recipe.RecipeType.SHAPED
				&& (recipe.getRow(1) == null || recipe.getRow(2) == null || recipe.getRow(3) == null)) {
			String errorMessage = "[CRAPI] Could not add recipe because shape cannot have null rows. Recipe: "
					+ recipe.getName();
			throw new InvalidRecipeException(errorMessage);
		}

		recipes.put(recipe.getName(), recipe);
		keyList.add(recipe.getKey());
	}

	/**
	 * Removes a recipe registered with CR.
	 * 
	 * @param recipeName the string name of the recipe you wanting to remove.
	 */
	public void removeRecipe(String recipeName) {
		recipes.remove(recipeName);
	}

	/**
	 * Getter for a recipe from the result ItemStack
	 * 
	 * @param item the ItemStack
	 * @return the Recipe that is found, can be null
	 */
	public Recipe getRecipeFromResult(ItemStack item) {
		for (Recipe recipe : recipes.values()) {
			ItemStack result = recipe.getResult();

			if (result.equals(item))
				return recipe;
		}
		return null;
	}

	/**
	 * Getter for a recipe from Namespacedkey
	 * 
	 * @param key the NamespacedKey
	 * @return the Recipe that is found, can be null
	 */
	public Recipe getRecipeFromKey(String key) {
		for (Recipe recipe : recipes.values()) {
			String recipeTag = recipe.getKey();

			if (key.equals(recipeTag))
				return recipe;
		}
		return null;
	}

	/**
	 * Getter for a recipe from the Name
	 * 
	 * @param recipeName the recipe name
	 * @return the Recipe that is found, can be null
	 */
	public Recipe getRecipe(String recipeName) {
		return recipes.get(recipeName);
	}

	/**
	 * Getter for all recipes registered
	 * 
	 * @return a hashmap of recipes, including CR recipes
	 */
	public HashMap<String, Recipe> getAllRecipes() {
		return recipes;
	}

	/**
	 * Getter for all recipe results
	 * 
	 * @return an arraylist of recipe ItemStacks
	 */
	public ArrayList<ItemStack> getAllResults() {
		ArrayList<ItemStack> results = new ArrayList<>();
		for (Recipe recipe : recipes.values()) {
			ItemStack result = recipe.getResult();
			if (result != null) {
				results.add(result);
			}
		}
		return results;
	}

	/**
	 * Getter for all recipe names
	 * 
	 * @return a set of strings
	 */
	public Set<String> getRecipeNames() {
		return recipes.keySet();
	}

	/**
	 * Resets and loads all registered recipes, including CR recipes
	 */
	public void reloadRecipes() {
		this.clearDuplicates();
		Main.getInstance().plugin.addRecipesFromAPI();
	}

	/**
	 * Clears all registered recipes, private
	 */
	private void clearDuplicates() {
		if (Main.getInstance().serverVersionAtLeast(1, 12))
			for (String getKey : keyList) {
				if (getKey == null)
					continue;

				String key = getKey.toLowerCase();
				NamespacedKey customKey = NamespacedKey.fromString("customrecipes:" + key);

				if (customKey != null && Bukkit.getRecipe(customKey) != null)
					Bukkit.removeRecipe(customKey);
			}
	}

	public static class Recipe {

		private ItemStack result;

		private ArrayList<String> disabledWorlds = new ArrayList<>();
		private ArrayList<Ingredient> ingredients;

		private String name;
		private String key;
		private String permission;

		private boolean bucketConsume = true;
		private boolean active = true;
		private boolean ignoreData = false;
		private boolean ignoreModelData = false;
		private boolean isTagged = false;

		private String row1;
		private String row2;
		private String row3;

		private int cookTime = 200;
		private float furnaceExperience = 1.0f;

		public enum RecipeType {
			SHAPELESS, SHAPED, STONECUTTER, FURNACE;
		}

		private RecipeType recipeType = RecipeType.SHAPED;

		/**
		 * Parameterized constructor. 
		 * Initializes a Recipe object with specified name
		 *
		 * @param name the name of the recipe
		 * 
		 */
		public Recipe(String name) {
			this.name = name;
			this.ingredients = new ArrayList<>();
		}

		/**
		 * Getter for setActive.
		 * 
		 * @return true if the recipe is enabled, false otherwise.
		 */
		public boolean isActive() {
			return active;
		}

		/**
		 * Enables or disables a recipe
		 * 
		 * @param status true or false boolean
		 */
		public void setActive(Boolean status) {
			active = status;
		}

		/**
		 * Getter for the recipe name
		 * 
		 * @return what the recipe name is
		 */
		public String getName() {
			return name;
		}

		/**
		 * Setter for the shaped recipe rows, required
		 * 
		 * @param row the recipe row
		 * @param shape the shape for the row (example: XDX)
		 * @throws ArrayIndexOutOfBoundsException if the integer specified is not rows 1-3
		 */
		public void setRow(int row, String shape) {

			switch (row) {
			case 1:
				row1 = shape;
				break;
			case 2:
				row2 = shape;
				break;
			case 3:
				row3 = shape;
				break;
			default:
				throw new ArrayIndexOutOfBoundsException("Invalid setRow usage: " + row + " (must be 1-3)");
			}
		}

		/**
		 * Getter for the shaped recipe rows
		 * 
		 * @param row the recipe row
		 * @throws ArrayIndexOutOfBoundsException if the integer specified is not rows 1-3
		 */
		public String getRow(int row) {
			switch (row) {
			case 1:
				return row1;
			case 2:
				return row2;
			case 3:
				return row3;
			default:
				throw new ArrayIndexOutOfBoundsException("Invalid getRow usage: " + row + " (must be 1-3)");
			}
		}

		/**
		 * Setter for the RecipeType, not required and defaults to SHAPED
		 * 
		 * @param SHAPELESS the recipe type will be shapeless
		 * @param SHAPED the recipe type will be shaped
		 * @param FURNACE the recipe type will be a furnace recipe
		 * @param STONECUTTER the recipe type will be a stonecutter recipe
		 */
		public void setType(RecipeType type) {
			recipeType = type;
		}

		/**
		 * Getter for the RecipeType
		 * 
		 * @returns the RecipeType
		 *          RecipeType.SHAPELESS 
		 *          RecipeType.SHAPED 
		 *          RecipeType.FURNACE
		 *          RecipeType.STONECUTTER
		 */
		public RecipeType getType() {
			return recipeType;
		}

		/**
		 * Setter for whether the result is tagged with the key
		 * 
		 * @param tagged tags if true, otherwise does not tag
		 * @throws InvalidRecipeException if the recipe result is null
		 * @throws InvalidRecipeException if there is no key found
		 */
		public void setTagged(boolean tagged) {
			if (result == null) {
				throw new InvalidRecipeException("There was no result found to tag");
			}
			if (key == null) {
				throw new InvalidRecipeException(
						"You must set a NameSpacedKey (setKey()) prior to calling setTagged()");
			} else {
				this.isTagged = tagged;
				result = NBTEditor.set(result, key, NBTEditor.CUSTOM_DATA, "CUSTOM_ITEM_IDENTIFIER");
			}
		}

		/**
		 * Getter for checking if the recipe is tagged with the key
		 * 
		 * @returns true if tagged, false otherwise
		 */
		public boolean isCustomTagged() {
			return this.isTagged;
		}

		/**
		 * Setter for the NamedspaceKey, required
		 * 
		 * @param key the NamedspaceKey
		 */
		public void setKey(String key) {
			this.key = key;
		}

		/**
		 * Getter for the NamedspaceKey
		 * 
		 * @returns the NamedspaceKey of the recipe
		 */
		public String getKey() {
			return key;
		}

		/**
		 * Getter for if the recipe has a NamedspaceKey
		 * 
		 * @returns the NamedspaceKey of the recipe
		 */
		public boolean hasKey() {
			return key == null ? false : true;
		}

		/**
		 * Setter for if the recipe ignores MetaData
		 * 
		 * @param ignoreData true to ignore, otherwise false
		 */
		public void setIgnoreData(boolean ignoreData) {
			this.ignoreData = ignoreData;
		}

		/**
		 * Getter for ignoreData
		 * 
		 * @returns true of the recipe ignores MetaData, false otherwise
		 */
		public boolean getIgnoreData() {
			return ignoreData;
		}

		/**
		 * Setter for if the recipe ignores CustomModelData
		 * 
		 * @param ignoreModelData true to ignore, otherwise false
		 */
		public void setIgnoreModelData(boolean ignoreModelData) {
			this.ignoreModelData = ignoreModelData;
		}

		/**
		 * Getter for ignoreModelData
		 * 
		 * @returns true of the recipe ignores CustomMetaData, false otherwise
		 */
		public boolean getIgnoreModelData() {
			return this.ignoreModelData;
		}

		/**
		 * Setter for the recipe result, required
		 * 
		 * @param result the ItemStack for the recipe result
		 */
		public void setResult(ItemStack result) {
			if (result == null || result.getType() == Material.AIR) {
				String errorMessage = "[CRAPI] The recipe result can not be set to null or air";
				throw new InvalidRecipeException(errorMessage);
			}

			this.result = result;
		}

		/**
		 * Getter for the recipe result
		 * 
		 * @returns the ItemStack of the recipe result
		 */
		public ItemStack getResult() {
			return result;
		}

		/**
		 * Adds an Ingredient object to the recipe, required 
		 * Requires 9 ingredients for crafting, 1 otherwise
		 * 
		 * @param ingredient a finalized Ingredient for the recipe
		 */
		public void addIngredient(Ingredient ingredient) {
			this.ingredients.add(ingredient);
		}

		/**
		 * Getter for the recipe ingredients
		 * 
		 * @returns a list of Ingredients
		 */
		public ArrayList<Ingredient> getIngredients() {
			return ingredients;
		}

		/**
		 * Getter for the amount of ingredients the recipe has
		 * 
		 * @returns the amount of ingredients the recipe has, can be null
		 */
		public int getIngredientSize() {
			return ingredients.size();
		}

		/**
		 * Setter for the cook time of a furnace recipe
		 * 
		 * @param cooktime how long it takes to cook the ingredient
		 */
		public void setCookTime(int cooktime) {
			this.cookTime = cooktime;
		}

		/**
		 * Getter for the cook time of a furnace recipe
		 * 
		 * @returns how long it takes to cook the ingredient
		 */
		public int getCookTime() {
			return cookTime;
		}

		/**
		 * Setter for the experience given from a furnace recipe
		 * 
		 * @param experience the amount of experience given
		 */
		public void setExperience(float experience) {
			this.furnaceExperience = experience;
		}

		/**
		 * Getter for the furnace experience of a furnace recipe result
		 * 
		 * @returns the amount of experience granted from a furnace result
		 */
		public float getExperience() {
			return furnaceExperience;
		}

		/**
		 * Getter for an ingredient slot
		 * 
		 * @param slot integer of the slot (1-9)
		 * @returns the Ingredient found that is in the specified slot
		 * @throws ArrayIndexOutOfBoundsException if param is not 1-9
		 */
		public Ingredient getSlot(int slot) throws ArrayIndexOutOfBoundsException {
			return ingredients.get(slot - 1);
		}

		/**
		 * Setter for the permission needed to craft the recipe
		 * 
		 * @param permission the permission required
		 */
		public void setPerm(String permission) {
			this.permission = permission;
		}

		/**
		 * Getter for the permission needed to craft the recipe, can be null
		 * 
		 * @returns the permission
		 */
		public String getPerm() {
			return permission;
		}

		/**
		 * Adds a world to the noCraft list
		 * 
		 * @param world the world to not allow crafting of the recipe in
		 */
		public void addDisabledWorld(String world) {
			disabledWorlds.add(world);
		}

		/**
		 * Getter for worlds the recipe can not be crafted in
		 * 
		 * @returns an arraylist of worlds
		 */
		public ArrayList<String> getDisabledWorlds() {
			return disabledWorlds;
		}

		/**
		 * Setter for whether a bucket is consumed or emptied
		 * 
		 * @param consume true if the bucket is set to consume, false if the bucket will empty
		 */
		public void setConsume(Boolean consume) {
			this.bucketConsume = consume;
		}

		/**
		 * Getter for whether a bucket is consumed or emptied
		 * 
		 * @returns true if the bucket is set to consume, false if the bucket will empty
		 */
		public Boolean isConsume() {
			return bucketConsume;
		}
	}

	public static class Ingredient {
		private Material material;

		private String abbreviation;
		private String displayName;
		private String identifier;
		private int slot = 0;
		private int amount = 1;

		/**
		 * Parameterized constructor. 
		 * Initializes an Ingredient object with specified
		 * abbreviation and material
		 *
		 * @param abbreviation the letter of the ingredient
		 * @param material     the Material type for the ingredient, can be null or air
		 */
		public Ingredient(String abbreviation, Material material) {
			this.material = material;
			this.abbreviation = abbreviation;
		}

		/**
		 * Setter for the ingredients displayname
		 * 
		 * @param displayname the name the ingredient is required to have
		 */
		public void setDisplayName(String displayName) {
			this.displayName = displayName;
		}

		/**
		 * Setter for the identifier of the ingredient 
		 * Requires the ingredient to be tagged with another Custom Recipe
		 * 
		 * @param identifier the identifier/tag the ingredient is required to have
		 */
		public void setIdentifier(String identifier) {
			if (!(identifier.equalsIgnoreCase("none")))
				this.identifier = identifier;
		}

		/**
		 * Getter for an ingredients identifier
		 * 
		 * @returns the ingredient identifier, can be null
		 */
		public String getIdentifier() {
			return identifier;
		}

		/**
		 * Getter for if the ingredient has an identifier set
		 * 
		 * @returns true if the ingredient has an identifier, false otherwise
		 */
		public boolean hasIdentifier() {
			return identifier == null ? false : true;
		}

		/**
		 * Setter for the slot the ingredient is set to, not required for shapeless
		 * 
		 * @param slot the inventory slot the ingredient is set to
		 * @throws ArrayIndexOutOfBoundsException if the slot is not within 1-9
		 */
		public void setSlot(int slot) {
			if (slot < 1 || slot > 9)
				throw new ArrayIndexOutOfBoundsException("Invalid setSlot usage: " + slot + " (must be 1-9)");

			this.slot = slot;
		}

		/**
		 * Getter for the ingredient slot
		 * 
		 * @returns the ingredient slot the ingredient is set to
		 */
		public int getSlot() {
			return slot;
		}

		/**
		 * Setter for the amount requirement
		 * 
		 * @param amount the amount of the ingredient the recipe requires
		 */
		public void setAmount(int amount) {
			this.amount = amount;
		}

		/**
		 * Getter for the amount requirement
		 * 
		 * @returns the amount of the ingredient the recipe requires
		 */
		public int getAmount() {
			return amount;
		}

		/**
		 * Getter for checking if the ingredient is empty
		 * 
		 * @returns true if the ingredient is AIR or NULL, false otherwise
		 */
		public boolean isEmpty() {
			return material == null || material == Material.AIR;
		}

		/**
		 * Getter for the ingredient displayname
		 * 
		 * @returns the displayname of the ingredient, can be null
		 */
		public String getDisplayName() {
			return ChatColor.translateAlternateColorCodes('&', displayName);
		}

		/**
		 * Getter for if the ingredient has a displayname set
		 * 
		 * @returns true if the ingredient has one, false otherwise
		 */
		public boolean hasDisplayName() {
			return displayName == null ? false : true;
		}

		/**
		 * Getter for the material of the ingredient
		 * 
		 * @returns the material of the ingredient, AIR if null
		 */
		public Material getMaterial() {
			return material == null ? Material.AIR : material;
		}

		/**
		 * Getter for the abbreviation of the ingredient, like G for grass 
		 * Used when shaping the recipe
		 * 
		 * @returns the abbreviation of the ingredient
		 */
		public String getAbbreviation() {
			return abbreviation;
		}
	}
}