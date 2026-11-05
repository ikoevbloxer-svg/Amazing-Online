package ru.gorod.rusgorod.law;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import ru.gorod.rusgorod.Config;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.item.GunItem;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.quest.QuestManager;
import ru.gorod.rusgorod.world.CityManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Правовая система города: фиксация правонарушений, свидетели, КУСП, уголовные дела,
 * розыск, задержание, СИЗО/ИВС, судимость.
 */
public final class CrimeSystem {
    private static final Map<String, Long> ASSAULT_COOLDOWN = new HashMap<>();

    // ======================================================================== розыск

    public static boolean isWanted(LivingEntity e) {
        if (e instanceof CitizenEntity c) return c.isWanted() && !c.isJailed();
        if (e instanceof ServerPlayer p) {
            if (p.isCreative() || p.isSpectator()) return false;
            PlayerRecord r = GorodData.get(p.serverLevel()).player(p.getUUID());
            return r.isWanted() && !r.isJailed(p.serverLevel().getGameTime());
        }
        return false;
    }

    public static int wantedStars(ServerPlayer p) {
        return GorodData.get(p.serverLevel()).player(p.getUUID()).wantedStars();
    }

    private static void setWanted(ServerLevel level, LivingEntity offender, Article a) {
        if (offender instanceof CitizenEntity c) {
            c.addWanted(a);
        } else if (offender instanceof ServerPlayer p) {
            PlayerRecord r = GorodData.get(level).player(p.getUUID());
            if (!r.wanted.contains(a)) r.wanted.add(a);
            p.sendSystemMessage(Component.literal("⚠ Вы объявлены в розыск: " + a.full()).withStyle(ChatFormatting.RED));
            Network.syncPlayer(p);
        }
    }

    // ======================================================================== правонарушения

    public static void commitByNpc(ServerLevel level, CitizenEntity offender, Article a, @Nullable LivingEntity victim) {
        commit(level, offender, a, offender.blockPosition(), victim);
    }

    public static void commitByPlayer(ServerPlayer p, Article a, @Nullable LivingEntity victim) {
        PlayerRecord r = GorodData.get(p.serverLevel()).player(p.getUUID());
        if (!r.faction.isState()) r.crimRep += a.administrative ? 1 : 2 + a.severity.ordinal();
        r.civRep = Math.max(0, r.civRep - (a.administrative ? 1 : 3));
        commit(p.serverLevel(), p, a, p.blockPosition(), victim);
    }

    private static void commit(ServerLevel level, LivingEntity offender, Article a, BlockPos pos, @Nullable LivingEntity victim) {
        GorodData d = GorodData.get(level);
        d.crimeIndex += a.administrative ? 0.3 : 1 + a.severity.ordinal();
        d.lastCrime = pos;
        d.setDirty();
        RandomSource r = level.random;

        // 1. Очевидцы из числа сотрудников — пресечение на месте
        List<CitizenEntity> cops = level.getEntitiesOfClass(CitizenEntity.class, new AABB(pos).inflate(24),
                c -> c != offender && c.getFaction().isLawEnforcement() && !c.isJailed() && c.hasLineOfSight(offender));
        boolean lawPlayerSaw = false;
        for (Player pl : level.players()) {
            if (pl == offender || pl.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > 24 * 24) continue;
            PlayerRecord pr = d.player(pl.getUUID());
            if (pr.faction.isLawEnforcement() && pl.hasLineOfSight(offender)) {
                lawPlayerSaw = true;
                pl.sendSystemMessage(Component.literal("[Служба] На ваших глазах совершено правонарушение: " + a.full()
                        + ". Нарушитель: " + nameOf(offender) + ". Пресеките (ст. 12 ФЗ «О полиции»).").withStyle(ChatFormatting.GOLD));
            }
        }
        if (!cops.isEmpty() || lawPlayerSaw) {
            setWanted(level, offender, a);
            boolean said = false;
            for (CitizenEntity cop : cops) {
                if (cop.getFaction().canArrest() && cop.getTarget() == null) {
                    cop.setTarget(offender);
                    if (!said) {
                        cop.say(cop.getFaction() == Faction.UFSB ? "Стоять! ФСБ!" : "Стоять! Полиция! Вы задержаны!");
                        said = true;
                    }
                }
            }
            if (!a.administrative) {
                CriminalCase cs = createCase(d, level, a, offender, true, pos);
                cs.stage = CriminalCase.Stage.SUSPECT_WANTED;
                notifyFaction(level, a.investigator, "[КУСП] " + cs.shortInfo(), ChatFormatting.AQUA);
            }
            return;
        }

        // 2. Гражданские очевидцы и потерпевший — сообщение в полицию (ст. 141 УПК — заявление о преступлении)
        List<CitizenEntity> seen = level.getEntitiesOfClass(CitizenEntity.class, new AABB(pos).inflate(16),
                c -> c != offender && c != victim && c.getFaction() == Faction.CIVILIAN && !c.isJailed() && c.hasLineOfSight(offender));
        // друзья игрока промолчат, недруги — сообщат обязательно
        boolean hostileWitness = false;
        if (offender instanceof ServerPlayer sp) {
            seen.removeIf(c -> c.relation(sp.getUUID()) >= 50);
            for (CitizenEntity c : seen) {
                c.changeRelation(sp.getUUID(), a.administrative ? -3 : -15);
                if (c.relation(sp.getUUID()) <= -30) hostileWitness = true;
            }
        }
        int witnesses = seen.size();
        double report = 0;
        if (victim != null && victim.isAlive()) report = 0.6;
        if (witnesses > 0) report = Math.max(report, Math.min(0.9, 0.35 + 0.1 * witnesses));
        if (hostileWitness) report = 1.0;
        if (a.administrative) report *= 0.3;
        if (r.nextDouble() >= report) return; // латентное правонарушение

        if (a.administrative) {
            setWanted(level, offender, a);
            return;
        }
        // свидетель-игроку: 30 секунд, чтобы договориться (или нарваться на ст. 309 УК)
        if (offender instanceof ServerPlayer sp && witnesses > 0) {
            CitizenEntity w = seen.get(0);
            for (CitizenEntity c : seen) if (c.distanceToSqr(sp) < w.distanceToSqr(sp)) w = c;
            final CitizenEntity witness = w;
            witness.witnessOf = sp.getUUID();
            witness.witnessArticle = a;
            witness.say("Я всё видел! Сейчас позвоню в полицию!", true);
            witness.setTarget(sp); // убегает от преступника
            sp.sendSystemMessage(Component.literal("Свидетель " + witness.fullName() + " видел преступление и через 30 секунд позвонит в полицию. "
                    + "Можно попробовать договориться (ПКМ по нему) — но подкуп и запугивание свидетеля сами по себе преступление (ст. 309 УК РФ).")
                    .withStyle(ChatFormatting.YELLOW));
            final int wn = witnesses;
            ru.gorod.rusgorod.util.Scheduler.later(600, () -> {
                if (!witness.isAlive() || witness.witnessOf == null) return; // договорились или убит
                witness.witnessOf = null;
                witness.say("Алло, полиция? Я свидетель преступления...", true);
                fileReport(level, GorodData.get(level), a, offender, pos, wn);
            });
            return;
        }
        fileReport(level, d, a, offender, pos, witnesses);
    }

    private static void fileReport(ServerLevel level, GorodData d, Article a, LivingEntity offender, BlockPos pos, int witnesses) {
        boolean identified = level.random.nextDouble() < (witnesses > 0 ? 0.55 : 0.3);
        CriminalCase cs = createCase(d, level, a, offender, identified, pos);
        notifyFaction(level, Faction.UMVD, "[Дежурная часть] Зарегистрировано сообщение о преступлении: " + a.full()
                + " (КУСП №" + cs.id + "). Место: " + pos.getX() + ", " + pos.getZ(), ChatFormatting.AQUA);
        if (a.investigator != Faction.UMVD)
            notifyFaction(level, a.investigator, "[КУСП] Материал направлен по подследственности (ст. 151 УПК): " + cs.shortInfo(), ChatFormatting.AQUA);
    }

    /** Дело, при котором правонарушитель уже установлен (задержан с поличным, выявлен на ОРМ). */
    public static void registerWitnessedCase(ServerLevel level, CitizenEntity offender, Article a, BlockPos pos) {
        if (a.administrative) return;
        GorodData d = GorodData.get(level);
        CriminalCase cs = createCase(d, level, a, offender, true, pos);
        cs.stage = CriminalCase.Stage.SUSPECT_WANTED;
    }

    private static CriminalCase createCase(GorodData d, ServerLevel level, Article a, LivingEntity offender, boolean identified, BlockPos pos) {
        CriminalCase cs = new CriminalCase();
        cs.id = d.nextCaseId++;
        cs.article = a;
        cs.offender = offender.getUUID();
        cs.offenderName = nameOf(offender);
        cs.offenderIsPlayer = offender instanceof ServerPlayer;
        cs.identified = identified;
        cs.pos = pos;
        cs.created = level.getGameTime();
        cs.stage = CriminalCase.Stage.REPORTED;
        d.cases.add(cs);
        d.setDirty();
        return cs;
    }

    // ======================================================================== реакция на насилие

    public static void onCitizenHurt(ServerLevel level, CitizenEntity victim, LivingEntity attacker) {
        if (attacker instanceof ServerPlayer p) {
            if (lawfulForceByPlayer(level, p, victim)) return;
            if (!cooldown(attacker, victim, level)) return;
            boolean official = victim.getFaction().isLawEnforcement() || victim.getFaction() == Faction.ARMY;
            commitByPlayer(p, official ? Article.UK_318 : Article.KOAP_6_1_1, victim);
        } else if (attacker instanceof CitizenEntity a) {
            if (a.getFaction().canArrest() && victim.isWanted()) return; // правомерное применение силы
            if (victim.getTarget() == a && victim.getLastHurtMob() == a) return; // самооборона ст. 37 УК
            if (a.getFaction() == Faction.ARMY && victim.getTarget() == a) return;
            if (!cooldown(attacker, victim, level)) return;
            boolean official = victim.getFaction().isLawEnforcement();
            commitByNpc(level, a, official ? Article.UK_318 : (a.getFaction().isCriminal() ? Article.UK_213 : Article.KOAP_6_1_1), victim);
        }
    }

    public static void onCitizenKilled(ServerLevel level, CitizenEntity victim, @Nullable Entity killer) {
        GorodData d = GorodData.get(level);
        // Закрытие дел в отношении умершего (п. 4 ч. 1 ст. 24 УПК РФ)
        for (CriminalCase cs : d.cases) {
            if (victim.getUUID().equals(cs.offender) && cs.stage != CriminalCase.Stage.CLOSED) {
                cs.stage = CriminalCase.Stage.CLOSED;
                cs.closeReason = "в связи со смертью подозреваемого (п. 4 ч. 1 ст. 24 УПК РФ)";
            }
        }
        if (killer instanceof ServerPlayer p) {
            QuestManager.onKill(p, victim.getFaction());
            PlayerRecord pr = d.player(p.getUUID());
            if (lawfulForceByPlayer(level, p, victim)) {
                if (pr.faction.isState()) p.sendSystemMessage(Component.literal("Применение оружия признано правомерным (ст. 23 ФЗ «О полиции», ст. 37 УК РФ). Проведена служебная проверка.").withStyle(ChatFormatting.GRAY));
                return;
            }
            if (pr.faction.isCriminal()) pr.crimRep += 2;
            commitByPlayer(p, victim.getFaction().isLawEnforcement() ? Article.UK_317 : Article.UK_105, victim);
            CityManager.changeSatisfaction(d, -3);
            d.addNews("Происшествие: убит " + victim.fullName() + ". Возбуждено уголовное дело.");
        } else if (killer instanceof CitizenEntity a) {
            if (a.getFaction().canArrest() && victim.isWanted()) return;
            commitByNpc(level, a, victim.getFaction().isLawEnforcement() ? Article.UK_317 : Article.UK_105, null);
            CityManager.changeSatisfaction(d, -2);
            d.addNews("Криминал: в городе убит " + victim.fullName() + ".");
        }
    }

    public static void onPlayerKilledPlayer(ServerPlayer killer, ServerPlayer victim) {
        GorodData d = GorodData.get(killer.serverLevel());
        PlayerRecord kr = d.player(killer.getUUID());
        PlayerRecord vr = d.player(victim.getUUID());
        if (kr.faction.canArrest() && vr.isWanted()) return;
        if (victim.getLastHurtByMob() == killer && killer.getLastHurtByMob() == victim) return;
        commitByPlayer(killer, vr.faction.isLawEnforcement() ? Article.UK_317 : Article.UK_105, victim);
    }

    public static void onPlayerShot(Player shooter, LivingEntity target) {
        if (!(shooter instanceof ServerPlayer p)) return;
        PlayerRecord r = GorodData.get(p.serverLevel()).player(p.getUUID());
        if (!r.faction.isState() && p.getMainHandItem().getItem() instanceof GunItem && p.getRandom().nextInt(4) == 0) {
            commitByPlayer(p, Article.UK_222, null);
        }
    }

    private static boolean lawfulForceByPlayer(ServerLevel level, ServerPlayer p, CitizenEntity victim) {
        if (victim.getTarget() == p) return true; // необходимая оборона
        PlayerRecord r = GorodData.get(level).player(p.getUUID());
        if (r.faction.isState() && victim.isWanted()) return true;
        // Причинение вреда при задержании лица, совершившего преступление (ст. 38 УК РФ) — для любого гражданина
        for (Article a : victim.wanted) if (!a.administrative) return true;
        if (r.faction.isState() && victim.getTarget() != null && victim.getTarget() != victim && victim.getFaction().isCriminal()) return true;
        return false;
    }

    private static boolean cooldown(Entity a, Entity b, ServerLevel level) {
        String key = a.getUUID() + "|" + b.getUUID();
        Long last = ASSAULT_COOLDOWN.get(key);
        long now = level.getGameTime();
        if (last != null && now - last < 300) return false;
        ASSAULT_COOLDOWN.put(key, now);
        if (ASSAULT_COOLDOWN.size() > 500) ASSAULT_COOLDOWN.clear();
        return true;
    }

    // ======================================================================== задержание

    public static void tryArrest(ServerLevel level, CitizenEntity officer, LivingEntity target) {
        if (target instanceof CitizenEntity tc) {
            double resist = tc.getFaction().isCriminal() ? 0.45 : 0.12;
            if (tc.getHealth() < tc.getMaxHealth() * 0.5f || level.random.nextDouble() > resist) {
                jailNpc(level, tc, officer.displayTitle());
                officer.setTarget(null);
                officer.say("Задержанный доставлен. Составляю протокол (ст. 92 УПК РФ).");
            } else {
                officer.doHurtTarget(tc);
                if (!tc.wanted.contains(Article.KOAP_19_3)) {
                    tc.addWanted(Article.KOAP_19_3);
                    officer.say("Оказывает сопротивление! Применяю физическую силу (ст. 20 ФЗ «О полиции»).");
                }
                if (tc.isCombatant()) tc.setTarget(officer);
            }
        } else if (target instanceof ServerPlayer sp) {
            if (sp.getHealth() <= 8f || sp.isCrouching()) {
                arrestPlayer(level, officer, sp, sp.isCrouching());
                officer.setTarget(null);
            } else {
                officer.doHurtTarget(sp);
                officer.say("Полиция! Прекратить сопротивление! (присядьте, чтобы сдаться)");
            }
        }
    }

    public static boolean playerArrestsNpc(ServerPlayer officer, CitizenEntity target) {
        ServerLevel level = officer.serverLevel();
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(officer.getUUID());
        if (!r.faction.canArrest()) return false;
        if (!target.isWanted()) {
            officer.sendSystemMessage(Component.literal("Оснований для задержания нет (ст. 91 УПК РФ, ст. 14 ФЗ «О полиции»). Незаконное задержание — ст. 286 УК РФ.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (target.getFaction().isCriminal() && level.random.nextFloat() < 0.3f) {
            target.say("Не возьмёшь, мусор!");
            target.addWanted(Article.KOAP_19_3);
            if (target.isCombatant()) target.setTarget(officer);
            return false;
        }
        Faction tf = target.getFaction();
        jailNpc(level, target, r.faction.shortRank(r.rank) + " " + officer.getName().getString());
        r.points += 15;
        r.civRep += 2;
        QuestManager.onArrest(officer, tf);
        Network.syncPlayer(officer);
        return true;
    }

    public static void jailNpc(ServerLevel level, CitizenEntity c, String officerName) {
        GorodData d = GorodData.get(level);
        boolean criminal = false;
        int sentence = 0;
        StringBuilder arts = new StringBuilder();
        for (Article a : c.wanted) {
            if (!a.administrative) {
                criminal = true;
                sentence += a.sentenceTicks;
            } else {
                sentence = Math.max(sentence, a.sentenceTicks);
            }
            if (arts.length() > 0) arts.append(", ");
            arts.append(a.code);
        }
        if (sentence <= 0) sentence = 1200;
        sentence = (int) (sentence * Config.SENTENCE_MULTIPLIER.get());
        List<BlockPos> cells = criminal ? d.sizoCells : d.ivsCells;
        BlockPos cell = cells.isEmpty() ? d.loc("sizo") : cells.get(level.random.nextInt(cells.size()));
        c.getNavigation().stop();
        c.setTarget(null);
        c.teleportTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
        c.jailUntil = level.getGameTime() + sentence;
        if (criminal) c.everConvicted = true;
        markArrested(d, c.getUUID());
        c.wanted.clear();
        c.protestUntil = -1;
        broadcastNear(level, cell, Component.literal(officerName + " задержал(а): " + c.fullName() + " (" + arts + ")").withStyle(ChatFormatting.GRAY));
        notifyFaction(level, Faction.UMVD, "[Дежурная часть] Задержан " + c.fullName() + " по " + arts + ". Помещён в " + (criminal ? "СИЗО-1" : "ИВС"), ChatFormatting.AQUA);
        if (criminal) notifyFaction(level, Faction.UFSIN, "[СИЗО-1] Принят под стражу: " + c.fullName() + " (" + arts + ")", ChatFormatting.AQUA);
        d.crimeIndex = Math.max(0, d.crimeIndex - 1);
        d.setDirty();
    }

    public static void releaseNpc(ServerLevel level, CitizenEntity c) {
        GorodData d = GorodData.get(level);
        BlockPos exit = d.loc("sizo_exit");
        c.jailUntil = -1;
        c.teleportTo(exit.getX() + 0.5, exit.getY(), exit.getZ() + 0.5);
        if (c.everConvicted && c.getFaction().isState()) {
            // Увольнение со службы в связи с осуждением (п. 7 ч. 3 ст. 82 ФЗ № 342-ФЗ)
            c.changeFaction(Faction.CIVILIAN, 0, d);
        }
        if (c.getFaction() == Faction.CIVILIAN && c.everConvicted) c.criminality = Math.min(1f, c.criminality + 0.15f);
    }

    public static void arrestPlayer(ServerLevel level, @Nullable LivingEntity officer, ServerPlayer p, boolean surrender) {
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        if (r.wanted.isEmpty()) return;
        long day = level.getDayTime() / 24000L;
        boolean criminal = false;
        int sentence = 0;
        int fines = 0;
        Article.Severity worst = Article.Severity.ADMIN;
        StringBuilder arts = new StringBuilder();
        for (Article a : r.wanted) {
            if (!a.administrative) {
                criminal = true;
                sentence += a.sentenceTicks;
                if (a.severity.ordinal() > worst.ordinal()) worst = a.severity;
            } else {
                fines += a.fine;
                sentence = Math.max(sentence, a.sentenceTicks);
            }
            if (arts.length() > 0) arts.append(", ");
            arts.append(a.code);
        }
        if (surrender) sentence = (int) (sentence * 0.6); // явка с повинной — смягчающее обстоятельство (п. «и» ч. 1 ст. 61 УК РФ)
        sentence = (int) (sentence * Config.SENTENCE_MULTIPLIER.get());

        p.sendSystemMessage(Component.literal("══ Вы задержаны ══").withStyle(ChatFormatting.RED));
        p.sendSystemMessage(Component.literal("Основание: " + arts + ". Права: ст. 46 УПК РФ — знать, в чём подозреваетесь; пользоваться помощью защитника; не свидетельствовать против себя (ст. 51 Конституции РФ).").withStyle(ChatFormatting.GRAY));
        boolean arrestable = false;
        for (Article a : r.wanted) if (a.administrative && a.sentenceTicks >= 1500) arrestable = true;
        if (!criminal && !arrestable) {
            // Протокол → постановление → штраф к оплате в 60 дней (ст. 32.2 КоАП); у нас — 3 игровых суток
            r.fineDebt += fines;
            if (r.fineDueDay < 0) r.fineDueDay = day + 3;
            r.wanted.clear();
            p.sendSystemMessage(Component.literal("Составлен протокол, вынесено постановление. Штраф " + fines + " ₽ — оплатить до " + (r.fineDueDay)
                    + "-х суток (G → «Город» → «Оплатить штрафы» / ПКМ по сотруднику банка или МФЦ). Неуплата — ст. 20.25 КоАП РФ.").withStyle(ChatFormatting.YELLOW));
            if (officer instanceof CitizenEntity oc) oc.say("Протокол составлен. Штраф оплатите в срок, иначе — двойной или арест.");
            Network.syncPlayer(p);
            return;
        }
        if (!criminal && fines > 0) {
            r.fineDebt += fines;
            if (r.fineDueDay < 0) r.fineDueDay = day + 3;
        }
        List<BlockPos> cells = criminal ? d.sizoCells : d.ivsCells;
        BlockPos cell = cells.isEmpty() ? d.loc("sizo") : cells.get(level.random.nextInt(cells.size()));
        r.jailUntil = level.getGameTime() + Math.max(600, sentence) + (criminal ? 700 : 0);
        r.jailType = criminal ? 2 : 1;
        r.clearQuest();
        if (criminal) {
            r.everConvicted = true;
            r.recordUntilDay = day + worst.recordDays;
            r.civRep = Math.max(0, r.civRep - 15);
            if (r.faction.isState()) {
                p.sendSystemMessage(Component.literal("Вы уволены со службы в связи с осуждением (п. 7 ч. 3 ст. 82 ФЗ № 342-ФЗ / аналогичные нормы).").withStyle(ChatFormatting.DARK_RED));
                r.faction = Faction.CIVILIAN;
                r.rank = 0;
                r.points = 0;
                r.uniform = false;
                p.refreshDisplayName();
                Network.broadcastRanks(level.getServer());
            } else if (r.faction.isCriminal()) {
                r.crimRep += 5; // «ходка» повышает авторитет
            }
        }
        markArrested(d, p.getUUID());
        r.wanted.clear();
        r.cuffed = false;
        if (criminal) {
            r.trialAt = level.getGameTime() + 600;
            r.trialArticles = arts.toString();
            p.sendSystemMessage(Component.literal("Через 30 секунд — судебное заседание. В последнем слове можете раскаяться (напишите в чат «раскаиваюсь») — это смягчит приговор (ч. 2 ст. 61 УК РФ).").withStyle(ChatFormatting.GOLD));
        }
        p.teleportTo(level, cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.sendSystemMessage(Component.literal((criminal ? "Мера пресечения — заключение под стражу. СИЗО-1. " : "Административный арест (ст. 3.9 КоАП РФ). ИВС. ")
                + "Срок: " + (r.jailUntil - level.getGameTime()) / 20 + " сек.").withStyle(ChatFormatting.RED));
        if (officer instanceof CitizenEntity oc) oc.say("Задержанный " + p.getName().getString() + " доставлен в " + (criminal ? "СИЗО" : "ИВС") + ".");
        if (officer instanceof ServerPlayer op) {
            PlayerRecord or = d.player(op.getUUID());
            or.points += 25;
            QuestManager.onArrest(op, r.faction);
            Network.syncPlayer(op);
        }
        notifyFaction(level, Faction.UMVD, "[Дежурная часть] Задержан " + p.getName().getString() + " (" + arts + ")", ChatFormatting.AQUA);
        d.setDirty();
        Network.syncPlayer(p);
    }

    private static void markArrested(GorodData d, UUID id) {
        for (CriminalCase cs : d.cases) {
            if (id.equals(cs.offender) && (cs.stage == CriminalCase.Stage.REPORTED || cs.stage == CriminalCase.Stage.INVESTIGATION
                    || cs.stage == CriminalCase.Stage.SUSPECT_WANTED)) {
                cs.stage = CriminalCase.Stage.ARRESTED;
                cs.identified = true;
                cs.progress = 0;
            }
        }
    }

    /** Каждые 20 тиков: удержание в камере и освобождение по истечении срока. */
    public static void tickJailedPlayer(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        if (r.jailUntil <= 0) return;
        if (level.getGameTime() >= r.jailUntil) {
            r.jailUntil = -1;
            BlockPos exit = r.jailType == 2 ? d.loc("sizo_exit") : d.loc("police");
            r.jailType = 0;
            p.teleportTo(level, exit.getX() + 0.5, exit.getY(), exit.getZ() + 0.5, p.getYRot(), p.getXRot());
            p.sendSystemMessage(Component.literal("Вы освобождены. Справка об освобождении выдана.").withStyle(ChatFormatting.GREEN));
            Network.syncPlayer(p);
            return;
        }
        List<BlockPos> cells = r.jailType == 2 ? d.sizoCells : d.ivsCells;
        if (cells.isEmpty()) return;
        BlockPos nearest = cells.get(0);
        double best = Double.MAX_VALUE;
        for (BlockPos c : cells) {
            double dd = p.distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            if (dd < best) {
                best = dd;
                nearest = c;
            }
        }
        if (best > 16 || p.level() != level.getServer().overworld()) {
            p.teleportTo(level.getServer().overworld(), nearest.getX() + 0.5, nearest.getY(), nearest.getZ() + 0.5, p.getYRot(), p.getXRot());
            p.sendSystemMessage(Component.literal("Побег из-под стражи — ст. 313 УК РФ. Вас вернули в камеру.").withStyle(ChatFormatting.RED));
        }
    }

    // ======================================================================== уголовные дела

    public static CriminalCase pickCaseFor(GorodData d, Faction investigator, RandomSource r) {
        List<CriminalCase> list = new ArrayList<>();
        for (CriminalCase cs : d.cases) {
            if (cs.article.investigator == investigator && (cs.stage == CriminalCase.Stage.REPORTED || cs.stage == CriminalCase.Stage.INVESTIGATION))
                list.add(cs);
        }
        return list.isEmpty() ? null : list.get(r.nextInt(list.size()));
    }

    public static void progressCase(ServerLevel level, GorodData d, CriminalCase cs, int amount) {
        switch (cs.stage) {
            case REPORTED -> {
                cs.stage = CriminalCase.Stage.INVESTIGATION;
                cs.progress = 0;
                notifyFaction(level, cs.article.investigator, "[Следствие] Возбуждено уголовное дело №" + cs.id + " по " + cs.article.full() + " (ст. 146 УПК РФ).", ChatFormatting.AQUA);
            }
            case INVESTIGATION -> {
                cs.progress += amount;
                if (cs.progress >= 100) {
                    cs.progress = 0;
                    if (cs.offender == null) {
                        close(level, d, cs, "приостановлено — лицо не установлено (п. 1 ч. 1 ст. 208 УПК РФ)");
                        return;
                    }
                    cs.identified = true;
                    cs.stage = CriminalCase.Stage.SUSPECT_WANTED;
                    applyWanted(level, d, cs);
                    notifyFaction(level, Faction.UMVD, "[Ориентировка] Установлен подозреваемый по делу №" + cs.id + ": " + cs.offenderName + " (" + cs.article.code + "). Объявлен в розыск.", ChatFormatting.GOLD);
                }
            }
            case ARRESTED -> {
                cs.progress += amount;
                if (cs.progress >= 100) {
                    cs.progress = 0;
                    cs.stage = CriminalCase.Stage.PROSECUTOR;
                    notifyFaction(level, Faction.PROK, "[Прокуратура] Поступило дело №" + cs.id + " с обвинительным заключением (ст. 220–221 УПК РФ). Требуется проверка.", ChatFormatting.AQUA);
                }
            }
            case PROSECUTOR -> {
                cs.progress += amount;
                if (cs.progress >= 100) {
                    cs.progress = 0;
                    cs.stage = CriminalCase.Stage.COURT;
                }
            }
            case COURT -> {
                cs.progress += amount;
                if (cs.progress >= 100) {
                    close(level, d, cs, "вынесен обвинительный приговор, вступил в законную силу");
                    d.addNews("Суд: по делу №" + cs.id + " (" + cs.article.code + ") вынесен приговор в отношении " + cs.offenderName + ".");
                }
            }
            default -> {
            }
        }
        d.setDirty();
    }

    private static void close(ServerLevel level, GorodData d, CriminalCase cs, String reason) {
        cs.stage = CriminalCase.Stage.CLOSED;
        cs.closeReason = reason;
        notifyFaction(level, cs.article.investigator, "[Дело №" + cs.id + "] Закрыто: " + reason, ChatFormatting.GRAY);
    }

    private static void applyWanted(ServerLevel level, GorodData d, CriminalCase cs) {
        if (cs.offenderIsPlayer) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(cs.offender);
            PlayerRecord r = d.player(cs.offender);
            if (!r.wanted.contains(cs.article) && !r.isJailed(level.getGameTime())) r.wanted.add(cs.article);
            if (p != null) {
                p.sendSystemMessage(Component.literal("⚠ Следствие установило вашу причастность к преступлению: " + cs.article.full() + ". Вы в розыске.").withStyle(ChatFormatting.RED));
                Network.syncPlayer(p);
            }
        } else {
            Entity e = level.getEntity(cs.offender);
            if (e instanceof CitizenEntity c && !c.isJailed()) c.addWanted(cs.article);
        }
    }

    /** Периодическая работа следствия, прокуратуры и суда. */
    public static void tickCases(ServerLevel level, GorodData d) {
        for (CriminalCase cs : d.cases) {
            if (cs.stage == CriminalCase.Stage.CLOSED) continue;
            Faction inv = cs.article.investigator;
            int workers = d.npcCount(inv) + onlineMembers(level, d, inv) * 2;
            if ((cs.article == Article.UK_210 || cs.article == Article.UK_282_1 || cs.article == Article.UK_210_1))
                workers += d.npcCount(Faction.UFSB) + onlineMembers(level, d, Faction.UFSB) * 2; // оперативное сопровождение ФСБ
            switch (cs.stage) {
                case REPORTED, INVESTIGATION, ARRESTED -> progressCase(level, d, cs, workers > 0 ? 4 + workers * 3 : 1);
                case PROSECUTOR -> {
                    int pr = d.npcCount(Faction.PROK) + onlineMembers(level, d, Faction.PROK) * 2;
                    progressCase(level, d, cs, pr > 0 ? 6 + pr * 6 : 2);
                }
                case COURT -> progressCase(level, d, cs, 25);
                case SUSPECT_WANTED -> {
                    if (!cs.offenderIsPlayer && cs.offender != null && !d.npcs.containsKey(cs.offender)) {
                        close(level, d, cs, "в связи со смертью подозреваемого (п. 4 ч. 1 ст. 24 УПК РФ)");
                    } else {
                        applyWanted(level, d, cs);
                    }
                }
                default -> {
                }
            }
        }
        // Архив: храним последние 40 закрытых дел
        int closed = 0;
        for (int i = d.cases.size() - 1; i >= 0; i--) {
            if (d.cases.get(i).stage == CriminalCase.Stage.CLOSED && ++closed > 40) d.cases.remove(i);
        }
        d.setDirty();
    }

    // ======================================================================== уведомления

    public static int onlineMembers(ServerLevel level, GorodData d, Faction f) {
        int n = 0;
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (d.player(p.getUUID()).faction == f) n++;
        }
        return n;
    }

    public static void notifyFaction(ServerLevel level, Faction f, String text, ChatFormatting color) {
        GorodData d = GorodData.get(level);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (d.player(p.getUUID()).faction == f) p.sendSystemMessage(Component.literal(text).withStyle(color));
        }
    }

    public static void broadcastNear(ServerLevel level, BlockPos pos, Component msg) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 48 * 48) p.sendSystemMessage(msg);
        }
    }

    public static String nameOf(Entity e) {
        if (e instanceof CitizenEntity c) return c.fullName();
        return e.getName().getString();
    }

    private CrimeSystem() {
    }
}
