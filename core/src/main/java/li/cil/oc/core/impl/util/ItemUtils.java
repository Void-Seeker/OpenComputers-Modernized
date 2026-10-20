package li.cil.oc.core.impl.util;

import java.util.ArrayList;
import java.util.List;
import li.cil.oc.core.Constants;
import li.cil.oc.core.common.Tier;
import li.cil.oc.core.impl.OCSettings;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class ItemUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(ItemUtils.class);

  public static int caseTier(ItemStack stack) {
    var descriptor = li.cil.oc.api.Items.get(stack);
    if (descriptor == li.cil.oc.api.Items.get(Constants.BlockName.CaseTier1)) return Tier.One;
    else if (descriptor == li.cil.oc.api.Items.get(Constants.BlockName.CaseTier2)) return Tier.Two;
    else if (descriptor == li.cil.oc.api.Items.get(Constants.BlockName.CaseTier3)) return Tier.Three;
    else if (descriptor == li.cil.oc.api.Items.get(Constants.BlockName.CaseCreative)) return Tier.Four;
    else if (descriptor == li.cil.oc.api.Items.get(Constants.ItemName.MicrocontrollerCaseTier1)) return Tier.One;
    else if (descriptor == li.cil.oc.api.Items.get(Constants.ItemName.MicrocontrollerCaseTier2)) return Tier.Two;
    else if (descriptor == li.cil.oc.api.Items.get(Constants.ItemName.MicrocontrollerCaseCreative))
      return Tier.Four;
    else if (descriptor == li.cil.oc.api.Items.get(Constants.ItemName.DroneCaseTier1)) return Tier.One;
    else if (descriptor == li.cil.oc.api.Items.get(Constants.ItemName.DroneCaseTier2)) return Tier.Two;
    else if (descriptor == li.cil.oc.api.Items.get(Constants.ItemName.DroneCaseCreative)) return Tier.Four;
    else if (descriptor == li.cil.oc.api.Items.get(Constants.ItemName.ServerTier1)) return Tier.One;
    else if (descriptor == li.cil.oc.api.Items.get(Constants.ItemName.ServerTier2)) return Tier.Two;
    else if (descriptor == li.cil.oc.api.Items.get(Constants.ItemName.ServerTier3)) return Tier.Three;
    else if (descriptor == li.cil.oc.api.Items.get(Constants.ItemName.ServerCreative)) return Tier.Four;
    else if (descriptor == li.cil.oc.api.Items.get(Constants.ItemName.TabletCaseTier1)) return Tier.One;
    else if (descriptor == li.cil.oc.api.Items.get(Constants.ItemName.TabletCaseTier2)) return Tier.Two;
    else if (descriptor == li.cil.oc.api.Items.get(Constants.ItemName.TabletCaseCreative)) return Tier.Four;
    else return Tier.None;
  }

  public static ItemStack[] getIngredients(ItemStack stack) {
    try {
      var server = li.cil.oc.core.impl.util.SideTracker.getCurrentServer();
      if (server == null) return new ItemStack[0];
      var recipeManager = server.getRecipeManager();
      var craftingRecipes = recipeManager.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING);

      // a recipe making the item as it is first (a loot disk's own, a dyed floppy's), then any making the item: its data
      // does not count (OC: isItemEqual - a crafted navigation upgrade carries its map, a used card its address, a
      // configured server its parts: none had parts, and their templates none to give back), its damage does
      for (boolean exact : new boolean[]{true, false}) {
        for (var recipe : craftingRecipes) {
          var result = recipe.getResultItem(server.registryAccess());
          if (ItemStack.isSameItem(result, stack) && result.getDamageValue() == stack.getDamageValue() && ItemStack.isSameItemSameTags(result, stack) == exact) {
            var parts = parts(recipe.getIngredients(), result, stack);
            if (parts != null) return parts;
          }
        }
      }
      return new ItemStack[0];
    } catch (Throwable t) {
      LOGGER.warn("Whoops, something went wrong when trying to figure out an item's parts.", t);
      return new ItemStack[0];
    }
  }

  // the recipe's parts of one item, or null when the item is not taken apart by it (a blacklisted input, the item itself)
  private static ItemStack[] parts(List<Ingredient> ings, ItemStack result, ItemStack stack) {
    List<ItemStack> stacks = new ArrayList<>();
    for (var ingr : ings) {
      var chosen = pick(ingr.getItems());
      // what crafting gives back is no part (OC stripped buckets: acid gave its water bucket back a second time)
      if (chosen.isEmpty() || chosen.getItem().hasCraftingRemainingItem() || chosen.getItem() instanceof BucketItem) continue;
      if (isInputBlacklisted(chosen)) return null;
      stacks.add(chosen);
    }
    // one item's share of what the recipe takes (it gave the whole recipe for each: 8x for a transistor), and
    // never a recipe that takes the item itself (an EEPROM copy recipe made 2 out of 1)
    if (stacks.stream().anyMatch(s -> sameItem(s, stack))) return null;
    List<ItemStack> merged = new ArrayList<>();
    for (var s : stacks) {
      var same = merged.stream().filter(m -> ItemStack.isSameItemSameTags(m, s)).findFirst();
      if (same.isPresent()) same.get().grow(s.getCount());
      else merged.add(s.copy());
    }
    int made = Math.max(1, result.getCount());
    List<ItemStack> share = new ArrayList<>();
    for (var m : merged) {
      int n = m.getCount() / made;
      if (n > 0) share.add(m.copyWithCount(n));
    }
    return share.toArray(new ItemStack[0]);
  }

  // the earliest registered of the items it takes (vanilla before modded, and vanilla's plain blocks before the others:
  // a chest, not an ender chest; an iron block, not a netherite one) - it was a random one of them
  private static ItemStack pick(ItemStack[] items) {
    ItemStack chosen = ItemStack.EMPTY;
    int first = Integer.MAX_VALUE;
    for (var item : items) {
      int id = BuiltInRegistries.ITEM.getId(item.getItem());
      if (!item.isEmpty() && id < first) {
        first = id;
        chosen = item;
      }
    }
    return chosen.copy();
  }

  // a beacon base print is a print (one item in OC): its recipe takes the print itself
  private static boolean sameItem(ItemStack a, ItemStack b) {
    return ItemStack.isSameItem(a, b) || isPrint(a) && isPrint(b);
  }

  private static boolean isPrint(ItemStack stack) {
    var descriptor = li.cil.oc.api.Items.get(stack);
    return descriptor != null && (descriptor == li.cil.oc.api.Items.get(Constants.BlockName.Print) || descriptor == li.cil.oc.api.Items.get(Constants.BlockName.BeaconBasePrint));
  }

  private static boolean isInputBlacklisted(ItemStack stack) {
    var blacklist = OCSettings.get().disassemblerInputBlacklist;
    Item item = stack.getItem();
    if (item instanceof BlockItem blockItem) {
      return blacklist.contains(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString());
    } else {
      return blacklist.contains(BuiltInRegistries.ITEM.getKey(item).toString());
    }
  }
}
