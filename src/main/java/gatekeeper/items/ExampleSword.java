package gatekeeper.items;

import necesse.inventory.item.Item;
import necesse.inventory.item.toolItem.swordToolItem.SwordToolItem;

public class ExampleSword extends SwordToolItem {
    
    public ExampleSword() {
        // Constructor: (enchantCost, lootTableCategory)
        super(500, null);
        
        // Set item rarity
        this.rarity = Item.Rarity.COMMON;
        
        // Attack speed in milliseconds (300 is standard for swords)
        this.attackAnimTime.setBaseValue(300);
        
        // Attack damage (base and upgraded values)
        this.attackDamage.setBaseValue(25.0F).setUpgradedValue(1.0F, 80.0F);
        
        // Attack range in pixels
        this.attackRange.setBaseValue(60);
        
        // Knockback strength
        this.knockback.setBaseValue(100);
        
        // Enable for raid events
        this.canBeUsedForRaids = true;
    }
}
