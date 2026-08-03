package twilightforest.item;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;

/**
 * Test-classpath representative of the concrete class name verified from the checksum-pinned
 * Twilight Forest JAR. Production code never links against this fixture; it pins the runtime class
 * name and verifies the real item/block bijection inside the initialized client.
 */
public final class ItemBlockTFMeta extends ItemBlock {
    public ItemBlockTFMeta(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }
}
