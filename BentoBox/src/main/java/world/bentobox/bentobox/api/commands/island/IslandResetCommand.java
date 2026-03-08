package world.bentobox.bentobox.api.commands.island;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.commands.ConfirmableCommand;
import world.bentobox.bentobox.api.events.island.IslandEvent;
import world.bentobox.bentobox.api.events.island.IslandEvent.Reason;
import world.bentobox.bentobox.api.events.team.TeamEvent;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.bentobox.util.Util;

/**
 * Handles the island reset command (/island reset).
 * <p>
 * This command DELETES the player's island completely. NO new island is created
 * - user must create one manually.
 * <p>
 * Process:
 * <ul>
 * <li>Kick all team members</li>
 * <li>Delete old island blocks</li>
 * <li>Remove player data from database (like new player)</li>
 * <li>Reset all player data (resets, etc.)</li>
 * </ul>
 * <p>
 * Permission: {@code island.reset} Aliases: reset, restart
 *
 * @author tastybento
 * @since 1.0
 */
public class IslandResetCommand extends ConfirmableCommand {

	public IslandResetCommand(CompositeCommand islandCommand) {
		super(islandCommand, "reset", "restart");
	}

	@Override
	public void setup() {
		setPermission("island.reset");
		setOnlyPlayer(true);
		setParametersHelp("commands.island.reset.parameters");
		setDescription("commands.island.reset.description");
	}

	@Override
	public boolean canExecute(User user, String label, List<String> args) {
		// Check cooldown
		if (getSettings().getResetCooldown() > 0 && checkCooldown(user)) {
			return false;
		}
		if (!getIslands().hasIsland(getWorld(), user.getUniqueId())) {
			user.sendMessage("general.errors.not-owner");
			return false;
		}
		int resetsLeft = getPlayers().getResetsLeft(getWorld(), user.getUniqueId());
		if (resetsLeft != -1) {
			if (resetsLeft == 0) {
				user.sendMessage("commands.island.reset.none-left");
				return false;
			} else {
				user.sendMessage("commands.island.reset.resets-left", TextVariables.NUMBER, String.valueOf(resetsLeft));
			}
		}
		return true;
	}

	@Override
	public boolean execute(User user, String label, List<String> args) {
		if (!checkForceFlag(user, args)) {
			return false;
		}
		return deleteIsland(user);
	}

	/**
	 * Deletes the player's island completely and resets their data like a new
	 * player. NO new island is created - user must create one manually.
	 *
	 * @param user
	 *            The user deleting their island
	 * @return true if deletion was successful
	 */
	private boolean deleteIsland(User user) {
		Island oldIsland = getIslands().getIsland(getWorld(), user);

		if (oldIsland == null) {
			user.sendMessage("general.errors.player-has-no-island");
			return false;
		}

		// Fire preclear event
		IslandEvent.builder().involvedPlayer(user.getUniqueId()).reason(Reason.PRECLEAR).island(oldIsland)
				.oldIsland(oldIsland).location(oldIsland.getCenter()).build();

		// Get spawn location for teleport
		Location spawn = getSpawnLocation();

		// Teleport all members to spawn before deletion
		teleportMembersToSpawn(oldIsland, spawn);

		// Kick all members and clean up
		kickAllMembers(oldIsland);

		// Delete the old island (blocks + data)
		getIslands().deleteIsland(oldIsland, true, user.getUniqueId());

		// Reset player data to be like a new player
		// This removes their data from database so they'll be treated as new on next
		// login
		resetPlayerData(user);

		// Set cooldown
		setCooldown(user.getUniqueId(), getSettings().getResetCooldown());

		user.sendMessage("commands.island.reset.success");
		return true;
	}

	/**
	 * Resets player data to be like a new player. This mimics what happens when a
	 * player first joins the server.
	 */
	private void resetPlayerData(User user) {
		// Delete player from database completely
		// When they login again, they'll be treated as a new player
		getPlayers().removePlayer(user.getUniqueId());
	}

	/**
	 * Gets the spawn location for teleporting players after reset.
	 */
	private Location getSpawnLocation() {
		if (!Bukkit.getWorlds().isEmpty()) {
			return Bukkit.getWorlds().get(0).getSpawnLocation();
		}
		return null;
	}

	/**
	 * Teleports all island members to spawn.
	 */
	private void teleportMembersToSpawn(Island island, Location spawn) {
		if (spawn == null) {
			return;
		}
		island.getMemberSet().forEach(uuid -> {
			User member = User.getInstance(uuid);
			if (member.isOnline()) {
				member.getPlayer().teleportAsync(spawn);
				if (!uuid.equals(island.getOwner())) {
					member.sendMessage("commands.island.reset.kicked-from-island", TextVariables.GAMEMODE,
							getAddon().getDescription().getName());
				}
			}
		});
	}

	/**
	 * Kicks all members and cleans up their data.
	 */
	private void kickAllMembers(Island island) {
		island.getMemberSet().forEach(memberUUID -> {
			User member = User.getInstance(memberUUID);

			// Remove from island
			getIslands().removePlayer(island, memberUUID);

			// Clean player data (inventory, ender chest, etc.)
			getPlayers().cleanLeavingPlayer(getWorld(), member, true, island);

			// Fire team delete event
			TeamEvent.builder().island(island).reason(TeamEvent.Reason.DELETE).involvedPlayer(memberUUID).build();

			// Reset rank to visitor
			IslandEvent.builder().island(island).involvedPlayer(memberUUID).admin(false)
					.reason(IslandEvent.Reason.RANK_CHANGE)
					.rankChange(island.getRank(member), RanksManager.VISITOR_RANK).build();
		});
	}
}
