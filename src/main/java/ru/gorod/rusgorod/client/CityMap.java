package ru.gorod.rusgorod.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import ru.gorod.rusgorod.RusGorod;

import java.io.File;
import java.io.FileInputStream;

/**
 * Карта города, которая «прорисовывается» по мере исследования: клиент читает верхние блоки загруженных чанков
 * и раскрашивает их как картограф (цвет карты + рельеф). Неисследованное — тёмное. Кэш сохраняется на диск.
 */
public final class CityMap {
    public static final ResourceLocation TEX = new ResourceLocation(RusGorod.MODID, "dynamic/citymap");
    public static int x0, z0, w, h;
    private static NativeImage img;
    private static DynamicTexture tex;
    private static short[] heights;
    private static String key = "";
    private static boolean dirty;
    private static int cursor;
    private static int uploadTimer, saveTimer;

    public static boolean ready() {
        return tex != null;
    }

    private static void ensure() {
        int[] b = ClientData.tag.getIntArray("bounds");
        if (b.length < 8 || (b[0] == 0 && b[2] == 0)) return;
        int minX = b[0], minZ = b[1], maxX = b[2], maxZ = b[3];
        if (b[6] != 0 || b[4] != 0) {
            minX = Math.min(minX, b[4]);
            minZ = Math.min(minZ, b[5]);
            maxX = Math.max(maxX, b[6]);
            maxZ = Math.max(maxZ, b[7]);
        }
        minX -= 48;
        minZ -= 48;
        maxX += 48;
        maxZ += 48;
        int nw = Math.min(1024, maxX - minX), nh = Math.min(1024, maxZ - minZ);
        String k = minX + "_" + minZ + "_" + nw + "_" + nh;
        if (k.equals(key) && tex != null) return;
        save();
        close();
        key = k;
        x0 = minX;
        z0 = minZ;
        w = nw;
        h = nh;
        img = new NativeImage(NativeImage.Format.RGBA, w, h, true);
        img.fillRect(0, 0, w, h, 0);
        heights = new short[w * h];
        File f = file();
        if (f != null && f.exists()) {
            try (FileInputStream in = new FileInputStream(f)) {
                NativeImage old = NativeImage.read(in);
                if (old.getWidth() == w && old.getHeight() == h) {
                    img.copyFrom(old);
                }
                old.close();
            } catch (Exception e) {
                RusGorod.LOGGER.warn("[RusGorod] карта: не удалось прочитать кэш {}", e.toString());
            }
        }
        tex = new DynamicTexture(img);
        Minecraft.getInstance().getTextureManager().register(TEX, tex);
        cursor = 0;
    }

    private static File file() {
        Minecraft mc = Minecraft.getInstance();
        String world = mc.getSingleplayerServer() != null ? mc.getSingleplayerServer().getWorldData().getLevelName()
                : mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "unknown";
        world = world.replaceAll("[^A-Za-z0-9А-Яа-я_.-]", "_");
        File dir = new File(mc.gameDirectory, "rusgorod_maps");
        if (!dir.exists() && !dir.mkdirs()) return null;
        return new File(dir, world + "_" + key + ".png");
    }

    public static void save() {
        if (img == null) return;
        try {
            File f = file();
            if (f != null) img.writeToFile(f);
        } catch (Exception e) {
            RusGorod.LOGGER.warn("[RusGorod] карта: не удалось сохранить {}", e.toString());
        }
    }

    public static void close() {
        if (tex != null) {
            Minecraft.getInstance().getTextureManager().release(TEX);
            tex = null;
            img = null;
        }
    }

    /** Каждый тик: прорисовываем порцию столбцов вокруг игрока. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !ClientData.has()) return;
        ensure();
        if (img == null) return;
        ClientLevel level = mc.level;
        int R = 96, side = R * 2;
        int px = mc.player.getBlockX(), pz = mc.player.getBlockZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int budget = 1400;
        for (int n = 0; n < budget; n++) {
            cursor = (cursor + 1) % (side * side);
            int wx = px - R + cursor % side, wz = pz - R + cursor / side;
            int ix = wx - x0, iz = wz - z0;
            if (ix < 0 || iz < 0 || ix >= w || iz >= h) continue;
            if (!level.getChunkSource().hasChunk(wx >> 4, wz >> 4)) continue;
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
            if (y <= level.getMinBuildHeight()) continue;
            m.set(wx, y, wz);
            BlockState st = level.getBlockState(m);
            MapColor mcol = st.getMapColor(level, m);
            int guard = 0;
            while (mcol == MapColor.NONE && y > level.getMinBuildHeight() && guard++ < 12) {
                m.setY(--y);
                st = level.getBlockState(m);
                mcol = st.getMapColor(level, m);
            }
            heights[iz * w + ix] = (short) y;
            int north = iz > 0 ? heights[(iz - 1) * w + ix] : y;
            MapColor.Brightness br = y > north ? MapColor.Brightness.HIGH : y < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
            if (mcol == MapColor.WATER) {
                int depth = 0;
                BlockPos.MutableBlockPos q = m.mutable();
                while (depth < 8 && level.getBlockState(q.move(0, -1, 0)).getMapColor(level, q) == MapColor.WATER) depth++;
                br = depth > 4 ? MapColor.Brightness.LOW : depth > 1 ? MapColor.Brightness.NORMAL : MapColor.Brightness.HIGH;
            }
            int abgr = mcol.calculateRGBColor(br);
            img.setPixelRGBA(ix, iz, stylize(abgr));
            dirty = true;
        }
        if (dirty && ++uploadTimer >= 10) {
            uploadTimer = 0;
            dirty = false;
            tex.upload();
        }
        if (++saveTimer >= 20 * 60) {
            saveTimer = 0;
            save();
        }
    }

    /** Лёгкая «ночная» стилизация: чуть темнее и холоднее, чтобы метки читались. */
    private static int stylize(int abgr) {
        int r = abgr & 255, g = (abgr >> 8) & 255, b = (abgr >> 16) & 255;
        int lum = (r * 3 + g * 6 + b) / 10;
        r = (r * 6 + lum * 2) / 10;
        g = (g * 6 + lum * 2) / 10;
        b = (b * 6 + lum * 2) / 10 + 10;
        return 0xFF000000 | (Math.min(255, b) << 16) | (Math.min(255, g) << 8) | Math.min(255, r);
    }

    private CityMap() {
    }
}
