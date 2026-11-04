package ru.gorod.rusgorod.law;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

import java.util.UUID;

/**
 * Уголовное дело / дело об административном правонарушении.
 * Стадии соответствуют УПК РФ: сообщение о преступлении (ст. 140–145) → возбуждение и расследование
 * (ст. 146, гл. 21–32) → задержание (ст. 91) → обвинительное заключение и направление прокурору (ст. 220–221)
 * → суд (разд. IX) → исполнение приговора.
 */
public class CriminalCase {
    public enum Stage {
        REPORTED("Зарегистрировано сообщение о преступлении (КУСП)"),
        INVESTIGATION("Возбуждено дело, ведётся расследование"),
        SUSPECT_WANTED("Подозреваемый установлен, объявлен в розыск"),
        ARRESTED("Подозреваемый задержан (ст. 91 УПК), содержится в СИЗО"),
        PROSECUTOR("Обвинительное заключение направлено прокурору (ст. 220–221 УПК)"),
        COURT("Дело направлено в суд"),
        CLOSED("Дело закрыто");

        public final String title;

        Stage(String title) {
            this.title = title;
        }
    }

    public int id;
    public Article article;
    public UUID offender;
    public String offenderName = "неустановленное лицо";
    public boolean offenderIsPlayer;
    public boolean identified;
    public BlockPos pos = BlockPos.ZERO;
    public Stage stage = Stage.REPORTED;
    public int progress;
    public long created;
    public String closeReason = "";

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putString("article", article.name());
        if (offender != null) t.putUUID("offender", offender);
        t.putString("oname", offenderName);
        t.putBoolean("oplayer", offenderIsPlayer);
        t.putBoolean("ident", identified);
        t.put("pos", NbtUtils.writeBlockPos(pos));
        t.putString("stage", stage.name());
        t.putInt("progress", progress);
        t.putLong("created", created);
        t.putString("close", closeReason);
        return t;
    }

    public static CriminalCase load(CompoundTag t) {
        CriminalCase c = new CriminalCase();
        c.id = t.getInt("id");
        c.article = Article.byName(t.getString("article"));
        if (t.hasUUID("offender")) c.offender = t.getUUID("offender");
        c.offenderName = t.getString("oname");
        c.offenderIsPlayer = t.getBoolean("oplayer");
        c.identified = t.getBoolean("ident");
        c.pos = NbtUtils.readBlockPos(t.getCompound("pos"));
        try {
            c.stage = Stage.valueOf(t.getString("stage"));
        } catch (Exception e) {
            c.stage = Stage.CLOSED;
        }
        c.progress = t.getInt("progress");
        c.created = t.getLong("created");
        c.closeReason = t.getString("close");
        return c;
    }

    public String shortInfo() {
        return "Дело №" + id + " по " + article.code + " (" + article.title + "), фигурант: "
                + (identified ? offenderName : "не установлен") + " — " + stage.title;
    }
}
