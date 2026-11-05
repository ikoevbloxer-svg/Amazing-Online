package ru.gorod.rusgorod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.faction.FactionService;
import ru.gorod.rusgorod.job.JobManager;
import ru.gorod.rusgorod.quest.QuestManager;
import ru.gorod.rusgorod.study.LawSchool;

import java.util.function.Supplier;

/** Действия из меню фракций (клавиша G). */
public class FactionActionPacket {
    public final String action;
    public final String arg;

    public FactionActionPacket(String action, String arg) {
        this.action = action;
        this.arg = arg;
    }

    public static void encode(FactionActionPacket m, FriendlyByteBuf buf) {
        buf.writeUtf(m.action, 32);
        buf.writeUtf(m.arg, 64);
    }

    public static FactionActionPacket decode(FriendlyByteBuf buf) {
        return new FactionActionPacket(buf.readUtf(32), buf.readUtf(64));
    }

    public static void handle(FactionActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) return;
        switch (m.action) {
            case "apply" -> FactionService.apply(p, Faction.byId(m.arg));
            case "leave" -> FactionService.leave(p);
            case "promote" -> FactionService.promote(p);
            case "gear" -> {
                PlayerRecord r = GorodData.get(p.serverLevel()).player(p.getUUID());
                FactionService.giveGear(p, r, false);
            }
            case "quest_take" -> QuestManager.take(p, m.arg);
            case "quest_drop" -> QuestManager.drop(p);
            case "quest_turnin" -> QuestManager.turnIn(p);
            case "job_take" -> JobManager.take(p, m.arg);
            case "job_quit" -> JobManager.quit(p);
            case "job_action" -> JobManager.action(p, m.arg);
            case "uniform" -> FactionService.toggleUniform(p);
            case "uni_enroll" -> LawSchool.enroll(p);
            case "uni_exam" -> LawSchool.startExam(p);
            case "pay_fines" -> {
                PlayerRecord r = GorodData.get(p.serverLevel()).player(p.getUUID());
                if (r.fineDebt > 0 && r.balance >= r.fineDebt) {
                    r.balance -= r.fineDebt;
                    GorodData.get(p.serverLevel()).budget += r.fineDebt;
                    p.sendSystemMessage(net.minecraft.network.chat.Component.literal("«Госуслуги»: штрафы на " + r.fineDebt + " ₽ оплачены. Спасибо!")
                            .withStyle(net.minecraft.ChatFormatting.GREEN));
                    r.fineDebt = 0;
                    r.fineDueDay = -1;
                    r.wanted.remove(ru.gorod.rusgorod.law.Article.KOAP_20_25);
                } else if (r.fineDebt > 0) {
                    p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Недостаточно средств для оплаты " + r.fineDebt + " ₽.")
                            .withStyle(net.minecraft.ChatFormatting.RED));
                }
            }
            default -> {
            }
        }
        Network.syncPlayer(p);
    }
}
