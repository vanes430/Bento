package world.bentobox.magiccobblestonegenerator.panels.player;

import java.util.*;
import java.util.stream.Collectors;
import org.bukkit.World;
import world.bentobox.bentobox.api.panels.Panel;
import world.bentobox.bentobox.api.panels.PanelItem;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.magiccobblestonegenerator.StoneGeneratorAddon;
import world.bentobox.magiccobblestonegenerator.database.objects.GeneratorDataObject;
import world.bentobox.magiccobblestonegenerator.database.objects.GeneratorTierObject;

public class GeneratorUserPanel extends Panel {
	protected final StoneGeneratorAddon addon;
	protected final Island island;
	protected final GeneratorDataObject data;
	protected final List<GeneratorTierObject> tiers;

	public GeneratorUserPanel(StoneGeneratorAddon addon, World world, User user) {
		this.addon = addon;
		this.island = addon.getIslands().getIsland(world, user);
		this.data = addon.getAddonManager().validateIslandData(this.island);
		this.tiers = addon.getAddonManager().getAllTiers(world).stream()
				.sorted(Comparator.comparing(GeneratorTierObject::getPriority)).limit(5).collect(Collectors.toList());

		Map<Integer, PanelItem> items = new HashMap<>();
		if (this.island != null && this.data != null) {
			PanelComponents.fillTiers(this, items, user);
			PanelComponents.fillActions(this, items, user);
		}

		String title = user.getTranslation("stone-generator.gui.titles.player-panel");
		this.makePanel(title, items, 27, user, null);
	}

	public static void openPanel(StoneGeneratorAddon addon, World world, User user) {
		if (user.isPlayer()) {
			user.getPlayer().getScheduler().run(addon.getPlugin(), task -> new GeneratorUserPanel(addon, world, user), null);
		} else {
			new GeneratorUserPanel(addon, world, user);
		}
	}
}
