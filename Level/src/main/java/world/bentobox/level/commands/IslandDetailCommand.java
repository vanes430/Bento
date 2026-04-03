package world.bentobox.level.commands;

import java.util.List;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.level.Level;
import world.bentobox.level.panels.DetailsPanel;

public class IslandDetailCommand extends CompositeCommand {

	private final Level addon;

	public IslandDetailCommand(Level addon, CompositeCommand parent) {
		super(parent, "detail");
		this.addon = addon;
	}

	@Override
	public void setup() {
		setPermission("island.detail");
		setDescription("island.detail.description");
		setOnlyPlayer(true);
	}

	@Override
	public boolean execute(User user, String label, List<String> list) {
		// Find the island globally if not in current world
		Island island = getIslands().getIsland(user.getWorld(), user);
		if (island == null) {
			List<Island> islands = getIslands().getIslands(user.getUniqueId());
			island = islands.stream()
					.filter(i -> addon.isRegisteredGameModeWorld(i.getWorld()))
					.findFirst()
					.orElse(null);
		}

		if (island == null) {
			user.sendMessage("general.errors.player-has-no-island");
			return false;
		}
		
		// If it's a self-request (not op/admin), check for ownership
		if (!user.isOp() && !user.hasPermission(this.getPermissionPrefix() + "admin.level")) {
			if (island.getRank(user.getUniqueId()) < RanksManager.OWNER_RANK) {
				user.sendMessage("stone-generator.messages.owner-only");
				return false;
			}
		}

		DetailsPanel.openPanel(this.addon, island.getWorld(), user);
		return true;
	}
}
