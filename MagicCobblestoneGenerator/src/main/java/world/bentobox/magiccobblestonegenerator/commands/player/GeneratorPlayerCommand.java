package world.bentobox.magiccobblestonegenerator.commands.player;

import java.util.List;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.magiccobblestonegenerator.StoneGeneratorAddon;
import world.bentobox.magiccobblestonegenerator.panels.player.GeneratorUserPanel;

public class GeneratorPlayerCommand extends CompositeCommand {
	public GeneratorPlayerCommand(StoneGeneratorAddon addon, CompositeCommand parent) {
		super(parent, addon.getSettings().getPlayerMainCommand());
		this.addon = addon;
	}

	@Override
	public void setup() {
		this.setPermission("[gamemode].stone-generator");
		this.setDescription("stone-generator.commands.player.main.description");
	}

	@Override
	public boolean canExecute(User user, String label, List<String> args) {
		world.bentobox.bentobox.database.objects.Island island = this.addon.getIslands().getIsland(this.getWorld(), user);
		if (island == null) {
			user.sendMessage("general.errors.no-island");
			return false;
		}
		if (island.getOwner() == null || !island.getOwner().equals(user.getUniqueId())) {
			user.sendMessage("stone-generator.messages.owner-only");
			return false;
		}
		// Initialize island data on first access
		this.addon.getAddonManager().getIslandDataManager().initializeIsland(island);
		return true;
	}

	@Override
	public boolean execute(User user, String label, List<String> args) {
		GeneratorUserPanel.openPanel(this.addon, user.getWorld(), user);
		return true;
	}

	private final StoneGeneratorAddon addon;
}
