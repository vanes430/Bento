package world.bentobox.magiccobblestonegenerator.managers;

import java.util.*;
import java.util.concurrent.*;
import org.jetbrains.annotations.*;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.magiccobblestonegenerator.StoneGeneratorAddon;
import world.bentobox.magiccobblestonegenerator.database.objects.GeneratorDataObject;
import world.bentobox.magiccobblestonegenerator.database.objects.GeneratorTierObject;

public class IslandDataManager {
	private final StoneGeneratorAddon addon;
	private final Set<org.bukkit.World> worlds = ConcurrentHashMap.newKeySet();

	public IslandDataManager(StoneGeneratorAddon addon) { this.addon = addon; }
	public void addWorld(org.bukkit.World w) { if (w != null) worlds.add(w); }
	public boolean canOperate(org.bukkit.World w) { return worlds.contains(w); }

	public @Nullable GeneratorDataObject validate(@Nullable Island island) {
		if (island == null || island.getOwner() == null) return null;
		GeneratorDataObject data = getOrCreate(island);
		data.getActiveGeneratorList().removeIf(id -> addon.getAddonManager().getGeneratorByID(id) == null || !data.getUnlockedTiers().contains(id));
		return data;
	}

	public void initializeIsland(Island island) {
		if (island == null || island.getOwner() == null) return;
		GeneratorDataObject data = getOrCreate(island);
		checkUnlock(island, data);
		checkDefaultGenerator(island, data);
		addon.getAddonManager().saveGeneratorData(data);
	}

	private void checkUnlock(Island island, GeneratorDataObject data) {
		addon.getAddonManager().getAllTiers(island.getWorld()).stream()
			.filter(g -> !g.isDefaultGenerator() && !data.getUnlockedTiers().contains(g.getUniqueId()))
			.forEach(g -> {
				data.getUnlockedTiers().add(g.getUniqueId());
				addon.getAddonManager().saveGeneratorData(data);
			});
	}

	private void checkDefaultGenerator(Island island, GeneratorDataObject data) {
		addon.getAddonManager().getAllTiers(island.getWorld()).stream()
			.filter(GeneratorTierObject::isDefaultGenerator)
			.findFirst()
			.ifPresent(defaultTier -> {
				if (!data.getUnlockedTiers().contains(defaultTier.getUniqueId())) {
					data.getUnlockedTiers().add(defaultTier.getUniqueId());
					data.getPurchasedTiers().add(defaultTier.getUniqueId());
					if (data.getActiveGeneratorList().isEmpty()) {
						data.getActiveGeneratorList().add(defaultTier.getUniqueId());
					}
					addon.getAddonManager().saveGeneratorData(data);
				}
			});
	}

	public GeneratorDataObject getOrCreate(Island island) {
		GeneratorDataObject data = addon.getAddonManager().getDataCache().get(island.getUniqueId());
		if (data != null) return data;
		
		// Check if object exists before loading to avoid warning
		if (addon.getAddonManager().getDataDb().objectExists(island.getUniqueId())) {
			data = addon.getAddonManager().getDataDb().loadObject(island.getUniqueId());
		}
		
		if (data == null) {
			data = new GeneratorDataObject();
			data.setUniqueId(island.getUniqueId());
			addon.getAddonManager().saveGeneratorData(data);
		}
		addon.getAddonManager().getDataCache().put(island.getUniqueId(), data);
		return data;
	}
}
