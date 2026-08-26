package com.cetori.privates;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.block.Block;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClaimManager {
    private final List<CetoriPrivates.Claim> claims = new CopyOnWriteArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Path file;
    private int nextId = 1;

    public void load(MinecraftServer server) {
        file = server.getSavePath(WorldSavePath.ROOT).resolve("cetori_privates.json");
        if (!Files.exists(file)) return;

        try (Reader reader = Files.newBufferedReader(file)) {
            List<CetoriPrivates.Claim> loaded = gson.fromJson(reader,
                new TypeToken<List<CetoriPrivates.Claim>>() {}.getType());
            claims.clear();
            if (loaded != null) claims.addAll(loaded);
            nextId = claims.stream().mapToInt(c -> c.id).max().orElse(0) + 1;
            server.getLogger().info("[CetoriPrivates] Loaded {} claims.", claims.size());
        } catch (Exception e) {
            server.getLogger().error("[CetoriPrivates] Could not load claims.", e);
        }
    }

    public synchronized void save(MinecraftServer server) {
        if (file == null) {
            file = server.getSavePath(WorldSavePath.ROOT).resolve("cetori_privates.json");
        }

        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp)) {
                gson.toJson(claims, writer);
            }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            server.getLogger().error("[CetoriPrivates] Could not save claims.", e);
        }
    }

    public CetoriPrivates.Claim create(ServerPlayerEntity player, ServerWorld world,
                                       BlockPos pos, int size, Block block) {
        if (overlaps(world, pos, size, null)) return null;

        CetoriPrivates.Claim claim = new CetoriPrivates.Claim(
            nextId++, player.getUuid(), player.getName().getString(),
            world.getRegistryKey().getValue().toString(), pos, size, block
        );

        claims.add(claim);
        save(world.getServer());
        return claim;
    }

    public CetoriPrivates.Claim get(int id) {
        return claims.stream().filter(c -> c.id == id).findFirst().orElse(null);
    }

    public CetoriPrivates.Claim remove(int id) {
        CetoriPrivates.Claim c = get(id);
        if (c != null) claims.remove(c);
        return c;
    }

    public CetoriPrivates.Claim findAt(ServerWorld world, BlockPos pos) {
        String dim = world.getRegistryKey().getValue().toString();
        return claims.stream()
            .filter(c -> c.dimension.equals(dim) && c.contains(pos))
            .min(Comparator.comparingInt(c -> c.size))
            .orElse(null);
    }

    public boolean overlaps(ServerWorld world, BlockPos pos, int size,
                            CetoriPrivates.Claim ignore) {
        String dim = world.getRegistryKey().getValue().toString();
        return claims.stream().anyMatch(c ->
            (ignore == null || c.id != ignore.id) &&
            c.dimension.equals(dim) &&
            c.overlaps(pos, size)
        );
    }

    public List<CetoriPrivates.Claim> byOwner(UUID uuid) {
        return claims.stream().filter(c -> c.owner.equals(uuid)).toList();
    }
}
