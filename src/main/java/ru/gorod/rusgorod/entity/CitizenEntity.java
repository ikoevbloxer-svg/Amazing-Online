package ru.gorod.rusgorod.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import ru.gorod.rusgorod.Config;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.entity.ai.CitizenBrainGoal;
import ru.gorod.rusgorod.entity.ai.CitizenCombatGoal;
import ru.gorod.rusgorod.entity.ai.Encounters;
import ru.gorod.rusgorod.entity.ai.RankReactions;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.law.Article;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.net.DialogService;
import ru.gorod.rusgorod.registry.ModItems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Житель города / сотрудник / член ОПГ. Поведение определяется фракцией, званием, должностью и профессией. */
public class CitizenEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> FACTION = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RANK = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PROF = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);

    public static final int ANIM_NONE = 0, ANIM_SALUTE = 1, ANIM_HANDS_UP = 2, ANIM_PROTEST = 3, ANIM_TALK = 4, ANIM_CUFFED = 5, ANIM_POINT = 6;

    /** FOLLOW — за командиром, HOLD — охранять точку, ASSAULT — выехать «на точку» (война за территорию). */
    public enum Order {NONE, FOLLOW, HOLD, ASSAULT}

    public String firstName = "Иван";
    public String lastName = "Иванов";
    public String patronymic = "Иванович";
    public String nickname = "";
    public boolean female;
    public int rank;
    public Profession profession = Profession.UNEMPLOYED;
    public BlockPos home = BlockPos.ZERO;
    public BlockPos work = BlockPos.ZERO;
    /** Склонность к правонарушениям 0..1. */
    public float criminality;
    /** Настроение 0..100: влияет на митинги, пьянство, вежливость. */
    public int mood = 60;
    public boolean nightShift;
    public boolean everConvicted;
    public boolean informant;
    public boolean drunk;
    public final List<Article> wanted = new ArrayList<>();
    public long jailUntil = -1;
    public long protestUntil = -1;

    // ---- приказы
    public Order order = Order.NONE;
    public UUID commander;
    public BlockPos holdPos = BlockPos.ZERO;
    public long orderUntil;

    // ---- служебное
    public int encounterCooldown = 200;
    /** До какого момента NPC стоит и разговаривает (сценка с другим NPC). */
    public long talkUntil;
    /** Награда за задержание (городское событие): ослабленный ударами игрока — сдаётся. */
    public int bounty;
    /** Отношение к конкретным игрокам: -100 (враг) … +100 (близкий друг). */
    public final Map<UUID, Integer> relations = new HashMap<>();
    /** Свидетель преступления игрока: пока не null — собирается звонить в полицию. */
    public UUID witnessOf;
    public Article witnessArticle;
    /** Просьба проводить до дома: куда идём. */
    public BlockPos escortTo;
    public int approachCooldown = 600;
    public final Map<UUID, Long> greeted = new HashMap<>();
    private int speechCooldown;
    private int animTicks;
    private boolean initialized;

    public CitizenEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanFloat(true);
        }
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(FACTION, 0);
        this.entityData.define(VARIANT, 0);
        this.entityData.define(RANK, 0);
        this.entityData.define(PROF, Profession.UNEMPLOYED.ordinal());
        this.entityData.define(ANIM, ANIM_NONE);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new CitizenCombatGoal(this));
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(3, new CitizenBrainGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0f));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        // Сотрудники полиции/ДПС/ФСБ — задержание лиц, находящихся в розыске (ст. 13–14 ФЗ «О полиции»)
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                e -> this.getFaction().canArrest() && !this.isJailed() && CrimeSystem.isWanted(e)));
        // Конфликты между группировками
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, CitizenEntity.class, 400, true, false,
                e -> e instanceof CitizenEntity c && !this.isJailed() && !c.isJailed() && (this.order == Order.NONE || this.order == Order.ASSAULT)
                        && this.getFaction().isHostileTo(c.getFaction())));
    }

    // ---------------------------------------------------------------- инициализация

    public void setup(Faction faction, int rank, RandomSource random, GorodData data) {
        this.setFaction(faction);
        this.rank = Math.min(rank, faction.topRank());
        this.female = faction == Faction.CIVILIAN ? random.nextInt(100) < 45
                : (faction == Faction.GOV || faction == Faction.PROK || faction == Faction.SK || faction == Faction.COURT) && random.nextInt(100) < 40;
        String[] n = NameGen.generate(random, female, faction == Faction.KAVKAZ_MAFIA);
        firstName = n[0];
        lastName = n[1];
        patronymic = n[2];
        if (faction.isCriminal()) nickname = NameGen.nickname(random);
        int variant = faction == Faction.CIVILIAN ? (female ? 4 + random.nextInt(4) : random.nextInt(4)) : random.nextInt(4);
        this.entityData.set(VARIANT, variant);
        this.nightShift = random.nextBoolean();
        this.mood = 40 + random.nextInt(50);
        this.criminality = switch (faction.type) {
            case CRIMINAL -> 0.7f + random.nextFloat() * 0.3f;
            case STATE -> random.nextFloat() * 0.12f;
            default -> random.nextFloat() * random.nextFloat();
        };
        if (faction == Faction.CIVILIAN) {
            Profession[] ps = Profession.values();
            Profession p = ps[random.nextInt(ps.length)];
            if (p == Profession.PROFESSOR || p == Profession.PRIEST || p == Profession.DOCTOR) p = Profession.UNEMPLOYED;
            setProfession(p, data);
        } else {
            work = data.loc(faction.hqKey);
        }
        if (faction == Faction.ARMY && !data.barracks.isEmpty()) {
            home = data.barracks.get(random.nextInt(data.barracks.size()));
        } else {
            home = data.randomHome(random);
        }
        applyStats();
        equip();
        refreshName();
        syncData();
        initialized = true;
    }

    public void setProfession(Profession p, GorodData data) {
        this.profession = p;
        this.work = data.loc(p.workKey);
        this.entityData.set(PROF, p.ordinal());
        if (p == Profession.PRIEST || p == Profession.PROFESSOR || p == Profession.DOCTOR) this.criminality = 0.02f;
    }

    /** Смена фракции (вербовка, поступление на службу, увольнение). */
    public void changeFaction(Faction f, int newRank, GorodData data) {
        setFaction(f);
        rank = Math.min(newRank, f.topRank());
        if (f.isCriminal() && nickname.isEmpty()) nickname = NameGen.nickname(getRandom());
        if (f == Faction.CIVILIAN) {
            setProfession(Profession.UNEMPLOYED, data);
        } else {
            work = data.loc(f.hqKey);
            entityData.set(PROF, Profession.UNEMPLOYED.ordinal());
            profession = Profession.UNEMPLOYED;
        }
        order = Order.NONE;
        commander = null;
        applyStats();
        equip();
        refreshName();
        syncData();
        data.npcs.put(getUUID(), f);
        data.setDirty();
    }

    private void applyStats() {
        Faction f = getFaction();
        double hp = switch (f) {
            case ARMY, UFSB -> 30;
            case UMVD, UGIBDD, UFSIN, RUS_MAFIA, KAVKAZ_MAFIA, BIKERS, SKINHEADS -> 26;
            default -> 20;
        };
        double dmg = f.carriesWeapon() ? 4.0 : 1.5;
        AttributeInstance a = getAttribute(Attributes.MAX_HEALTH);
        if (a != null) a.setBaseValue(hp);
        AttributeInstance d = getAttribute(Attributes.ATTACK_DAMAGE);
        if (d != null) d.setBaseValue(dmg);
        setHealth((float) hp);
    }

    public void equip() {
        for (EquipmentSlot s : EquipmentSlot.values()) {
            setItemSlot(s, ItemStack.EMPTY);
            setDropChance(s, 0f);
        }
        Faction f = getFaction();
        switch (f) {
            case UMVD -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(rank >= 7 ? ModItems.PM.get() : ModItems.BATON.get()));
            case UGIBDD -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.BATON.get()));
            case UFSIN -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.BATON.get()));
            case UFSB -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.PM.get()));
            case ARMY -> {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(rank >= 8 ? ModItems.PM.get() : ModItems.AK74.get()));
                if (rank < 8) setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            }
            case RUS_MAFIA, KAVKAZ_MAFIA -> {
                if (rank >= 1) setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.PM.get()));
            }
            case BIKERS -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            case SKINHEADS -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.BATON.get()));
            default -> {
            }
        }
    }

    public void refreshName() {
        setCustomName(Component.literal(displayTitle()));
        setCustomNameVisible(false);
    }

    public void syncData() {
        if (entityData.get(RANK) != rank) entityData.set(RANK, rank);
        if (entityData.get(PROF) != profession.ordinal()) entityData.set(PROF, profession.ordinal());
    }

    public String initials() {
        String s = firstName.isEmpty() ? "" : firstName.charAt(0) + ".";
        if (!patronymic.isEmpty()) s += patronymic.charAt(0) + ".";
        return s;
    }

    public String displayTitle() {
        Faction f = getFaction();
        return switch (f.type) {
            case STATE -> f.shortRank(rank) + " " + lastName + " " + initials();
            case CRIMINAL -> f.rankName(rank) + " «" + nickname + "»";
            default -> firstName + " " + lastName;
        };
    }

    public String fullName() {
        return lastName + " " + firstName + (patronymic.isEmpty() ? "" : " " + patronymic);
    }

    /** Имя-отчество для вежливого обращения. */
    public String politeName() {
        return patronymic.isEmpty() ? firstName : firstName + " " + patronymic;
    }

    /** Представление по ст. 5 ФЗ «О полиции»: должность, звание, фамилия. */
    public String introduce() {
        Faction f = getFaction();
        return switch (f.type) {
            case STATE -> f.positionName(rank) + ", " + f.rankName(rank).toLowerCase() + " " + lastName + ".";
            case CRIMINAL -> "«" + nickname + "». Остальное тебе знать не надо.";
            default -> firstName + " " + lastName + ", " + profession.title + ".";
        };
    }

    // ---------------------------------------------------------------- состояние

    public Faction getFaction() {
        return Faction.byOrdinal(this.entityData.get(FACTION));
    }

    public void setFaction(Faction f) {
        this.entityData.set(FACTION, f.ordinal());
    }

    public int getVariant() {
        return this.entityData.get(VARIANT);
    }

    /** Звание, синхронизированное с клиентом (для погон). */
    public int getSyncedRank() {
        return this.entityData.get(RANK);
    }

    public Profession getSyncedProfession() {
        return Profession.byOrdinal(this.entityData.get(PROF));
    }

    public int getAnim() {
        return this.entityData.get(ANIM);
    }

    public void setAnim(int anim, int ticks) {
        this.entityData.set(ANIM, anim);
        this.animTicks = ticks;
    }

    public String skinKey() {
        Faction f = getFaction();
        if (f == Faction.CIVILIAN) {
            Profession p = getSyncedProfession();
            if (p.skin != null) return p.skin;
        }
        return f.texture(getVariant());
    }

    public boolean isWanted() {
        return !wanted.isEmpty();
    }

    public boolean isJailed() {
        return jailUntil > 0 && level().getGameTime() < jailUntil;
    }

    public boolean isProtesting() {
        return protestUntil > 0 && level().getGameTime() < protestUntil;
    }

    public boolean isOnDuty() {
        long t = level().getDayTime() % 24000L;
        boolean dayHalf = t < 12500L;
        return nightShift != dayHalf;
    }

    public boolean isCombatant() {
        Faction f = getFaction();
        return f != Faction.CIVILIAN && f != Faction.GOV && f != Faction.PROK && f != Faction.COURT;
    }

    public boolean isTalking(Level lvl) {
        return talkUntil > lvl.getGameTime();
    }

    public boolean isBusy() {
        return getTarget() != null || isJailed() || order != Order.NONE;
    }

    public void addWanted(Article a) {
        if (!wanted.contains(a)) wanted.add(a);
    }

    // ---------------------------------------------------------------- речь

    public void say(String text) {
        say(text, false);
    }

    /** force — игнорировать паузу между репликами (для диалогов). */
    public void say(String text, boolean force) {
        if (level().isClientSide || !Config.NPC_SPEECH.get() || !isAlive()) return;
        if (!force && speechCooldown > 0) return;
        speechCooldown = 60;
        if (getAnim() == ANIM_NONE) setAnim(ANIM_TALK, 30);
        ChatFormatting color = switch (getFaction().type) {
            case STATE -> ChatFormatting.AQUA;
            case CRIMINAL -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
        MutableComponent msg = Component.literal("[" + displayTitle() + "] ").withStyle(color)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(14))) {
            p.sendSystemMessage(msg);
        }
    }

    // ---------------------------------------------------------------- логика

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (speechCooldown > 0) speechCooldown--;
        if (encounterCooldown > 0) encounterCooldown--;
        if (animTicks > 0 && --animTicks == 0) entityData.set(ANIM, ANIM_NONE);
        if (!(level() instanceof ServerLevel sl)) return;
        if (!initialized && tickCount > 5) {
            // NPC из яйца призыва или из старого сохранения без данных
            setup(getFaction(), rank, getRandom(), GorodData.get(sl));
            GorodData.get(sl).npcs.put(getUUID(), getFaction());
        }
        if (tickCount % 20 == 0) {
            syncData();
            if (jailUntil > 0) {
                if (sl.getGameTime() >= jailUntil) {
                    CrimeSystem.releaseNpc(sl, this);
                    setAnim(ANIM_NONE, 0);
                } else {
                    getNavigation().stop();
                    setTarget(null);
                    if (getAnim() != ANIM_CUFFED) setAnim(ANIM_CUFFED, 0);
                }
            } else if (isProtesting() && getRandom().nextInt(3) == 0) {
                setAnim(ANIM_PROTEST, 30);
            }
            if (order != Order.NONE && sl.getGameTime() > orderUntil) {
                order = Order.NONE;
                commander = null;
                escortTo = null;
            }
        }
        if (tickCount % 10 == 0 && !isJailed()) RankReactions.tickNpc(sl, this);
        if (approachCooldown > 0) approachCooldown--;
        if (tickCount % 100 == 33 && approachCooldown <= 0 && !isBusy() && !isTalking(sl)) ru.gorod.rusgorod.social.NpcApproach.tick(sl, this);
        if (tickCount % 40 == 7 && encounterCooldown <= 0 && !isBusy() && !isTalking(sl)) Encounters.tryStart(sl, this);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ModItems.CUFFS.get())) return InteractionResult.PASS; // обрабатывает предмет
        if (!level().isClientSide && player instanceof ServerPlayer sp) {
            getNavigation().stop();
            getLookControl().setLookAt(player);
            DialogService.open(sp, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean res = super.hurt(source, amount);
        if (res && !level().isClientSide && level() instanceof ServerLevel sl) {
            Entity attacker = source.getEntity();
            if (bounty > 0 && attacker instanceof ServerPlayer sp && isAlive() && getHealth() < getMaxHealth() * 0.5f) {
                // Задержание гражданином лица, совершившего преступление (ст. 38 УК РФ)
                say("Всё, всё, сдаюсь! Не бейте!", true);
                setAnim(ANIM_HANDS_UP, 60);
                GorodData d = GorodData.get(sl);
                ru.gorod.rusgorod.data.PlayerRecord pr = d.player(sp.getUUID());
                pr.balance += bounty;
                pr.civRep += 5;
                sp.sendSystemMessage(Component.literal("Вы задержали преступника и передали полиции. Благодарность от потерпевшего: " + bounty + " ₽, +5 репутации.")
                        .withStyle(ChatFormatting.GREEN));
                bounty = 0;
                CrimeSystem.jailNpc(sl, this, sp.getName().getString());
                ru.gorod.rusgorod.net.Network.syncPlayer(sp);
                return res;
            }
            if (attacker instanceof Player pl) changeRelation(pl.getUUID(), -25);
            if (attacker instanceof LivingEntity le && le != this) {
                CrimeSystem.onCitizenHurt(sl, this, le);
                mood = Math.max(0, mood - 10);
                // Свои вступаются (у ОПГ — «за своих», у силовиков — пресечение преступления)
                for (CitizenEntity ally : sl.getEntitiesOfClass(CitizenEntity.class, getBoundingBox().inflate(16),
                        c -> c != this && c.isCombatant() && c.getFaction() == getFaction() && c.getTarget() == null && !c.isJailed())) {
                    if (!(le instanceof CitizenEntity lc && lc.getFaction() == ally.getFaction())) ally.setTarget(le);
                }
                if (getTarget() == null && !(le instanceof Player p && isCommander(p))) setTarget(le);
            }
        }
        return res;
    }

    public int relation(UUID player) {
        return relations.getOrDefault(player, 0);
    }

    public void changeRelation(UUID player, int delta) {
        int v = Math.max(-100, Math.min(100, relation(player) + delta));
        relations.put(player, v);
        if (relations.size() > 40) {
            UUID weakest = null;
            for (Map.Entry<UUID, Integer> e : relations.entrySet())
                if (weakest == null || Math.abs(e.getValue()) < Math.abs(relations.get(weakest))) weakest = e.getKey();
            if (weakest != null && !weakest.equals(player)) relations.remove(weakest);
        }
    }

    public boolean isCommander(Player p) {
        return commander != null && commander.equals(p.getUUID());
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            GorodData d = GorodData.get(sl);
            d.npcs.remove(getUUID());
            d.setDirty();
            CrimeSystem.onCitizenKilled(sl, this, source.getEntity());
        }
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    // ---------------------------------------------------------------- NBT

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putString("Faction", getFaction().id);
        t.putInt("Variant", getVariant());
        t.putString("First", firstName);
        t.putString("Last", lastName);
        t.putString("Patr", patronymic);
        t.putString("Nick", nickname);
        t.putBoolean("Female", female);
        t.putInt("Rank", rank);
        t.putString("Profession", profession.name());
        t.put("Home", NbtUtils.writeBlockPos(home));
        t.put("Work", NbtUtils.writeBlockPos(work));
        t.putFloat("Criminality", criminality);
        t.putInt("Mood", mood);
        t.putBoolean("NightShift", nightShift);
        t.putBoolean("Convicted", everConvicted);
        t.putBoolean("Informant", informant);
        t.putBoolean("Drunk", drunk);
        ListTag w = new ListTag();
        for (Article a : wanted) w.add(StringTag.valueOf(a.name()));
        t.put("Wanted", w);
        t.putLong("JailUntil", jailUntil);
        t.putLong("ProtestUntil", protestUntil);
        t.putBoolean("Init", initialized);
        t.putInt("Bounty", bounty);
        ListTag rel = new ListTag();
        for (Map.Entry<UUID, Integer> e : relations.entrySet()) {
            CompoundTag rt = new CompoundTag();
            rt.putUUID("P", e.getKey());
            rt.putInt("V", e.getValue());
            rel.add(rt);
        }
        t.put("Relations", rel);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        setFaction(Faction.byId(t.getString("Faction")));
        this.entityData.set(VARIANT, t.getInt("Variant"));
        firstName = t.getString("First");
        lastName = t.getString("Last");
        patronymic = t.getString("Patr");
        nickname = t.getString("Nick");
        female = t.getBoolean("Female");
        rank = Math.min(t.getInt("Rank"), getFaction().topRank());
        profession = Profession.byName(t.getString("Profession"));
        home = NbtUtils.readBlockPos(t.getCompound("Home"));
        work = NbtUtils.readBlockPos(t.getCompound("Work"));
        criminality = t.getFloat("Criminality");
        mood = t.contains("Mood") ? t.getInt("Mood") : 60;
        nightShift = t.getBoolean("NightShift");
        everConvicted = t.getBoolean("Convicted");
        informant = t.getBoolean("Informant");
        drunk = t.getBoolean("Drunk");
        wanted.clear();
        ListTag w = t.getList("Wanted", Tag.TAG_STRING);
        for (int i = 0; i < w.size(); i++) wanted.add(Article.byName(w.getString(i)));
        jailUntil = t.getLong("JailUntil");
        protestUntil = t.getLong("ProtestUntil");
        initialized = t.getBoolean("Init");
        bounty = t.getInt("Bounty");
        relations.clear();
        ListTag rel = t.getList("Relations", Tag.TAG_COMPOUND);
        for (int i = 0; i < rel.size(); i++) {
            CompoundTag rt = rel.getCompound(i);
            if (rt.hasUUID("P")) relations.put(rt.getUUID("P"), rt.getInt("V"));
        }
        syncData();
    }
}
