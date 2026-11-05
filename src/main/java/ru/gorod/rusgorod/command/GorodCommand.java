package ru.gorod.rusgorod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.faction.FactionService;
import ru.gorod.rusgorod.law.CriminalCase;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.social.PlayerInteractions;
import ru.gorod.rusgorod.world.CityGenerator;

import java.util.Arrays;

/** /gorod — информация о городе и админ-команды; /r — рация фракции; /do, /try — отыгрыш. */
public final class GorodCommand {

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("gorod")
                .executes(c -> info(c.getSource()))
                .then(Commands.literal("info").executes(c -> info(c.getSource())))
                .then(Commands.literal("news").executes(c -> news(c.getSource())))
                .then(Commands.literal("cases").executes(c -> cases(c.getSource())))
                .then(Commands.literal("tp").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("place", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(GorodData.get(c.getSource().getLevel()).locs.keySet(), b))
                                .executes(c -> tp(c.getSource(), StringArgumentType.getString(c, "place")))))
                .then(Commands.literal("setfaction").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("faction", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Faction.values()).map(f -> f.id), b))
                                        .then(Commands.argument("rank", IntegerArgumentType.integer(0, 20))
                                                .executes(c -> setFaction(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                        StringArgumentType.getString(c, "faction"), IntegerArgumentType.getInteger(c, "rank")))))))
                .then(Commands.literal("money").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                .executes(c -> money(c.getSource(), IntegerArgumentType.getInteger(c, "amount")))))
                .then(Commands.literal("diploma").requires(s -> s.hasPermission(2)).executes(c -> diploma(c.getSource())))
                .then(Commands.literal("rally").requires(s -> s.hasPermission(2)).executes(c -> rally(c.getSource())))
                .then(Commands.literal("clearwanted").requires(s -> s.hasPermission(2)).executes(c -> clearWanted(c.getSource())))
                .then(Commands.literal("regenerate").requires(s -> s.hasPermission(3)).executes(c -> regen(c.getSource())))
                .then(Commands.literal("recount").requires(s -> s.hasPermission(2)).executes(c -> recount(c.getSource()))));

        // рация: сообщение всем игрокам своей фракции
        d.register(Commands.literal("r")
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(c -> radio(c.getSource(), StringArgumentType.getString(c, "text")))));
        // /do — описание обстановки от третьего лица
        d.register(Commands.literal("do")
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(c -> doCmd(c.getSource(), StringArgumentType.getString(c, "text")))));
        // /try — попытка действия: удачно / неудачно
        d.register(Commands.literal("try")
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(c -> tryCmd(c.getSource(), StringArgumentType.getString(c, "text")))));
    }

    private static int radio(CommandSourceStack s, String text) {
        ServerPlayer p = s.getPlayer();
        if (p == null) return 0;
        GorodData d = GorodData.get(s.getLevel());
        PlayerRecord r = d.player(p.getUUID());
        if (r.faction == Faction.CIVILIAN) {
            s.sendFailure(Component.literal("Рация есть только у сотрудников и членов группировок."));
            return 0;
        }
        String prefix = r.faction.isState() ? "[Рация " + r.faction.title + "] " : "[Телефон «" + r.faction.title + "»] ";
        Component msg = Component.literal(prefix).withStyle(r.faction.isState() ? ChatFormatting.DARK_AQUA : ChatFormatting.DARK_RED)
                .append(Component.literal(r.faction.rankName(r.rank) + " " + p.getName().getString() + ": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
        for (ServerPlayer o : s.getServer().getPlayerList().getPlayers()) {
            if (d.player(o.getUUID()).faction == r.faction) o.sendSystemMessage(msg);
        }
        return 1;
    }

    private static int doCmd(CommandSourceStack s, String text) {
        ServerPlayer p = s.getPlayer();
        if (p == null) return 0;
        Component msg = Component.literal("* " + text + " (" + p.getName().getString() + ")").withStyle(ChatFormatting.LIGHT_PURPLE);
        for (ServerPlayer o : s.getLevel().getEntitiesOfClass(ServerPlayer.class, new AABB(p.blockPosition()).inflate(20))) o.sendSystemMessage(msg);
        return 1;
    }

    private static int tryCmd(CommandSourceStack s, String text) {
        ServerPlayer p = s.getPlayer();
        if (p == null) return 0;
        boolean ok = p.getRandom().nextBoolean();
        PlayerInteractions.me(s.getLevel(), p, text + (ok ? " — удачно" : " — неудачно"));
        return 1;
    }

    private static int info(CommandSourceStack s) {
        GorodData d = GorodData.get(s.getLevel());
        if (!d.generated) {
            s.sendSuccess(() -> Component.literal(CityGenerator.isRunning() ? "Город строится: " + CityGenerator.progressPercent() + "%" : "Город ещё не построен."), false);
            return 1;
        }
        BlockPos sq = d.loc("square");
        s.sendSuccess(() -> Component.literal("══ г. " + CityGenerator.CITY_NAME + " ══").withStyle(ChatFormatting.GOLD), false);
        s.sendSuccess(() -> Component.literal("Площадь: X " + sq.getX() + " Z " + sq.getZ() + "; воинская часть: X " + (d.baseMinX + 32) + " Z " + (d.baseMinZ + 32)), false);
        s.sendSuccess(() -> Component.literal("Бюджет: " + d.budget + " ₽ | доверие к власти: " + Math.round(d.satisfaction) + "% | преступность: " + Math.round(d.crimeIndex)), false);
        StringBuilder sb = new StringBuilder("Население: ");
        for (Faction f : Faction.values()) sb.append(f.title).append(" ").append(d.npcCount(f)).append("; ");
        s.sendSuccess(() -> Component.literal(sb.toString()).withStyle(ChatFormatting.GRAY), false);
        s.sendSuccess(() -> Component.literal("RP-команды: /r <текст> — рация фракции, /me <действие>, /do <обстановка>, /try <действие>. ПКМ по игроку — взаимодействие.").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int news(CommandSourceStack s) {
        GorodData d = GorodData.get(s.getLevel());
        s.sendSuccess(() -> Component.literal("══ «Вестник " + CityGenerator.CITY_NAME + "а» ══").withStyle(ChatFormatting.GOLD), false);
        for (String n : d.news) s.sendSuccess(() -> Component.literal("• " + n), false);
        return 1;
    }

    private static int cases(CommandSourceStack s) {
        GorodData d = GorodData.get(s.getLevel());
        int open = 0;
        for (CriminalCase c : d.cases) {
            if (c.stage == CriminalCase.Stage.CLOSED) continue;
            open++;
            s.sendSuccess(() -> Component.literal(c.shortInfo()).withStyle(ChatFormatting.AQUA), false);
        }
        final int o = open;
        s.sendSuccess(() -> Component.literal("Открытых дел: " + o), false);
        return 1;
    }

    private static int tp(CommandSourceStack s, String place) {
        ServerPlayer p = s.getPlayer();
        if (p == null) return 0;
        GorodData d = GorodData.get(s.getLevel());
        BlockPos pos = d.locs.get(place);
        if (pos == null) {
            s.sendFailure(Component.literal("Нет такого места"));
            return 0;
        }
        p.teleportTo(s.getServer().overworld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p.getYRot(), p.getXRot());
        return 1;
    }

    private static int setFaction(CommandSourceStack s, ServerPlayer p, String id, int rank) {
        PlayerRecord r = GorodData.get(s.getLevel()).player(p.getUUID());
        Faction f = Faction.byId(id);
        if (f == Faction.CIVILIAN) {
            r.faction = Faction.CIVILIAN;
            r.rank = 0;
            r.uniform = false;
            FactionService.changed(p);
        } else {
            FactionService.enlist(p, r, f, rank);
        }
        s.sendSuccess(() -> Component.literal(p.getName().getString() + " → " + r.faction.title + ", " + r.faction.rankName(r.rank)), true);
        return 1;
    }

    private static int money(CommandSourceStack s, int amount) {
        ServerPlayer p = s.getPlayer();
        if (p == null) return 0;
        GorodData.get(s.getLevel()).player(p.getUUID()).balance += amount;
        Network.syncPlayer(p);
        return 1;
    }

    private static int diploma(CommandSourceStack s) {
        ServerPlayer p = s.getPlayer();
        if (p == null) return 0;
        PlayerRecord r = GorodData.get(s.getLevel()).player(p.getUUID());
        r.legalEducation = true;
        r.enrolled = false;
        Network.syncPlayer(p);
        s.sendSuccess(() -> Component.literal("Выдано высшее юридическое образование."), true);
        return 1;
    }

    private static int rally(CommandSourceStack s) {
        GorodData d = GorodData.get(s.getLevel());
        d.satisfaction = 20;
        d.rallyUntil = -1;
        s.sendSuccess(() -> Component.literal("Доверие к власти снижено до 20%. Митинг начнётся в 10:00 игрового времени (или /time set 4000)."), true);
        return 1;
    }

    private static int clearWanted(CommandSourceStack s) {
        ServerPlayer p = s.getPlayer();
        if (p == null) return 0;
        PlayerRecord r = GorodData.get(s.getLevel()).player(p.getUUID());
        r.wanted.clear();
        r.jailUntil = -1;
        r.cuffed = false;
        r.trialAt = -1;
        Network.syncPlayer(p);
        return 1;
    }

    private static int regen(CommandSourceStack s) {
        GorodData d = GorodData.get(s.getLevel());
        d.generated = false;
        d.setDirty();
        s.sendSuccess(() -> Component.literal("Город будет перестроен (существующие NPC сохранятся). Новые здания появятся по новой планировке."), true);
        return 1;
    }

    /** Пересчитать реестр NPC по фактически загруженным сущностям. */
    private static int recount(CommandSourceStack s) {
        ServerLevel level = s.getLevel();
        GorodData d = GorodData.get(level);
        d.npcs.clear();
        int n = 0;
        for (var e : level.getAllEntities()) {
            if (e instanceof CitizenEntity c && c.isAlive()) {
                d.npcs.put(c.getUUID(), c.getFaction());
                n++;
            }
        }
        d.setDirty();
        final int cnt = n;
        s.sendSuccess(() -> Component.literal("Учтено NPC: " + cnt + ". Недостающие будут пополнены автоматически."), true);
        return 1;
    }

    private GorodCommand() {
    }
}
