package world.bentobox.bentobox.api.commands.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.commands.island.IslandGoCommand;
import world.bentobox.bentobox.api.commands.island.IslandGoCommand.IslandInfo;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;

/**
 * Enables admins to teleport to a player's island, nether or end islands.
 * <p>
 * Usage: - /island tp &lt;player&gt; [island name] - teleport to player's
 * overworld island - /island tp &lt;player&gt; overworld - teleport to player's
 * overworld island - /island tp &lt;player&gt; nether - teleport to player's
 * nether island - /island tp &lt;player&gt; end - teleport to player's end
 * island - /island tp &lt;player&gt; [island name] end - teleport to named
 * island in end dimension
 *
 * For example /acid tp tastybento [island name] would teleport to tastybento's
 * [named] island
 *
 */
public class AdminTeleportCommand extends CompositeCommand {

	private static final String NOT_SAFE = "general.errors.no-safe-location-found";
	private @Nullable UUID targetUUID;
	private Location warpSpot;
	private World targetWorld;

	/**
	 * @param parent
	 *            - parent command
	 * @param tpCommand
	 *            - should be "tp"
	 */
	public AdminTeleportCommand(CompositeCommand parent, String tpCommand) {
		super(parent, tpCommand);
	}

	@Override
	public void setup() {
		// Permission
		setPermission("admin.tp");
		setParametersHelp("commands.admin.tp.parameters");
		setDescription("commands.admin.tp.description");
		this.setOnlyPlayer(true);
	}

	@Override
	public boolean canExecute(User user, String label, List<String> args) {
		if (args.isEmpty()) {
			this.showHelp(this, user);
			return false;
		}
		// Check for console or not
		if (!user.isPlayer()) {
			user.sendMessage("general.errors.use-in-game");
			return false;
		}
		// Convert name to a UUID
		targetUUID = Util.getUUID(args.getFirst());
		if (targetUUID == null) {
			user.sendMessage("general.errors.unknown-player", TextVariables.NAME, args.getFirst());
			return false;
		}
		// Check island exists in overworld
		if (!getIslands().hasIsland(getWorld(), targetUUID) && !getIslands().inTeam(getWorld(), targetUUID)) {
			user.sendMessage("general.errors.player-has-no-island");
			return false;
		}

		// Default to overworld
		targetWorld = getWorld();
		String dimensionArg = null;
		int nameEndIndex = args.size();

		// Check if last arg is a dimension
		if (args.size() >= 2) {
			String lastArg = args.getLast().toLowerCase();
			if (lastArg.equals("overworld") || lastArg.equals("nether") || lastArg.equals("end")
					|| lastArg.equals("the_end")) {
				dimensionArg = lastArg;
				nameEndIndex = args.size() - 1;
			}
		}

		// Determine target world based on dimension argument
		if (dimensionArg != null) {
			if (dimensionArg.equals("nether")) {
				targetWorld = getPlugin().getIWM().getNetherWorld(getWorld());
			} else if (dimensionArg.equals("end") || dimensionArg.equals("the_end")) {
				targetWorld = getPlugin().getIWM().getEndWorld(getWorld());
			} else {
				targetWorld = getWorld();
			}
		}

		if (targetWorld == null) {
			user.sendMessage("commands.admin.tp.world-not-available", "[dimension]",
					dimensionArg != null ? dimensionArg : "unknown");
			return false;
		}

		// Get default location if there are no more arguments
		warpSpot = getSpot(targetWorld);
		if (warpSpot == null) {
			user.sendMessage(NOT_SAFE);
			return false;
		}

		// If only dimension specified or no extra args, we're done
		if (args.size() == 1 || (args.size() == 2 && dimensionArg != null)) {
			return true;
		}

		// They named the island to go to
		Map<String, IslandInfo> names = IslandGoCommand.getNameIslandMap(User.getInstance(targetUUID), getWorld());
		final String name = String.join(" ", args.subList(1, nameEndIndex));
		if (!names.containsKey(name)) {
			// Failed home name check
			user.sendMessage("commands.island.go.unknown-home");
			user.sendMessage("commands.island.sethome.homes-are");
			names.keySet()
					.forEach(n -> user.sendMessage("commands.island.sethome.home-list-syntax", TextVariables.NAME, n));
			return false;
		} else if (names.size() > 1) {
			IslandInfo info = names.get(name);
			Island island = info.island();
			warpSpot = island.getSpawnPoint(targetWorld.getEnvironment()) != null
					? island.getSpawnPoint(targetWorld.getEnvironment())
					: island.getProtectionCenter().toVector().toLocation(targetWorld);
		}
		return true;
	}

	@Override
	public boolean execute(User user, String label, List<String> args) {
		Objects.requireNonNull(warpSpot);
		Objects.requireNonNull(targetWorld);

		// Teleport using teleportAsync (Folia-compatible)
		Player player = user.getPlayer();
		player.teleportAsync(warpSpot).thenRun(() -> {
			if (user.isPlayer()) {
				user.getPlayer().getScheduler().run(getPlugin(),
						t -> user.sendMessage("commands.admin.tp.success", "[location]",
								warpSpot.getBlockX() + " " + warpSpot.getBlockY() + " " + warpSpot.getBlockZ(),
								"[dimension]", targetWorld.getEnvironment().name()),
						null);
			} else {
				Bukkit.getGlobalRegionScheduler().run(getPlugin(),
						t -> user.sendMessage("commands.admin.tp.success", "[location]",
								warpSpot.getBlockX() + " " + warpSpot.getBlockY() + " " + warpSpot.getBlockZ(),
								"[dimension]", targetWorld.getEnvironment().name()));
			}
		});
		return true;
	}

	private Location getSpot(World world) {
		assert targetUUID != null;
		Island island = getIslands().getIsland(world, targetUUID);
		if (island == null) {
			return null;
		}
		return island.getSpawnPoint(world.getEnvironment()) != null
				? island.getSpawnPoint(world.getEnvironment())
				: island.getProtectionCenter().toVector().toLocation(world);
	}

	@Override
	public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
		String lastArg = !args.isEmpty() ? args.getLast() : "";
		if (args.isEmpty()) {
			// Don't show every player on the server. Require at least the first letter
			return Optional.empty();
		}
		if (args.size() == 1) {
			return Optional.of(Util.tabLimit(new ArrayList<>(Util.getOnlinePlayerList(user)), lastArg));
		}

		// Suggest dimensions for second arg
		if (args.size() == 2) {
			// Check if first arg is a valid player
			UUID target = Util.getUUID(args.get(0));
			if (target != null) {
				// Suggest island names and dimensions
				List<String> suggestions = new ArrayList<>();
				suggestions.add("overworld");
				suggestions.add("nether");
				suggestions.add("end");
				suggestions.addAll(IslandGoCommand.getNameIslandMap(User.getInstance(target), getWorld()).keySet());
				return Optional.of(Util.tabLimit(suggestions, lastArg));
			}
			return Optional.of(Util.tabLimit(new ArrayList<>(Util.getOnlinePlayerList(user)), lastArg));
		}

		if (args.size() == 3) {
			// Third arg could be dimension if second was island name, or island name if
			// second was dimension
			UUID target = Util.getUUID(args.get(0));
			if (target == null) {
				return Optional.empty();
			}

			// Check if second arg is a dimension
			String secondArg = args.get(1).toLowerCase();
			if (secondArg.equals("overworld") || secondArg.equals("nether") || secondArg.equals("end")
					|| secondArg.equals("the_end")) {
				// Suggest island names
				return Optional.of(Util.tabLimit(
						new ArrayList<>(
								IslandGoCommand.getNameIslandMap(User.getInstance(target), getWorld()).keySet()),
						lastArg));
			} else {
				// Suggest dimensions
				List<String> suggestions = List.of("overworld", "nether", "end");
				return Optional.of(Util.tabLimit(suggestions, lastArg));
			}
		}
		return Optional.empty();
	}

}
