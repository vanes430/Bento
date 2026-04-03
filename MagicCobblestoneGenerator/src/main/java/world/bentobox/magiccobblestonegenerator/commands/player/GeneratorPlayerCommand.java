package world.bentobox.magiccobblestonegenerator.commands.player;

import java.util.List;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.magiccobblestonegenerator.StoneGeneratorAddon;
import world.bentobox.magiccobblestonegenerator.panels.player.GeneratorUserPanel;

public class GeneratorPlayerCommand extends CompositeCommand {
	public GeneratorPlayerCommand(StoneGeneratorAddon addon, CompositeCommand parent) {
		super(parent, addon.getSettings().getPlayerMainCommand());
		this.addon = addon;
	}

	@Override
	public void setup() {
		this.permission = this.getParent().getPermissionPrefix() + "generator";
		this.setDescription("stone-generator.commands.player.main.description");
		this.setOnlyPlayer(true);
	}

	@Override
	public boolean canExecute(User user, String label, List<String> args) {
		// Find the island
		Island island = this.addon.getIslands().getIsland(user.getWorld(), user);
		
		if (island == null) {
			// Search for any island the player has in worlds supported by this addon
			List<Island> islands = this.addon.getIslands().getIslands(user.getUniqueId());
			island = islands.stream()
					.filter(i -> this.addon.getAddonManager().isWorldSupported(i.getWorld()))
					.findFirst()
					.orElse(null);
		}
		
		if (island == null) {
			user.sendMessage("general.errors.no-island");
			return false;
		}
		
		// Check if user is owner or higher rank using RankManager constants
		if (island.getRank(user.getUniqueId()) < RanksManager.OWNER_RANK) {
			user.sendMessage("stone-generator.messages.owner-only");
			return false;
		}

		// Initialize island data on first access
		this.addon.getAddonManager().getIslandDataManager().initializeIsland(island);
		return true;
	}

	@Override
	public boolean execute(User user, String label, List<String> args) {
		Island island = this.addon.getIslands().getIsland(user.getWorld(), user);
		if (island == null) {
			List<Island> islands = this.addon.getIslands().getIslands(user.getUniqueId());
			island = islands.stream()
					.filter(i -> this.addon.getAddonManager().isWorldSupported(i.getWorld()))
					.findFirst()
					.orElse(null);
		}
		
		if (island != null) {
			GeneratorUserPanel.openPanel(this.addon, island.getWorld(), user);
		}
		return true;
	}

	private final StoneGeneratorAddon addon;
}
