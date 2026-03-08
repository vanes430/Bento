package world.bentobox.magiccobblestonegenerator.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;

/**
 * Super Lite Utils for MagicCobblestone.
 */
public class Utils {
	public static void sendMessage(User user, String message) {
		if (message != null && !message.isEmpty()) {
			user.sendMessage(Util.translateColorCodes(message));
		}
	}

	public static String prettifyObject(Object object) {
		if (object == null)
			return "None";
		String name = object.toString().toLowerCase().replace("_", " ");
		return name.substring(0, 1).toUpperCase() + name.substring(1);
	}

	public static Material getGeneratorTypeMaterial(Material type) {
		return type != null ? type : Material.COBBLESTONE;
	}
}
