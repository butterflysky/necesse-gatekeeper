package gatekeeper;

import necesse.engine.modLoader.annotations.ModEntry;
import necesse.engine.registries.ItemRegistry;
import necesse.inventory.recipe.Ingredient;
import necesse.inventory.recipe.Recipe;
import necesse.inventory.recipe.Recipes;
import necesse.engine.registries.RecipeTechRegistry;
import gatekeeper.items.ExampleSword;

@ModEntry
public class GatekeeperMod {
    
    // Called first - register content (items, mobs, tiles, etc.)
    public void init() {
        System.out.println("GateKeeper is loading...");
        
  // Register items (modern API)
  ItemRegistry.registerItem("examplesword", new ExampleSword());
        
        System.out.println("GateKeeper loaded successfully!");
    }
    
    // Called second - load resources (images, sounds, etc...)
    public void initResources() {

    }
    
    // Called last - everything is loaded, safe to reference any content
    public void postInit() {

        // Add crafting recipe
        Recipes.registerModRecipe(new Recipe(
            "examplesword",                      
            1,                                
            RecipeTechRegistry.IRON_ANVIL,   
            new Ingredient[]{                 
                new Ingredient("ironbar", 5),    
                new Ingredient("anystone", 10)    
            }
        ));
        System.out.println("GateKeeper post-initialization complete!");
    }
}
