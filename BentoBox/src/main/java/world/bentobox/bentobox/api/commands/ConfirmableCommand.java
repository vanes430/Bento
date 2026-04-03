package world.bentobox.bentobox.api.commands;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.ExpiringMap;
import world.bentobox.bentobox.util.Pair;

/**
 * An extension of {@link CompositeCommand} that adds a confirmation step for
 * potentially destructive or significant actions using the --force flag.
 * <p>
 * When a command needs confirmation, it calls
 * {@link #checkForceFlag(User, List)}. If the flag is not provided, the command
 * will be cancelled and a message will be shown to the user.
 * <p>
 * Alternatively, it can call {@link #checkConfirmation(User)} to require the
 * user to enter the command again within a certain time frame.
 * <p>
 * Example usage:
 * 
 * <pre>
 * {@code
 * if (!checkForceFlag(user, args)) {
 * 	return false;
 * }
 * // Code to run after confirmation
 * island.delete();
 * user.sendMessage("island.deleted");
 * }
 * </pre>
 *
 * @author tastybento
 * @author Poslovitch
 * @since 1.0
 */
public abstract class ConfirmableCommand extends CompositeCommand {

	/**
	 * Confirmation cache for users who have run a command once and need to run it
	 * again to confirm.
	 */
	private static final ExpiringMap<Pair<UUID, String>, Boolean> confirmationCache = new ExpiringMap<>(10,
			TimeUnit.SECONDS);

	/**
	 * Creates a top-level confirmable command registered by an addon.
	 *
	 * @param addon
	 *            The addon registering the command.
	 * @param label
	 *            The primary label for the command.
	 * @param aliases
	 *            Optional aliases for the command.
	 */
	protected ConfirmableCommand(Addon addon, String label, String... aliases) {
		super(addon, label, aliases);
	}

	/**
	 * Creates a confirmable sub-command registered by an addon, attached to a
	 * parent command.
	 *
	 * @param addon
	 *            The addon registering this sub-command.
	 * @param parent
	 *            The parent command.
	 * @param label
	 *            The label for this sub-command.
	 * @param aliases
	 *            Optional aliases for this sub-command.
	 */
	protected ConfirmableCommand(Addon addon, CompositeCommand parent, String label, String... aliases) {
		super(addon, parent, label, aliases);
	}

	/**
	 * Creates a confirmable sub-command that belongs to a parent command.
	 *
	 * @param parent
	 *            The parent command.
	 * @param label
	 *            The label for this sub-command.
	 * @param aliases
	 *            Optional aliases for this sub-command.
	 */
	protected ConfirmableCommand(CompositeCommand parent, String label, String... aliases) {
		super(parent, label, aliases);
	}

	/**
	 * Checks if the user has provided the --force flag to confirm a destructive
	 * action.
	 * <p>
	 * If the flag is not provided, a warning message is shown to the user
	 * instructing them to use the --force flag.
	 * <p>
	 * Example usage:
	 * 
	 * <pre>
	 * {@code
	 * @Override
	 * public boolean execute(User user, String label, List<String> args) {
	 * 	if (!checkForceFlag(user, args)) {
	 * 		return false;
	 * 	}
	 * 	// Destructive action here
	 * 	return true;
	 * }
	 * }
	 * </pre>
	 *
	 * @param user
	 *            The user to check for the --force flag.
	 * @param args
	 *            The command arguments.
	 * @return true if the --force flag was provided, false otherwise.
	 */
	protected boolean checkForceFlag(User user, List<String> args) {
		// Check if user provided --force flag
		if (args.contains("--force")) {
			return true;
		}

		// Show warning message
		user.sendMessage("commands.confirmation.force-flag-required", "[command]", getUsage());
		return false;
	}

	/**
	 * Checks if the user has provided the --force flag to confirm a destructive
	 * action. Same as {@link #checkForceFlag(User, List)} but with a custom
	 * message.
	 *
	 * @param user
	 *            The user to check for the --force flag.
	 * @param args
	 *            The command arguments.
	 * @param message
	 *            Custom message to show if flag is not provided.
	 * @return true if the --force flag was provided, false otherwise.
	 */
	protected boolean checkForceFlag(User user, List<String> args, String message) {
		if (args.contains("--force")) {
			return true;
		}

		user.sendRawMessage(message);
		return false;
	}

	/**
	 * Checks if the user has run the same command once and is now running it again
	 * to confirm.
	 * 
	 * @param user
	 *            The user to check for confirmation.
	 * @return true if the user has confirmed, false otherwise.
	 */
	protected boolean checkConfirmation(User user) {
		Pair<UUID, String> key = new Pair<>(user.getUniqueId(), getUsage());
		if (confirmationCache.containsKey(key)) {
			confirmationCache.remove(key);
			return true;
		}

		// User has not run the command once. Add them to the cache.
		confirmationCache.put(key, true);
		user.sendMessage("commands.confirmation.confirm", "[seconds]",
				String.valueOf(getSettings().getConfirmationTime()));
		return false;
	}
}
