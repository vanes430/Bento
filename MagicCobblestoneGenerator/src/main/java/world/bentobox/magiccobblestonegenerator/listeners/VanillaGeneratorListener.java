package world.bentobox.magiccobblestonegenerator.listeners;

import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.magiccobblestonegenerator.StoneGeneratorAddon;
import world.bentobox.magiccobblestonegenerator.database.objects.GeneratorTierObject;

public class VanillaGeneratorListener implements Listener {
	private final StoneGeneratorAddon addon;
	public VanillaGeneratorListener(StoneGeneratorAddon addon) {
		this.addon = addon;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockFormEvent(BlockFormEvent event) {
		Block block = event.getBlock();
		if (!block.isLiquid() || !this.addon.getAddonManager().canOperate(block.getWorld()))
			return;

		Optional<Island> island = this.addon.getIslands().getIslandAt(block.getLocation());
		if (island.isEmpty() || !island.get().isAllowed(StoneGeneratorAddon.MAGIC_COBBLESTONE_GENERATOR))
			return;

		Material type = event.getNewState().getType();
		GeneratorTierObject.GeneratorType gType = null;
		if (type == Material.COBBLESTONE)
			gType = GeneratorTierObject.GeneratorType.COBBLESTONE;
		else if (type == Material.STONE)
			gType = GeneratorTierObject.GeneratorType.STONE;
		else if (type == Material.BASALT)
			gType = GeneratorTierObject.GeneratorType.BASALT;

		if (gType != null) {
			GeneratorTierObject tier = this.addon.getAddonManager().getGeneratorTier(island.get(), block.getLocation(),
					gType);
			Material replacement = this.addon.getGenerator().processBlockReplacement(tier, block.getLocation());
			if (replacement != null && replacement.isBlock())
				event.getNewState().setType(replacement);
		}
	}
}
