/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.paper;

import dev.anirban.pegasus.common.Branding;
import dev.anirban.pegasus.common.Messages;
import dev.anirban.pegasus.common.OwnershipRecord;
import dev.anirban.pegasus.common.PegasusVariant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;

/** Implements {@code /pegasus} with per-subcommand permissions and tab completion. */
public final class PegasusCommand implements CommandExecutor, TabCompleter {
    private static final double LOOK_DISTANCE = 12.0;
    private static final List<String> SUBCOMMANDS = List.of(
            "info", "owner", "transfer", "clearowner", "giveegg", "summon", "reload");

    private final PegasusPlugin plugin;
    private final PegasusEntities entities;
    private final PaperOwnershipStore ownership;
    private final SpawnEggService eggs;

    public PegasusCommand(PegasusPlugin plugin, PegasusEntities entities,
                          PaperOwnershipStore ownership, SpawnEggService eggs) {
        this.plugin = plugin;
        this.entities = entities;
        this.ownership = ownership;
        this.eggs = eggs;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            about(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info" -> info(sender);
            case "owner" -> owner(sender);
            case "transfer" -> transfer(sender, args);
            case "clearowner" -> clearOwner(sender);
            case "giveegg" -> giveEgg(sender, args);
            case "summon" -> summon(sender, args);
            case "reload" -> reload(sender);
            default -> {
                reply(sender, "Unknown subcommand. Use one of: " + String.join(", ", SUBCOMMANDS),
                        NamedTextColor.RED);
                yield true;
            }
        };
    }

    private void about(CommandSender sender) {
        sender.sendMessage(Component.text(Branding.STARTUP_TITLE).color(NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Subcommands: " + String.join(", ", SUBCOMMANDS))
                .color(NamedTextColor.GRAY));
    }

    private boolean info(CommandSender sender) {
        if (!require(sender, "pegasus.command.info")) {
            return true;
        }
        sender.sendMessage(Component.text(Branding.STARTUP_TITLE).color(NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Natural spawning: "
                + (plugin.config().naturalSpawning().enabled() ? "enabled" : "disabled")
                + " | minimum Y " + plugin.config().naturalSpawning().platform().minimumY()
                + " | platform blocks " + plugin.config().naturalSpawning().platform().minimumPlatformBlocks())
                .color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Tame chance: " + plugin.config().taming().chancePercent()
                + "% | tame items: " + String.join(", ", plugin.config().taming().items()))
                .color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Tracked flying Pegasus: " + plugin.flight().trackedCount())
                .color(NamedTextColor.GRAY));
        return true;
    }

    private boolean owner(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            reply(sender, "This subcommand must be run by a player looking at a Pegasus.", NamedTextColor.RED);
            return true;
        }
        if (!require(sender, "pegasus.command.owner")) {
            return true;
        }
        Optional<Horse> target = targetPegasus(player);
        if (target.isEmpty()) {
            reply(sender, "Look at a Pegasus within " + (int) LOOK_DISTANCE + " blocks.", NamedTextColor.YELLOW);
            return true;
        }
        Optional<OwnershipRecord> record = ownership.owner(target.get());
        if (record.isEmpty()) {
            reply(sender, plugin.config().messages().get(Messages.OWNER_NONE), NamedTextColor.GRAY);
            return true;
        }
        reply(sender, plugin.config().messages().format(Messages.OWNER_INFO, Map.of(
                "owner", record.get().lastKnownName(),
                "uuid", record.get().ownerId().toString())), NamedTextColor.GRAY);
        return true;
    }

    private boolean transfer(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            reply(sender, "This subcommand must be run by a player looking at a Pegasus.", NamedTextColor.RED);
            return true;
        }
        if (!require(sender, "pegasus.command.transfer")) {
            return true;
        }
        if (args.length < 2) {
            reply(sender, "Usage: /pegasus transfer <player>", NamedTextColor.YELLOW);
            return true;
        }
        Optional<Horse> target = targetPegasus(player);
        if (target.isEmpty()) {
            reply(sender, "Look at a Pegasus within " + (int) LOOK_DISTANCE + " blocks.", NamedTextColor.YELLOW);
            return true;
        }
        // Accept offline players so ownership can be handed to someone who is not connected.
        OfflinePlayer recipient = resolvePlayer(args[1]);
        if (recipient == null || recipient.getName() == null) {
            reply(sender, "Unknown player: " + args[1], NamedTextColor.RED);
            return true;
        }
        boolean admin = player.hasPermission(PegasusListener.PERMISSION_ADMIN);
        boolean changed = ownership.transfer(target.get(), player.getUniqueId(), admin,
                recipient.getUniqueId(), recipient.getName());
        if (!changed) {
            reply(sender, plugin.config().messages().get(Messages.NOT_OWNER), NamedTextColor.RED);
            return true;
        }
        target.get().setOwner(recipient);
        reply(sender, plugin.config().messages().format(Messages.TRANSFER_DONE,
                "owner", recipient.getName()), NamedTextColor.GREEN);
        return true;
    }

    private boolean clearOwner(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            reply(sender, "This subcommand must be run by a player looking at a Pegasus.", NamedTextColor.RED);
            return true;
        }
        if (!require(sender, "pegasus.command.clearowner")) {
            return true;
        }
        Optional<Horse> target = targetPegasus(player);
        if (target.isEmpty()) {
            reply(sender, "Look at a Pegasus within " + (int) LOOK_DISTANCE + " blocks.", NamedTextColor.YELLOW);
            return true;
        }
        boolean admin = player.hasPermission(PegasusListener.PERMISSION_ADMIN);
        if (!ownership.clear(target.get(), player.getUniqueId(), admin)) {
            reply(sender, plugin.config().messages().get(Messages.NOT_OWNER), NamedTextColor.RED);
            return true;
        }
        Horse pegasus = target.get();
        pegasus.setTamed(false);
        pegasus.setOwner(null);
        reply(sender, plugin.config().messages().get(Messages.OWNER_CLEARED), NamedTextColor.GREEN);
        return true;
    }

    private boolean giveEgg(CommandSender sender, String[] args) {
        if (!require(sender, "pegasus.command.giveegg")) {
            return true;
        }
        if (args.length < 2) {
            reply(sender, "Usage: /pegasus giveegg <player> [variant]", NamedTextColor.YELLOW);
            return true;
        }
        Player recipient = plugin.getServer().getPlayerExact(args[1]);
        if (recipient == null) {
            reply(sender, "Player must be online: " + args[1], NamedTextColor.RED);
            return true;
        }
        PegasusVariant variant = args.length >= 3
                ? PegasusVariant.parse(args[2]).orElse(null)
                : PegasusVariant.CLASSIC;
        if (variant == null) {
            reply(sender, "Unknown variant. Valid: classic, blue_eye", NamedTextColor.RED);
            return true;
        }
        recipient.getInventory().addItem(eggs.create(variant, 1));
        reply(sender, plugin.config().messages().format(Messages.EGG_GIVEN, Map.of(
                "variant", variant.displayName(), "player", recipient.getName())), NamedTextColor.GREEN);
        return true;
    }

    private boolean summon(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            reply(sender, "This subcommand must be run by a player.", NamedTextColor.RED);
            return true;
        }
        if (!require(sender, "pegasus.command.summon")) {
            return true;
        }
        PegasusVariant variant = args.length >= 2 ? PegasusVariant.parse(args[1]).orElse(null) : PegasusVariant.CLASSIC;
        if (variant == null) {
            reply(sender, "Unknown variant. Valid: classic, blue_eye", NamedTextColor.RED);
            return true;
        }
        Location location = player.getLocation();
        PegasusVariant chosen = variant;
        location.getWorld().spawn(location, Horse.class, horse ->
                entities.initialisePegasus(horse, chosen, plugin.config(), false));
        reply(sender, "Summoned a " + variant.displayName() + ".", NamedTextColor.GREEN);
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!require(sender, "pegasus.command.reload")) {
            return true;
        }
        List<String> warnings = plugin.reloadPegasusConfig();
        reply(sender, plugin.config().messages().get(Messages.RELOADED), NamedTextColor.GREEN);
        for (String warning : warnings) {
            reply(sender, "Warning: " + warning, NamedTextColor.YELLOW);
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    /** Ray-traces for a Pegasus the player is looking at, ignoring blocks that are not in the way. */
    private Optional<Horse> targetPegasus(Player player) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), LOOK_DISTANCE,
                entity -> entities.isPegasus(entity) && !entity.equals(player));
        if (result == null) {
            return Optional.empty();
        }
        Entity hit = result.getHitEntity();
        return hit instanceof Horse horse ? Optional.of(horse) : Optional.empty();
    }

    @SuppressWarnings("deprecation") // Name lookup for offline players has no non-deprecated equivalent.
    private OfflinePlayer resolvePlayer(String name) {
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = plugin.getServer().getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    private boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission(PegasusListener.PERMISSION_ADMIN)) {
            return true;
        }
        reply(sender, plugin.config().messages().get(Messages.NO_PERMISSION), NamedTextColor.RED);
        return false;
    }

    private static void reply(CommandSender sender, String message, NamedTextColor colour) {
        sender.sendMessage(Component.text(message).color(colour));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("giveegg") || sub.equals("transfer"))) {
            List<String> names = new ArrayList<>();
            plugin.getServer().getOnlinePlayers().forEach(player -> names.add(player.getName()));
            return filter(names, args[1]);
        }
        if ((args.length == 3 && sub.equals("giveegg")) || (args.length == 2 && sub.equals("summon"))) {
            return filter(Arrays.stream(PegasusVariant.values()).map(PegasusVariant::id).toList(),
                    args[args.length - 1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    /** Exposed for the Fabric module's parity checks and for tests. */
    public static List<String> subcommands() {
        return SUBCOMMANDS;
    }

    static UUID unusedPlaceholder() {
        return UUID.randomUUID();
    }
}
