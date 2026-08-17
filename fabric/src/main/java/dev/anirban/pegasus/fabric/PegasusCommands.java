/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.anirban.pegasus.common.Branding;
import dev.anirban.pegasus.common.Messages;
import dev.anirban.pegasus.common.OwnershipRecord;
import dev.anirban.pegasus.common.PegasusVariant;
import dev.anirban.pegasus.fabric.entity.PegasusEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Brigadier implementation of {@code /pegasus}, matching the Paper subcommand set.
 *
 * <p>Fabric has no permission plugin API, so operator level is the gate: level 2 for ownership
 * overrides and administrative actions, level 0 for read-only lookups.
 */
public final class PegasusCommands {
    private static final int ADMIN_LEVEL = 2;
    private static final double LOOK_DISTANCE = 12.0;

    private PegasusCommands() { }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("pegasus")
                .executes(PegasusCommands::about);

        root.then(CommandManager.literal("info").executes(PegasusCommands::info));
        root.then(CommandManager.literal("owner").executes(PegasusCommands::owner));
        root.then(CommandManager.literal("clearowner")
                .requires(source -> source.hasPermissionLevel(ADMIN_LEVEL))
                .executes(PegasusCommands::clearOwner));

        root.then(CommandManager.literal("transfer")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(PegasusCommands::transfer)));

        root.then(CommandManager.literal("giveegg")
                .requires(source -> source.hasPermissionLevel(ADMIN_LEVEL))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(context -> giveEgg(context, PegasusVariant.CLASSIC))
                        .then(CommandManager.argument("variant", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (PegasusVariant variant : PegasusVariant.values()) {
                                        builder.suggest(variant.id());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> giveEgg(context, null)))));

        root.then(CommandManager.literal("summon")
                .requires(source -> source.hasPermissionLevel(ADMIN_LEVEL))
                .executes(context -> summon(context, PegasusVariant.CLASSIC))
                .then(CommandManager.argument("variant", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (PegasusVariant variant : PegasusVariant.values()) {
                                builder.suggest(variant.id());
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> summon(context, null))));

        root.then(CommandManager.literal("reload")
                .requires(source -> source.hasPermissionLevel(ADMIN_LEVEL))
                .executes(PegasusCommands::reload));

        dispatcher.register(root);
    }

    private static int about(CommandContext<ServerCommandSource> context) {
        context.getSource().sendFeedback(() -> Text.literal(Branding.STARTUP_TITLE), false);
        return 1;
    }

    private static int info(CommandContext<ServerCommandSource> context) {
        var config = PegasusMod.config();
        context.getSource().sendFeedback(() -> Text.literal(Branding.STARTUP_TITLE), false);
        context.getSource().sendFeedback(() -> Text.literal(
                "Natural spawning: " + (config.naturalSpawning().enabled() ? "enabled" : "disabled")
                        + " | minimum Y " + config.naturalSpawning().platform().minimumY()
                        + " | platform blocks " + config.naturalSpawning().platform().minimumPlatformBlocks()), false);
        context.getSource().sendFeedback(() -> Text.literal(
                "Tame chance: " + config.taming().chancePercent() + "% | tame items: "
                        + String.join(", ", config.taming().items())), false);
        return 1;
    }

    private static int owner(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("This subcommand must be run by a player."));
            return 0;
        }
        Optional<PegasusEntity> target = lookedAtPegasus(player);
        if (target.isEmpty()) {
            context.getSource().sendError(Text.literal(
                    "Look at a Pegasus within " + (int) LOOK_DISTANCE + " blocks."));
            return 0;
        }
        Optional<OwnershipRecord> record = target.get().ownership();
        var messages = PegasusMod.config().messages();
        if (record.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal(messages.get(Messages.OWNER_NONE)), false);
            return 1;
        }
        context.getSource().sendFeedback(() -> Text.literal(messages.format(Messages.OWNER_INFO, Map.of(
                "owner", record.get().lastKnownName(),
                "uuid", record.get().ownerId().toString()))), false);
        return 1;
    }

    private static int transfer(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("This subcommand must be run by a player."));
            return 0;
        }
        Optional<PegasusEntity> target = lookedAtPegasus(actor);
        if (target.isEmpty()) {
            context.getSource().sendError(Text.literal(
                    "Look at a Pegasus within " + (int) LOOK_DISTANCE + " blocks."));
            return 0;
        }
        ServerPlayerEntity recipient;
        try {
            recipient = EntityArgumentType.getPlayer(context, "player");
        } catch (Exception exception) {
            context.getSource().sendError(Text.literal("Could not resolve that player."));
            return 0;
        }

        PegasusEntity pegasus = target.get();
        boolean admin = context.getSource().hasPermissionLevel(ADMIN_LEVEL);
        if (!pegasus.canManage(actor, admin)) {
            context.getSource().sendError(Text.literal(
                    PegasusMod.config().messages().get(Messages.NOT_OWNER)));
            return 0;
        }
        pegasus.assignOwnership(recipient.getUuid(), recipient.getGameProfile().getName());
        context.getSource().sendFeedback(() -> Text.literal(PegasusMod.config().messages()
                .format(Messages.TRANSFER_DONE, "owner", recipient.getGameProfile().getName())), true);
        return 1;
    }

    private static int clearOwner(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("This subcommand must be run by a player."));
            return 0;
        }
        Optional<PegasusEntity> target = lookedAtPegasus(actor);
        if (target.isEmpty()) {
            context.getSource().sendError(Text.literal(
                    "Look at a Pegasus within " + (int) LOOK_DISTANCE + " blocks."));
            return 0;
        }
        target.get().clearOwnership();
        context.getSource().sendFeedback(() -> Text.literal(
                PegasusMod.config().messages().get(Messages.OWNER_CLEARED)), true);
        return 1;
    }

    private static int giveEgg(CommandContext<ServerCommandSource> context, PegasusVariant fixed) {
        ServerPlayerEntity recipient;
        try {
            recipient = EntityArgumentType.getPlayer(context, "player");
        } catch (Exception exception) {
            context.getSource().sendError(Text.literal("Could not resolve that player."));
            return 0;
        }
        PegasusVariant variant = fixed;
        if (variant == null) {
            String raw = StringArgumentType.getString(context, "variant");
            variant = PegasusVariant.parse(raw).orElse(null);
            if (variant == null) {
                context.getSource().sendError(Text.literal("Unknown variant. Valid: "
                        + String.join(", ", variantIds())));
                return 0;
            }
        }
        var egg = PegasusRegistry.egg(variant);
        if (egg == null) {
            context.getSource().sendError(Text.literal("Spawn eggs are not registered."));
            return 0;
        }
        recipient.getInventory().insertStack(new ItemStack(egg));
        PegasusVariant given = variant;
        context.getSource().sendFeedback(() -> Text.literal(PegasusMod.config().messages()
                .format(Messages.EGG_GIVEN, Map.of(
                        "variant", given.displayName(),
                        "player", recipient.getGameProfile().getName()))), true);
        return 1;
    }

    private static int summon(CommandContext<ServerCommandSource> context, PegasusVariant fixed) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("This subcommand must be run by a player."));
            return 0;
        }
        PegasusVariant variant = fixed;
        if (variant == null) {
            variant = PegasusVariant.parse(StringArgumentType.getString(context, "variant")).orElse(null);
            if (variant == null) {
                context.getSource().sendError(Text.literal("Unknown variant. Valid: "
                        + String.join(", ", variantIds())));
                return 0;
            }
        }
        ServerWorld world = (ServerWorld) player.getWorld();
        PegasusEntity pegasus = PegasusRegistry.PEGASUS.create(world);
        if (pegasus == null) {
            context.getSource().sendError(Text.literal("Could not create the Pegasus entity."));
            return 0;
        }
        pegasus.setVariant(variant);
        pegasus.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0.0f);
        world.spawnEntity(pegasus);
        PegasusVariant summoned = variant;
        context.getSource().sendFeedback(() -> Text.literal("Summoned a " + summoned.displayName() + "."), true);
        return 1;
    }

    private static int reload(CommandContext<ServerCommandSource> context) {
        List<String> warnings = PegasusMod.reload();
        context.getSource().sendFeedback(() -> Text.literal(
                PegasusMod.config().messages().get(Messages.RELOADED)), true);
        warnings.forEach(warning ->
                context.getSource().sendFeedback(() -> Text.literal("Warning: " + warning), false));
        return 1;
    }

    /** Ray-traces for a Pegasus in front of the player. */
    private static Optional<PegasusEntity> lookedAtPegasus(ServerPlayerEntity player) {
        Vec3d eye = player.getCameraPosVec(1.0f);
        Vec3d look = player.getRotationVec(1.0f).multiply(LOOK_DISTANCE);
        Vec3d end = eye.add(look);
        Box searchBox = player.getBoundingBox().stretch(look).expand(1.0);

        EntityHitResult hit = net.minecraft.entity.projectile.ProjectileUtil.raycast(
                player, eye, end, searchBox,
                entity -> entity instanceof PegasusEntity && entity != player,
                LOOK_DISTANCE * LOOK_DISTANCE);

        if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
            return Optional.empty();
        }
        Entity entity = hit.getEntity();
        return entity instanceof PegasusEntity pegasus ? Optional.of(pegasus) : Optional.empty();
    }

    private static List<String> variantIds() {
        return java.util.Arrays.stream(PegasusVariant.values()).map(PegasusVariant::id).toList();
    }
}
