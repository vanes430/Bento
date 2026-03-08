package world.bentobox.magiccobblestonegenerator.tasks;

import java.util.Random;
import java.util.TreeMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import world.bentobox.magiccobblestonegenerator.StoneGeneratorAddon;
import world.bentobox.magiccobblestonegenerator.database.objects.GeneratorTierObject;

/**
 * Super Lite MagicGenerator.
 */
public class MagicGenerator {
	public MagicGenerator(StoneGeneratorAddon addon) {
		this.addon = addon;
	}

	public @Nullable Material processBlockReplacement(@Nullable GeneratorTierObject tier, Location loc) {
		if (tier == null)
			return null;
		TreeMap<Double, Material> chanceMap = tier.getBlockChanceMap();
		if (chanceMap.isEmpty())
			return null;

		double rand = this.random.nextDouble() * chanceMap.lastKey();
		return chanceMap.ceilingEntry(rand).getValue();
	}

	private final StoneGeneratorAddon addon;
	private final Random random = new Random(System.currentTimeMillis());
}
