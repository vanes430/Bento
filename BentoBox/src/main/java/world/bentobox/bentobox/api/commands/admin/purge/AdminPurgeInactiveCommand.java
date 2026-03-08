package world.bentobox.bentobox.api.commands.admin.purge;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandsManager;

/**
 * Command to list and purge inactive islands based on player last login time
 * Usage: - /island admin unactive [days] - List inactive islands - /island
 * admin purge-inactive <days> - Purge all inactive islands - /island admin
 * purge-player <player> - Purge specific player's island
 */
public class AdminPurgeInactiveCommand extends CompositeCommand {

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static final long ONE_DAY_MS = 24 * 60 * 60 * 1000L;

	public AdminPurgeInactiveCommand(CompositeCommand parent) {
		super(parent, "unactive", "purge-inactive", "purge-player");
	}

	@Override
	public void setup() {
		setPermission("admin.purge");
		setOnlyPlayer(false);
		setDescription("commands.admin.purge-inactive.description");
	}

	@Override
	public boolean canExecute(User user, String label, List<String> args) {
		if (args.isEmpty()) {
			user.sendMessage("commands.admin.purge-inactive.usage");
			return false;
		}
		return true;
	}

	@Override
	public boolean execute(User user, String label, List<String> args) {
		String subCommand = args.getFirst().toLowerCase();

		if (subCommand.equals("unactive") || subCommand.equals("list")) {
			// List inactive islands
			int days = args.size() > 1 ? Integer.parseInt(args.get(1)) : 7;
			listInactiveIslands(user, days);
			return true;
		} else if (subCommand.equals("purge-inactive")) {
			// Purge all inactive islands
			if (args.size() < 2) {
				user.sendMessage("commands.admin.purge-inactive.usage");
				return false;
			}
			int days = Integer.parseInt(args.get(1));
			purgeInactiveIslands(user, days);
			return true;
		} else if (subCommand.equals("purge-player")) {
			// Purge specific player's island
			if (args.size() < 2) {
				user.sendMessage("commands.admin.purge-player.usage");
				return false;
			}
			String playerName = args.get(1);
			purgePlayerIsland(user, playerName);
			return true;
		}

		user.sendMessage("commands.admin.purge-inactive.usage");
		return false;
	}

	/**
	 * List inactive islands (owner hasn't logged in for X days)
	 */
	private void listInactiveIslands(User user, int days) {
		user.sendMessage("commands.admin.purge-inactive.scanning", TextVariables.NUMBER, String.valueOf(days));

		long threshold = System.currentTimeMillis() - (days * ONE_DAY_MS);
		IslandsManager im = BentoBox.getInstance().getIslands();
		List<String> messages = new ArrayList<>();

		int count = 0;
		for (Island island : im.getIslands()) {
			UUID ownerUUID = island.getOwner();
			if (ownerUUID == null) {
				continue; // No owner
			}

			OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUUID);
			long lastLogin = owner.getLastPlayed();

			if (lastLogin < threshold) {
				count++;
				String lastLoginStr = lastLogin > 0 ? DATE_FORMAT.format(new Date(lastLogin)) : "Never";
				long daysInactive = lastLogin > 0 ? (System.currentTimeMillis() - lastLogin) / ONE_DAY_MS : -1;

				messages.add("  §e#" + count + " §f- Owner: §b" + owner.getName() + " §f| Last login: §c" + lastLoginStr
						+ " §f| Inactive: §c" + daysInactive + " days" + " §f| Island: §6"
						+ island.getCenter().getBlockX() + ", " + island.getCenter().getBlockZ());
			}
		}

		if (count == 0) {
			user.sendMessage("commands.admin.purge-inactive.no-islands", TextVariables.NUMBER, String.valueOf(days));
		} else {
			user.sendMessage("commands.admin.purge-inactive.found", TextVariables.NUMBER, String.valueOf(count));
			for (String msg : messages) {
				user.sendMessage(msg);
			}
			user.sendMessage("commands.admin.purge-inactive.list-tail");
		}
	}

	/**
	 * Purge all inactive islands
	 */
	private void purgeInactiveIslands(User user, int days) {
		user.sendMessage("commands.admin.purge-inactive.scanning", TextVariables.NUMBER, String.valueOf(days));

		long threshold = System.currentTimeMillis() - (days * ONE_DAY_MS);
		IslandsManager im = BentoBox.getInstance().getIslands();
		List<Island> toPurge = new ArrayList<>();

		for (Island island : im.getIslands()) {
			UUID ownerUUID = island.getOwner();
			if (ownerUUID == null) {
				continue; // No owner
			}

			OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUUID);
			long lastLogin = owner.getLastPlayed();

			if (lastLogin < threshold) {
				toPurge.add(island);
			}
		}

		if (toPurge.isEmpty()) {
			user.sendMessage("commands.admin.purge-inactive.no-islands", TextVariables.NUMBER, String.valueOf(days));
			return;
		}

		user.sendMessage("commands.admin.purge-inactive.purging", TextVariables.NUMBER, String.valueOf(toPurge.size()));

		int[] purged = {0};
		for (Island island : toPurge) {
			im.deleteIsland(island, true, null);
			purged[0]++;

			if (purged[0] % 10 == 0) {
				user.sendMessage("commands.admin.purge-inactive.progress", TextVariables.NUMBER,
						String.valueOf(purged[0]) + "/" + toPurge.size());
			}
		}

		user.sendMessage("commands.admin.purge-inactive.completed", TextVariables.NUMBER, String.valueOf(purged[0]));
	}

	/**
	 * Purge specific player's island
	 */
	private void purgePlayerIsland(User user, String playerName) {
		OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
		if (target == null || !target.hasPlayedBefore()) {
			user.sendMessage("commands.admin.purge-player.player-not-found", TextVariables.NAME, playerName);
			return;
		}

		UUID targetUUID = target.getUniqueId();
		IslandsManager im = BentoBox.getInstance().getIslands();
		Island island = im.getIsland(getWorld(), targetUUID);

		if (island == null) {
			user.sendMessage("commands.admin.purge-player.no-island", TextVariables.NAME, playerName);
			return;
		}

		// Show last login info
		long lastLogin = target.getLastPlayed();
		String lastLoginStr = lastLogin > 0 ? DATE_FORMAT.format(new Date(lastLogin)) : "Never";
		long daysInactive = lastLogin > 0 ? (System.currentTimeMillis() - lastLogin) / ONE_DAY_MS : -1;

		user.sendMessage("commands.admin.purge-player.confirm", TextVariables.NAME, playerName, "[last_login]",
				lastLoginStr, "[days_inactive]", String.valueOf(daysInactive));

		im.deleteIsland(island, true, null);
		user.sendMessage("commands.admin.purge-player.completed", TextVariables.NAME, playerName);
	}
}
