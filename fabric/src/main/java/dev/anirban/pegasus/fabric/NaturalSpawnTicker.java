/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.fabric;

import dev.anirban.pegasus.common.PegasusConfig;
import dev.anirban.pegasus.common.PegasusVariant;
import dev.anirban.pegasus.common.SpawnPlatformRules;
import dev.anirban.pegasus.fabric.entity.PegasusEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * Scans for Hay Bale platforms high in the sky and occasionally spawns a Pegasus.
 *
 * <p>Runs on the server tick thread. Cost is bounded: it only acts every configured interval, only
 * inspects already-loaded chunks, samples a fixed area, and stops at the first valid platform per
 * player. It never forces chunk loading or generation.
 */
public final class NaturalSpawnTicker {
    private static final int SEARCH_RADIUS = 24;
    private static final int SEARCH_STEP = 4;
    private static final int VERTICAL_RADIUS = 12;

    private final Random random = new Random();
    private int tickCounter;

    public void onEndTick(MinecraftServer server) {
        PegasusConfig config = PegasusMod.config();
        if (!config.naturalSpawning().enabled()) {
            return;
        }
        int interval = (int) Math.max(1, config.naturalSpawning().checkInterval().toSeconds() * 20L);
        if (++tickCounter < interval) {
            return;
        }
        tickCounter = 0;

        Optional<Block> required = resolveBlock(config.naturalSpawning().requiredBlock());
        if (required.isEmpty()) {
            PegasusMod.debug(() -> "natural-spawning.required-block '"
                    + config.naturalSpawning().requiredBlock() + "' is not a valid block");
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            attemptForPlayer(player, required.get(), config);
        }
    }

    private void attemptForPlayer(ServerPlayerEntity player, Block required, PegasusConfig config) {
        ServerWorld world = (ServerWorld) player.getWorld();
        String worldId = world.getRegistryKey().getValue().toString();
        if (!config.naturalSpawning().allowsWorld(worldId)
                && !config.naturalSpawning().allowsWorld(world.getRegistryKey().getValue().getPath())) {
            return;
        }

        Optional<BlockPos> surface = findPlatformSurface(world, player.getBlockPos(), required, config);
        if (surface.isEmpty()) {
            return;
        }
        BlockPos platform = surface.get();

        List<Boolean> scan = scanPlatform(world, platform, required, config.naturalSpawning().platform());
        SpawnPlatformRules.Result validation = SpawnPlatformRules.validate(
                platform.getY(), scan, config.naturalSpawning().platform());

        if (!validation.valid()) {
            PegasusMod.debug(() -> "platform rejected at " + platform.toShortString() + ": "
                    + validation.reason() + " (" + validation.matchingBlocks() + "/"
                    + validation.scannedBlocks() + " blocks)");
            return;
        }
        PegasusMod.debug(() -> "valid platform at " + platform.toShortString() + " ("
                + validation.matchingBlocks() + "/" + validation.scannedBlocks() + " blocks)");

        BlockPos spawnPos = platform.up();
        if (!world.getBlockState(spawnPos).isAir() || !world.getBlockState(spawnPos.up()).isAir()) {
            return;
        }
        int radius = config.naturalSpawning().nearbyRadius();
        long nearby = world.getEntitiesByClass(PegasusEntity.class,
                new Box(spawnPos).expand(radius), entity -> true).size();
        if (nearby > config.naturalSpawning().maxNearbyPegasus()) {
            return;
        }
        if (random.nextInt(100) >= config.naturalSpawning().spawnChancePercent()) {
            return;
        }

        PegasusEntity pegasus = PegasusRegistry.PEGASUS.create(world);
        if (pegasus == null) {
            return;
        }
        pegasus.setVariant(PegasusVariant.CLASSIC);
        pegasus.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                random.nextFloat() * 360.0f, 0.0f);
        world.spawnEntity(pegasus);
        PegasusMod.debug(() -> "Pegasus spawned naturally at " + spawnPos.toShortString());
    }

    private Optional<BlockPos> findPlatformSurface(ServerWorld world, BlockPos origin,
                                                   Block required, PegasusConfig config) {
        int minY = Math.max(config.naturalSpawning().platform().minimumY() + 1, origin.getY() - VERTICAL_RADIUS);
        int maxY = Math.min(world.getTopY() - 2, origin.getY() + VERTICAL_RADIUS);
        if (minY > maxY) {
            return Optional.empty();
        }
        for (int y = maxY; y >= minY; y--) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx += SEARCH_STEP) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz += SEARCH_STEP) {
                    BlockPos pos = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                    // Only inspect loaded chunks; never trigger generation from a tick handler.
                    if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                        continue;
                    }
                    if (world.getBlockState(pos).isOf(required)) {
                        return Optional.of(pos);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private List<Boolean> scanPlatform(ServerWorld world, BlockPos surface, Block required,
                                       SpawnPlatformRules.Settings platform) {
        int side = (int) Math.ceil(Math.sqrt(platform.minimumPlatformBlocks())) + 2;
        int half = Math.max(1, side / 2);
        List<Boolean> results = new ArrayList<>();
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                BlockPos pos = surface.add(dx, 0, dz);
                if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                    continue;
                }
                results.add(world.getBlockState(pos).isOf(required));
            }
        }
        return results;
    }

    /** Resolves a configured block id, accepting both {@code hay_block} and {@code minecraft:hay_block}. */
    private static Optional<Block> resolveBlock(String configured) {
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        String normalised = configured.strip().toLowerCase(Locale.ROOT).replace(' ', '_');
        Identifier id = Identifier.tryParse(normalised.contains(":") ? normalised : "minecraft:" + normalised);
        if (id == null) {
            return Optional.empty();
        }
        Block block = Registries.BLOCK.get(id);
        // Registries.BLOCK returns AIR for unknown ids, so treat that as "not found".
        return block == net.minecraft.block.Blocks.AIR ? Optional.empty() : Optional.of(block);
    }
}
