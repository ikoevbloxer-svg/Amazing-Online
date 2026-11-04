package ru.gorod.rusgorod.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.gorod.rusgorod.RusGorod;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.faction.FactionService;
import ru.gorod.rusgorod.law.CriminalCase;

import java.util.List;

public final class Network {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RusGorod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncPacket::encode).decoder(SyncPacket::decode).consumerMainThread(SyncPacket::handle).add();
        CHANNEL.messageBuilder(OpenDialogPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenDialogPacket::encode).decoder(OpenDialogPacket::decode).consumerMainThread(OpenDialogPacket::handle).add();
        CHANNEL.messageBuilder(DialogActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DialogActionPacket::encode).decoder(DialogActionPacket::decode).consumerMainThread(DialogActionPacket::handle).add();
        CHANNEL.messageBuilder(FactionActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FactionActionPacket::encode).decoder(FactionActionPacket::decode).consumerMainThread(FactionActionPacket::handle).add();
    }

    public static void sendTo(ServerPlayer p, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    /** Отправить клиенту полное состояние игрока и города. */
    public static void syncPlayer(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        CompoundTag t = new CompoundTag();
        t.put("rec", r.save());
        long day = level.getDayTime() / 24000L;
        t.putLong("day", day);
        t.putLong("time", level.getGameTime());
        t.putLong("dayTime", level.getDayTime());
        t.putBoolean("generated", d.generated);
        t.putLong("budget", d.budget);
        t.putDouble("sat", d.satisfaction);
        t.putDouble("crime", d.crimeIndex);
        t.putBoolean("rally", d.rallyUntil > level.getGameTime());
        t.putBoolean("rallyNotified", d.rallyNotified);
        t.putLong("funds", d.fundsOf(r.faction));
        t.putLong("income", FactionService.dailyIncome(r));
        ListTag news = new ListTag();
        for (String s : d.news) news.add(StringTag.valueOf(s));
        t.put("news", news);
        CompoundTag counts = new CompoundTag();
        CompoundTag chances = new CompoundTag();
        CompoundTag unmet = new CompoundTag();
        for (Faction f : Faction.values()) {
            counts.putInt(f.id, d.npcCount(f));
            chances.putInt(f.id, (int) Math.round(FactionService.chance(r, f) * 100));
            ListTag l = new ListTag();
            List<String> u = FactionService.unmetRequirements(r, f, day);
            for (String s : u) l.add(StringTag.valueOf(s));
            unmet.put(f.id, l);
        }
        t.put("counts", counts);
        t.put("chances", chances);
        t.put("unmet", unmet);
        ListTag cases = new ListTag();
        int n = 0;
        for (int i = d.cases.size() - 1; i >= 0 && n < 10; i--) {
            CriminalCase cs = d.cases.get(i);
            if (r.faction.isState() || (cs.offenderIsPlayer && p.getUUID().equals(cs.offender))) {
                cases.add(StringTag.valueOf(cs.shortInfo() + (cs.stage == CriminalCase.Stage.CLOSED ? " (" + cs.closeReason + ")" : "")));
                n++;
            }
        }
        t.put("cases", cases);
        sendTo(p, new SyncPacket(t));
    }

    private Network() {
    }
}
