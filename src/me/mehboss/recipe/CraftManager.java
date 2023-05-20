package me.mehboss.recipe;

/*
 * Mozilla Public License v2.0
 * 
 * Author: Mehboss
 * Copyright (c) 2023 Mehboss
 * Spigot: https://www.spigotmc.org/resources/authors/mehboss.139036/
 *
 * DO NOT REMOVE THIS SECTION IF YOU WISH TO UTILIZE ANY PORTIONS OF THIS CODE
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitScheduler;

import me.mehboss.recipe.RecipeAPI.Ingredient;

public class CraftManager implements Listener {

	boolean matchedRecipe(CraftingInventory inv) {
		if (inv.getResult() == null || inv.getResult() == new ItemStack(Material.AIR)) {
			if (debug())
				debug("Could not find a recipe to match with!");

			return false;
		}
		return true;
	}

	HashMap<String, Integer> countItemsByMaterial(CraftingInventory inv) {
		HashMap<String, Integer> counts = new HashMap<>();

		for (ItemStack item : inv.getContents()) {
			if (item != null && item.getType() != Material.AIR && !(item.isSimilar(inv.getResult()))) {
				Material material = item.getType();
				String displayName = null; // Default to null if no display name is present

				if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
					displayName = item.getItemMeta().getDisplayName();
				}

				// Generate a unique key for the material and display name combination
				String key = material.toString() + "-" + displayName;

				if (debug())
					debug("countItemsByMaterial Key: " + key);

				int amount = item.getAmount();
				counts.put(key, counts.getOrDefault(key, 0) + amount);
			}
		}

		// Combine counts for items with the same material and display name
		HashMap<String, Integer> combinedCounts = new HashMap<>();
		for (HashMap.Entry<String, Integer> entry : counts.entrySet()) {
			String key = entry.getKey();
			int amount = entry.getValue();
			combinedCounts.put(key, combinedCounts.getOrDefault(key, 0) + amount);
		}

		return combinedCounts;
	}

	boolean amountsMatch(CraftingInventory inv, String recipeName) {
		for (RecipeAPI.Ingredient ingredient : getIngredients(recipeName)) {
			if (ingredient.isEmpty())
				continue;

			Material material = ingredient.getMaterial();
			String displayName = ingredient.getDisplayName();
			int requiredAmount = ingredient.getAmount();

			for (ItemStack item : inv.getMatrix()) {
				if (item != null && item.getType() == material && hasMatchingDisplayName(item, displayName)) {
					if (item.getAmount() < requiredAmount)
						return false;
				}
			}
		}

		return true; // All required amounts are present in the inventory
	}

	boolean isBlacklisted(CraftingInventory inv, Player p) {
		if (customConfig().getBoolean("blacklist-recipes") == true) {
			for (String item : disabledrecipe()) {

				String[] split = item.split(":");
				String id = split[0];
				ItemStack i = null;

				if (customConfig().getString("vanilla-recipes." + split[0]) != null
						&& !XMaterial.matchXMaterial(split[0]).isPresent()) {
					getLogger().log(Level.SEVERE, "We are having trouble matching the material '" + split[0]
							+ "' to a minecraft item. This can cause issues with the plugin. Please double check you have inputted the correct material "
							+ "ID into the blacklisted config file and try again. If this problem persists please contact Mehboss on Spigot!");
				}

				if (XMaterial.matchXMaterial(split[0]).isPresent())
					i = XMaterial.matchXMaterial(split[0]).get().parseItem();

				if (split.length == 2)
					i.setDurability(Short.valueOf(split[1]));

				if (debug())
					debug("Blacklisted Array Size: " + disabledrecipe().size());

				String getPerm = customConfig().getString("vanilla-recipes." + item + ".permission");

				if ((NBTEditor.contains(inv.getResult(), id) && !identifier().containsKey(id))
						|| inv.getResult().isSimilar(i)) {

					if (i == null) {
						getPerm = customConfig().getString("custom-recipes." + item + ".permission");
					}

					if (getPerm != null && !(getPerm.equalsIgnoreCase("none"))) {
						if (p.hasPermission("crecipe." + getPerm)) {
							if (debug())
								debug("User DOES have permission!");
							return false;
						}
					}

					if (debug())
						debug("Recipe has been set to air");

					sendMessages(p, getPerm);
					inv.setResult(new ItemStack(Material.AIR));
					return true;
				}
			}
		}
		return false;
	}

	// Updated handleShiftClicks method
	@EventHandler
	void handleShiftClicks(CraftItemEvent e) {
		CraftingInventory inv = e.getInventory();

		if (inv.getType() != InventoryType.WORKBENCH || !(matchedRecipe(inv)))
			return;

		if (!(configName().containsKey(inv.getResult())))
			return;

		if (e.getCurrentItem() == null || !e.getCurrentItem().equals(inv.getResult()))
			return;

		String recipeName = configName().get(inv.getResult());
		final ItemStack result = inv.getResult();

		if ((e.getCursor() != null && !e.getCursor().getType().equals(Material.AIR))
				|| (e.getCursor().equals(result) && result.getMaxStackSize() <= 1))
			return;

		// Remove the required items from the inventory

		int itemsToRemove = 0;
		int itemsToAdd = 0;

		for (RecipeAPI.Ingredient ingredient : getIngredients(recipeName)) {
			if (ingredient.isEmpty())
				continue;

			Material material = ingredient.getMaterial();
			String displayName = ingredient.getDisplayName();
			final int requiredAmount = ingredient.getAmount();

			for (int highest = 1; highest < 10; highest++) {
				ItemStack slot = inv.getItem(highest);
				if (slot == null)
					continue;

				if (slot.getAmount() < requiredAmount)
					break;

				if (itemsToRemove == 0 || (slot.getAmount() / requiredAmount) < itemsToRemove) {
					itemsToAdd = (slot.getAmount() / requiredAmount);
					itemsToRemove = itemsToAdd * requiredAmount;
				}
			}

			for (int i = 1; i < 10; i++) {
				ItemStack item = inv.getItem(i);

				if (item != null && item.getType() == material && hasMatchingDisplayName(item, displayName)) {
					int itemAmount = item.getAmount();

					if (itemAmount < requiredAmount)
						break;

					if (e.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) {
						BukkitScheduler scheduler = Bukkit.getScheduler();
						scheduler.runTask(Main.getInstance(), () -> {
							item.setAmount((item.getAmount() + 1) - requiredAmount);
						});
					} else {
						item.setAmount(item.getAmount() - itemsToRemove);
					}
				}
			}
		}

		// Add the result items to the player's inventory
		Player player = (Player) e.getWhoClicked();

		if (e.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) {

			BukkitScheduler scheduler = Bukkit.getScheduler();
			scheduler.runTask(Main.getInstance(), () -> {
				if (!(amountsMatch(inv, recipeName)))
					inv.setResult(new ItemStack(Material.AIR));
			});

			if (inv.getResult() != null && inv.getResult().equals(result))
				Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> e.setCursor(result), 10L);

			return;
		}

		// Check if the result can be added to the player's inventory
		// Set the cursor item to null (to prevent duplication)

		e.setCursor(null);
		inv.setResult(new ItemStack(Material.AIR));

		for (int i = 0; i < itemsToAdd; i++)
			player.getInventory().addItem(result);
	}

	public ArrayList<Ingredient> getIngredients(String recipeName) {
		ArrayList<RecipeAPI.Ingredient> allIngredients = api().getIngredients(recipeName);
		ArrayList<RecipeAPI.Ingredient> newIngredients = new ArrayList<>();

		for (RecipeAPI.Ingredient ingredient : allIngredients) {
			// Ignore the slot field and only save material, amount, and slot
			Ingredient newIngr = api().new Ingredient(ingredient.getMaterial(), ingredient.getDisplayName(), null,
					ingredient.getAmount(), 0, false);

			if (newIngr.getMaterial() != Material.AIR && newIngr.getMaterial() != null) {
				if (!newIngredients.contains(newIngr)) {
					newIngredients.add(newIngr);
				}
			}
		}

		return newIngredients;
	}

	private boolean hasMatchingDisplayName(ItemStack item, String displayName) {
		if (displayName == null || displayName.equals("false")) {
			return !item.hasItemMeta() || !item.getItemMeta().hasDisplayName();
		} else {
			return item.hasItemMeta() && item.getItemMeta().hasDisplayName()
					&& item.getItemMeta().getDisplayName().equals(displayName);
		}
	}

	boolean compareIngredients(CraftingInventory inv, String recipeName) {
		ArrayList<Material> materials = new ArrayList<Material>();

		for (Ingredient ingredient : api().getIngredients(recipeName)) {
			if (!(ingredient.isEmpty()))
				materials.add(ingredient.getMaterial());
		}

		for (ItemStack item : inv.getContents()) {
			if (item == null || item.getType() == Material.AIR)
				continue;
			if (!(materials.contains(item.getType())))
				return false;
		}
		return true;
	}

	@EventHandler
	void handleCrafting(PrepareItemCraftEvent e) {

		CraftingInventory inv = e.getInventory();

		Boolean passedCheck = true;
		String recipeName = null;

		if (!(e.getView().getPlayer() instanceof Player))
			return;

		Player p = (Player) e.getView().getPlayer();

		if (inv.getType() != InventoryType.WORKBENCH || !(matchedRecipe(inv)) || isBlacklisted(inv, p))
			return;

		recipeName = configName().get(inv.getResult());

		if (recipeName == null || !(api().hasRecipe(recipeName)))
			return;

		if (debug())
			debug("Recipe Config: " + recipeName);

		if (getConfig().isSet("Items." + recipeName + ".Ignore-Data")
				&& getConfig().getBoolean("Items." + recipeName + ".Ignore-Data") == true)
			return;

		for (String recipes : api().getRecipes()) {
			ArrayList<RecipeAPI.Ingredient> recipeIngredients = api().getIngredients(recipes);

			recipeName = recipes;
			passedCheck = true;

			if (getConfig().isBoolean("Items." + recipeName + ".Shapeless")
					&& getConfig().getBoolean("Items." + recipeName + ".Shapeless") == true) {
				// runs checks if recipe is shapeless

				ArrayList<String> slotNames = new ArrayList<String>();
				ArrayList<String> recipeNames = new ArrayList<String>();

				for (int slot = 1; slot < 10; slot++) {
					if (inv.getItem(slot) == null || !(inv.getItem(slot).getItemMeta().hasDisplayName())) {
						slotNames.add("false");
						continue;
					}

					if (!(NBTEditor.contains(inv.getItem(slot), "CUSTOM_ITEM_IDENTIFIER")))
						break;

					slotNames.add(inv.getItem(slot).getItemMeta().getDisplayName());
				}

				for (RecipeAPI.Ingredient names : recipeIngredients) {

					if (names.getMaterial() != null && !(inv.contains(names.getMaterial())))
						break;

					if (names.hasIdentifier() && identifier().containsKey(names.getIdentifier())) {
						if (identifier().get(names.getIdentifier()).getItemMeta().hasDisplayName())
							recipeNames.add(identifier().get(names.getIdentifier()).getItemMeta().getDisplayName());
						else
							recipeNames.add("false");
						continue;
					}
					recipeNames.add(names.getDisplayName());
				}

				if (debug()) {
					debug("ContainsAll: " + slotNames.containsAll(recipeNames) + " | AmountsMatch: "
							+ amountsMatch(inv, recipeName));

					getLogger().log(Level.SEVERE, "SN SIZE: " + slotNames.size());
					getLogger().log(Level.SEVERE, "RN SIZE: " + recipeNames.size());
					for (String names : slotNames)
						getLogger().log(Level.SEVERE, names);
					for (String names : recipeNames)
						getLogger().log(Level.SEVERE, names);
				}

				if (slotNames.size() != 9 || recipeNames.size() != 9 || !(slotNames.containsAll(recipeNames))
						|| !(amountsMatch(inv, recipeName))) {
					passedCheck = false;
					continue;
				}

			} else {

				// runs check for non-shapeless recipes
				if (debug())
					debug("Checking for RECIPE: " + recipeName);

				int i = 0;

				for (RecipeAPI.Ingredient ingredient : recipeIngredients) {
					i++;

					if (ingredient.getMaterial() != null && !(inv.contains(ingredient.getMaterial()))) {
						passedCheck = false;
						break;
					}

					if (inv.getItem(i) == null && !(ingredient.isEmpty())) {
						passedCheck = false;

						if (debug())
							debug("[1] SKIPPING: " + recipeName);
						break;
					}

					if (inv.getItem(i) != null) {
						ItemMeta meta = inv.getItem(i).getItemMeta();

						if (ingredient.hasIdentifier() && identifier().containsKey(ingredient.getIdentifier())
								&& identifier().get(ingredient.getIdentifier()).equals(inv.getItem(i)))
							continue;

						// checks for custom tag
						if (meta.hasDisplayName() && !(NBTEditor.contains(inv.getItem(i), "CUSTOM_ITEM_IDENTIFIER"))) {
							passedCheck = false;
							if (debug())
								debug("[2] SKIPPING: " + recipeName);
							break;
						}

						// checks if displayname is null
						if ((!meta.hasDisplayName() && ingredient.hasDisplayName())
								|| (meta.hasDisplayName() && !ingredient.hasDisplayName())) {
							passedCheck = false;
							if (debug()) {
								debug("[3] SKIPPING: " + recipeName);
								debug("[3] IngDN: " + ingredient.hasDisplayName());
								debug("[3] MetaDN: " + meta.hasDisplayName());
								debug("[3] IngSlot: " + ingredient.getSlot());
								debug("[3] IngDN: " + ingredient.hasDisplayName());
							}
							break;
						}

						if (ingredient.hasDisplayName() && meta.hasDisplayName()
								&& !(ingredient.getDisplayName().equals(meta.getDisplayName()))) {
							passedCheck = false;
							if (debug()) {
								debug("[4] SKIPPING: " + recipeName);
								debug("[4] ING DN: " + ingredient.getDisplayName());
								debug("[4] META DN: " + meta.getDisplayName());
								break;
							}
						}

						// checks amounts
						if (!(amountsMatch(inv, recipeName))) {
							if (debug())
								debug("[5] SKIPPING: " + recipeName);
							passedCheck = false;
							break;
						}
					}
				}
				if (passedCheck)
					break;
			}
		}

		if (passedCheck && (getConfig().getBoolean("Items." + recipeName + ".Enabled") == false
				|| ((getConfig().isSet("Items." + recipeName + ".Permission")
						&& (!(p.hasPermission(getConfig().getString("Items." + recipeName + ".Permission")))))))) {
			inv.setResult(new ItemStack(Material.AIR));
			sendNoPermsMessage(p);
			return;
		}

		if (!(passedCheck))
			inv.setResult(new ItemStack(Material.AIR));

		if (debug())
			debug("Final Recipe Match: " + passedCheck + "| Recipe Pulled: " + recipeName);
	}

	boolean debug() {
		return Main.getInstance().debug;
	}

	void debug(String st) {
		getLogger().log(Level.WARNING, "-----------------");
		getLogger().log(Level.WARNING, "DEBUG IS TURNED ON! PLEASE CONTACT MEHBOSS ON SPIGOT FOR ASSISTANCE");
		getLogger().log(Level.WARNING, st);
		getLogger().log(Level.WARNING, "-----------------");
	}

	void sendMessages(Player p, String s) {
		Main.getInstance().sendMessages(p, s);
	}

	void sendNoPermsMessage(Player p) {
		if (debug())
			debug("Sending noPERMS message now");

		Main.getInstance().sendMessage(p);
	}

	HashMap<String, ItemStack> identifier() {
		return Main.getInstance().identifier;
	}

	ArrayList<String> disabledrecipe() {
		return Main.getInstance().disabledrecipe;
	}

	HashMap<String, ItemStack> getRecipe() {
		return Main.getInstance().giveRecipe;
	}

	HashMap<ItemStack, String> configName() {
		return Main.getInstance().configName;
	}

	Logger getLogger() {
		return Main.getInstance().getLogger();
	}

	FileConfiguration customConfig() {
		return Main.getInstance().customConfig;
	}

	FileConfiguration getConfig() {
		return Main.getInstance().getConfig();
	}

	RecipeAPI api() {
		return Main.getInstance().api;
	}
}
