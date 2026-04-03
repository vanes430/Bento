package world.bentobox.level.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.bentobox.util.Util;
import world.bentobox.level.Level;
import world.bentobox.level.objects.IslandLevels;
import world.bentobox.level.panels.ValuePanel;
import world.bentobox.level.util.Utils;

public class IslandValueCommand extends CompositeCommand {
	private static final String MATERIAL = "[material]";
	private final Level addon;

	public IslandValueCommand(Level addon, CompositeCommand parent) {
		super(parent, "value");
		this.addon = addon;
	}

	@Override
	public void setup() {
		this.setPermission("island.value");
		this.setParametersHelp("level.commands.value.parameters");
		this.setDescription("level.commands.value.description");
		this.setOnlyPlayer(true);
	}

	@Override
	public boolean execute(User user, String label, List<String> args) {
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

		if (args.size() > 1) {
			showHelp(this, user);
			return false;
		}

		if (args.isEmpty()) {
			ValuePanel.openPanel(addon, island.getWorld(), user);
			return true;
		}

		String arg = args.get(0);
		if ("HAND".equalsIgnoreCase(arg)) {
			executeHandCommand(user, island);
			return true;
		}

		executeMaterialCommand(user, arg, island);
		return true;
	}

	private void executeHandCommand(User user, Island island) {
		Player player = user.getPlayer();
		PlayerInventory inventory = player.getInventory();
		ItemStack mainHandItem = inventory.getItemInMainHand();

		if (mainHandItem.getType() == Material.AIR) {
			Utils.sendMessage(user, user.getTranslation("level.conversations.empty-hand"));
			return;
		}

		printValue(user, mainHandItem.getType(), island);
	}

	private void executeMaterialCommand(User user, String arg, Island island) {
		Material material = Material.matchMaterial(arg);
		if (material == null) {
			Utils.sendMessage(user, user.getTranslation(island.getWorld(), "level.conversations.unknown-item", MATERIAL, arg));
		} else {
			printValue(user, material, island);
		}
	}

	/**
	 * This method prints value of the given material in chat.
	 * 
	 * @param user
	 *            User who receives the message.
	 * @param material
	 *            Material value.
	 * @param island
	 *            The island to check values for.
	 */
	private void printValue(User user, Object material, Island island) {
		Integer value = this.addon.getBlockConfig().getValue(island.getWorld(), material);

		if (value != null) {
			Utils.sendMessage(user, user.getTranslation(island.getWorld(), "level.conversations.value", "[value]",
					String.valueOf(value), MATERIAL, Utils.prettifyObject(material, user)));

			double underWater = this.addon.getSettings().getUnderWaterMultiplier();

			if (underWater > 1.0) {
				Utils.sendMessage(user, user.getTranslation(island.getWorld(), "level.conversations.success-underwater",
						"[value]", (underWater * value) + ""), MATERIAL, Utils.prettifyObject(material, user));
			}

			// Show how many have been placed and how many are allowed
			@NonNull IslandLevels lvData = this.addon.getManager()
					.getLevelsData(island);
			int count = lvData.getMdCount().getOrDefault(material, 0) + lvData.getUwCount().getOrDefault(material, 0);
			user.sendMessage("level.conversations.you-have", TextVariables.NUMBER, String.valueOf(count));
			Integer limit = this.addon.getBlockConfig().getLimit(material);
			if (limit != null) {
				user.sendMessage("level.conversations.you-can-place", TextVariables.NUMBER, String.valueOf(limit));
			}
		} else {
			Utils.sendMessage(user, user.getTranslation(island.getWorld(), "level.conversations.no-value"));
		}
	}

	@Override
	public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
		String lastArg = !args.isEmpty() ? args.get(args.size() - 1) : "";

		if (args.isEmpty()) {
			// Don't show every player on the server. Require at least the first letter
			return Optional.empty();
		}

		List<String> options = new ArrayList<>(Arrays.stream(Material.values()).filter(Material::isBlock)
				.map(Material::name).map(String::toLowerCase).toList());

		options.add("HAND");

		return Optional.of(Util.tabLimit(options, lastArg));
	}
}