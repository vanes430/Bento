package world.bentobox.magiccobblestonegenerator.commands.admin;

import java.util.List;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.magiccobblestonegenerator.StoneGeneratorAddon;
import world.bentobox.magiccobblestonegenerator.utils.Utils;

public class GeneratorAdminCommand extends CompositeCommand {
	public GeneratorAdminCommand(StoneGeneratorAddon addon, CompositeCommand parent) {
		super(parent, addon.getSettings().getAdminMainCommand());
		this.addon = addon;
	}

	@Override
	public void setup() {
		this.permission = this.getParent().getPermissionPrefix() + "admin.generator";
	}

	@Override
	public boolean execute(User user, String label, List<String> args) {
		if (args.size() > 0 && args.get(0).equalsIgnoreCase("import")) {
			this.addon.getImportManager().importFile(null, user.getWorld());
			Utils.sendMessage(user, "&aGenerators imported successfully from template!");
			return true;
		}
		Utils.sendMessage(user, "&eUsage: /" + label + " generator import");
		return true;
	}

	private final StoneGeneratorAddon addon;
}
