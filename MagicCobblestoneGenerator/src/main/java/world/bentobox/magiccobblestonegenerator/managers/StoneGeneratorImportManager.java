package world.bentobox.magiccobblestonegenerator.managers;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import world.bentobox.magiccobblestonegenerator.StoneGeneratorAddon;
import world.bentobox.magiccobblestonegenerator.database.objects.GeneratorTierObject;

/**
 * Lite Import Manager.
 */
public class StoneGeneratorImportManager {
	private final StoneGeneratorAddon addon;

	public StoneGeneratorImportManager(StoneGeneratorAddon addon) {
		this.addon = addon;
	}

	public void importFile(File file, World world) {
		String gm = this.addon.getPlugin().getIWM().getAddon(world).map(a -> a.getDescription().getName().toLowerCase()).orElse("unknown");
		File template = file != null ? file : new File(this.addon.getDataFolder(), "generator.yml");
		if (!template.exists()) return;

		YamlConfiguration config = YamlConfiguration.loadConfiguration(template);
		ConfigurationSection tiers = config.getConfigurationSection("tiers");
		if (tiers == null) return;

		for (String key : tiers.getKeys(false)) {
			ConfigurationSection s = tiers.getConfigurationSection(key);
			GeneratorTierObject tier = new GeneratorTierObject();
			tier.setUniqueId(gm + "_" + key);
			tier.setFriendlyName(s.getString("name", key));
			tier.setDescription(s.getStringList("description"));
			tier.setPriority(s.getInt("priority", 0));
			tier.setDefaultGenerator(s.getBoolean("default", false));
			tier.setGeneratorTierCost(s.getDouble("requirements.purchase-cost", 0.0));

			String iconStr = s.getString("icon", "COBBLESTONE:1");
			String[] parts = iconStr.split(":");
			tier.setGeneratorIcon(new ItemStack(Material.valueOf(parts[0])));

			String typeStr = s.getString("type", "COBBLESTONE");
			try {
				tier.setGeneratorType(GeneratorTierObject.GeneratorType.valueOf(typeStr));
			} catch (IllegalArgumentException e) {
				tier.setGeneratorType(GeneratorTierObject.GeneratorType.COBBLESTONE);
			}

			TreeMap<Double, Material> blockMap = new TreeMap<>();
			double current = 0;
			ConfigurationSection blocks = s.getConfigurationSection("blocks");
			if (blocks != null) {
				for (String b : blocks.getKeys(false)) {
					double chance = blocks.getDouble(b);
					current += chance;
					blockMap.put(current, Material.valueOf(b));
				}
			}
			tier.setBlockChanceMap(blockMap);
			this.addon.getAddonManager().saveGeneratorTier(tier);
			this.addon.getAddonManager().getTierCache().put(tier.getUniqueId(), tier);
		}
	}
}
