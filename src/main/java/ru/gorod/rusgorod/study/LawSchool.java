package ru.gorod.rusgorod.study;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.entity.Profession;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.net.OpenDialogPacket;
import ru.gorod.rusgorod.registry.ModItems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Юридический институт. Порядок:
 * 1) поступить (G → «Юрфак»);
 * 2) по каждому предмету прослушать 2 лекции — днём стоять в аудитории LECTURE_SECONDS секунд;
 * 3) сдать экзамен (3 вопроса, нужно 2 верных ответа) — в институте, G → «Юрфак» → «Сдать экзамен»;
 * 4) после всех 7 экзаменов — диплом бакалавра юриспруденции.
 * Учиться можно в любой фракции («заочно»): так сержант полиции может стать офицером.
 */
public final class LawSchool {
    public static final int LECTURE_SECONDS = 30;
    public static final int LECTURES_PER_SUBJECT = 2;
    public static final int EXAM_QUESTIONS = 3;
    public static final int EXAM_PASS = 2;

    public record Subject(String name, String[] lectureLines, String[][] questions) {
    }

    public static final Subject[] SUBJECTS = {
            new Subject("Конституционное право", new String[]{
                    "Конституция РФ принята всенародным голосованием 12 декабря 1993 года и имеет высшую юридическую силу (ст. 15).",
                    "Носителем суверенитета и единственным источником власти является её многонациональный народ (ст. 3).",
                    "Человек, его права и свободы являются высшей ценностью (ст. 2).",
                    "Арест и заключение под стражу — только по судебному решению; до него задержание не более 48 часов (ст. 22).",
                    "Граждане вправе собираться мирно, без оружия, проводить собрания, митинги и демонстрации (ст. 31).",
                    "Никто не обязан свидетельствовать против себя, своего супруга и близких родственников (ст. 51)."},
                    new String[][]{
                            {"Какой акт имеет высшую юридическую силу в РФ?", "Конституция РФ", "Федеральный закон", "Указ Президента", "Постановление Правительства"},
                            {"Сколько можно задержать человека до судебного решения (ст. 22 Конституции)?", "48 часов", "24 часа", "72 часа", "10 суток"},
                            {"Какая статья Конституции позволяет не свидетельствовать против себя?", "Статья 51", "Статья 31", "Статья 15", "Статья 22"},
                            {"Кто является единственным источником власти в РФ?", "Многонациональный народ", "Президент", "Федеральное Собрание", "Правительство"},
                            {"Когда принята действующая Конституция РФ?", "12 декабря 1993 года", "12 июня 1990 года", "7 октября 1977 года", "1 июля 2020 года"}}),
            new Subject("Уголовное право", new String[]{
                    "Преступление — виновно совершённое общественно опасное деяние, запрещённое УК под угрозой наказания (ст. 14 УК).",
                    "Уголовная ответственность по общему правилу — с 16 лет, за ряд тяжких преступлений — с 14 лет (ст. 20 УК).",
                    "Преступления бывают небольшой, средней тяжести, тяжкие и особо тяжкие (ст. 15 УК).",
                    "Кража — тайное хищение (ст. 158), грабёж — открытое (ст. 161), разбой — нападение с опасным насилием (ст. 162).",
                    "Не является преступлением причинение вреда в состоянии необходимой обороны (ст. 37 УК).",
                    "Судимость погашается через сроки ст. 86 УК и аннулирует все её правовые последствия."},
                    new String[][]{
                            {"С какого возраста по общему правилу наступает уголовная ответственность?", "С 16 лет", "С 14 лет", "С 18 лет", "С 21 года"},
                            {"Как называется тайное хищение чужого имущества?", "Кража", "Грабёж", "Разбой", "Мошенничество"},
                            {"Открытое хищение чужого имущества — это…", "Грабёж", "Кража", "Вымогательство", "Растрата"},
                            {"Какая статья УК РФ регулирует необходимую оборону?", "Статья 37", "Статья 105", "Статья 14", "Статья 51"},
                            {"С какого возраста наступает ответственность за убийство?", "С 14 лет", "С 16 лет", "С 18 лет", "С 12 лет"}}),
            new Subject("Уголовный процесс", new String[]{
                    "Сообщение о преступлении проверяется до 3 суток, с продлением до 10 и 30 суток (ст. 144 УПК).",
                    "Уголовное дело возбуждается при наличии повода и основания (ст. 140, 146 УПК).",
                    "Подозреваемый может быть задержан не более чем на 48 часов (ст. 94 УПК).",
                    "Заключение под стражу как мера пресечения избирается только судом (ст. 108 УПК).",
                    "Подследственность определяет ст. 151 УПК: убийства и преступления против представителей власти расследует СК.",
                    "Обвинительное заключение утверждает прокурор и направляет дело в суд (ст. 221 УПК)."},
                    new String[][]{
                            {"Кто утверждает обвинительное заключение?", "Прокурор", "Следователь", "Судья", "Начальник полиции"},
                            {"Каков общий срок проверки сообщения о преступлении?", "3 суток", "24 часа", "10 суток", "30 суток"},
                            {"Кто избирает меру пресечения в виде заключения под стражу?", "Суд", "Следователь", "Прокурор", "Начальник СИЗО"},
                            {"Какой орган расследует убийства (ст. 151 УПК)?", "Следственный комитет", "Полиция (дознание)", "ФСБ", "Прокуратура"},
                            {"Как называется лицо, удостоверяющее ход следственного действия?", "Понятой", "Свидетель", "Эксперт", "Специалист"}}),
            new Subject("Административное право", new String[]{
                    "Административная ответственность наступает с 16 лет (ст. 2.3 КоАП).",
                    "Административный арест назначает судья на срок до 30 суток, за отдельные нарушения — до 60 (ст. 3.9 КоАП).",
                    "Штраф уплачивается в течение 60 дней со дня вступления постановления в силу (ст. 32.2 КоАП).",
                    "Неуплата штрафа в срок — двойной штраф, арест до 15 суток или обязательные работы (ст. 20.25 КоАП).",
                    "Мелкое хулиганство, в том числе нецензурная брань в общественных местах, — ст. 20.1 КоАП.",
                    "Уведомление о публичном мероприятии подаётся за 10–15 дней (ст. 7 ФЗ № 54-ФЗ); нарушение порядка — ст. 20.2 КоАП."},
                    new String[][]{
                            {"С какого возраста наступает административная ответственность?", "С 16 лет", "С 14 лет", "С 18 лет", "С 12 лет"},
                            {"Сколько дней даётся на уплату административного штрафа?", "60 дней", "10 дней", "30 дней", "1 год"},
                            {"Нецензурная брань в общественном месте — это статья…", "20.1 КоАП", "20.20 КоАП", "319 УК", "6.1.1 КоАП"},
                            {"Максимальный срок административного ареста по общему правилу?", "30 суток", "15 суток", "60 суток", "90 суток"},
                            {"За сколько дней подаётся уведомление о митинге?", "За 10–15 дней", "За 1 день", "За 3 дня", "За 30 дней"}}),
            new Subject("Правоохранительные органы", new String[]{
                    "Полиция действует на основании ФЗ «О полиции» № 3-ФЗ от 7 февраля 2011 года.",
                    "Сотрудник полиции обязан назвать должность, звание, фамилию и по требованию предъявить удостоверение (ст. 5).",
                    "Огнестрельное оружие полиция применяет только в случаях, перечисленных в ст. 23 ФЗ «О полиции».",
                    "Прокуратура — единая федеральная централизованная система надзора за исполнением законов.",
                    "Следственный комитет расследует тяжкие и особо тяжкие преступления и подчиняется Президенту РФ.",
                    "ФСБ ведёт контрразведку и борьбу с терроризмом; оперативно-розыскная деятельность — по ФЗ № 144-ФЗ."},
                    new String[][]{
                            {"Что обязан сделать полицейский, обращаясь к гражданину?", "Назвать должность, звание и фамилию", "Сразу проверить документы", "Предъявить оружие", "Ничего"},
                            {"Какой номер у ФЗ «О полиции»?", "3-ФЗ", "144-ФЗ", "54-ФЗ", "131-ФЗ"},
                            {"Какой орган осуществляет надзор за исполнением законов?", "Прокуратура", "Суд", "Полиция", "ФСБ"},
                            {"Какой закон регулирует оперативно-розыскную деятельность?", "ФЗ № 144-ФЗ", "ФЗ № 3-ФЗ", "ФЗ № 59-ФЗ", "ФЗ № 25-ФЗ"},
                            {"Какое звание следует за капитаном полиции?", "Майор полиции", "Старший лейтенант полиции", "Подполковник полиции", "Старшина полиции"}}),
            new Subject("Гражданское право", new String[]{
                    "Дееспособность возникает в полном объёме с 18 лет (ст. 21 ГК).",
                    "Работающий по трудовому договору 16-летний может быть эмансипирован (ст. 27 ГК).",
                    "Собственнику принадлежат права владения, пользования и распоряжения имуществом (ст. 209 ГК).",
                    "Общий срок исковой давности — три года (ст. 196 ГК).",
                    "По договору купли-продажи продавец передаёт вещь в собственность покупателю за плату (ст. 454 ГК).",
                    "Вред, причинённый личности или имуществу гражданина, возмещается в полном объёме (ст. 1064 ГК)."},
                    new String[][]{
                            {"С какого возраста гражданин полностью дееспособен?", "С 18 лет", "С 14 лет", "С 16 лет", "С 21 года"},
                            {"Общий срок исковой давности?", "3 года", "1 год", "5 лет", "10 лет"},
                            {"Какие правомочия входят в право собственности?", "Владение, пользование, распоряжение", "Аренда, залог, дарение", "Только владение", "Пользование и наследование"},
                            {"С какого возраста возможна эмансипация?", "С 16 лет", "С 14 лет", "С 15 лет", "С 17 лет"},
                            {"Договор передачи вещи в собственность за плату — это…", "Купля-продажа", "Аренда", "Дарение", "Ссуда"}}),
            new Subject("Криминалистика", new String[]{
                    "Криминалистика изучает закономерности совершения преступлений и методы их раскрытия и расследования.",
                    "Дактилоскопия — идентификация человека по папиллярным узорам пальцев рук.",
                    "Осмотр места происшествия проводится с фотосъёмкой и оформляется протоколом (ст. 176–177 УПК).",
                    "Судебная баллистика исследует огнестрельное оружие, боеприпасы и следы выстрела.",
                    "Субъективный портрет (фоторобот) составляется со слов очевидцев.",
                    "Трасология изучает следы рук, обуви, орудий взлома и транспортных средств."},
                    new String[][]{
                            {"Как называется идентификация по папиллярным узорам?", "Дактилоскопия", "Баллистика", "Трасология", "Одорология"},
                            {"Что исследует судебная баллистика?", "Огнестрельное оружие и следы выстрела", "Почерк", "Запаховые следы", "Документы"},
                            {"Какими статьями УПК регулируется осмотр места происшествия?", "176–177", "91–92", "144–145", "220–221"},
                            {"Изображение подозреваемого со слов очевидцев — это…", "Субъективный портрет (фоторобот)", "Дактилокарта", "Ориентировка", "Протокол"},
                            {"Какой раздел изучает следы обуви и орудий взлома?", "Трасология", "Баллистика", "Одорология", "Почерковедение"}})
    };

    // ================================================================== состояние

    /** Текущий предмет: первый несданный. -1 — всё сдано. */
    public static int currentSubject(PlayerRecord r) {
        for (int i = 0; i < SUBJECTS.length; i++) if (!r.examPassed(i)) return i;
        return -1;
    }

    public static boolean inAuditorium(ServerPlayer p, GorodData d) {
        BlockPos a = d.loc("auditorium");
        return p.distanceToSqr(a.getX() + 0.5, a.getY(), a.getZ() + 0.5) < 9 * 9 && Math.abs(p.getY() - a.getY()) < 4;
    }

    public static boolean inUniversity(ServerPlayer p, GorodData d) {
        BlockPos a = d.loc("university");
        return p.distanceToSqr(a.getX() + 0.5, a.getY(), a.getZ() + 0.5) < 16 * 16;
    }

    public static boolean lectureTime(ServerLevel level) {
        long t = level.getDayTime() % 24000L;
        return t >= 500 && t < 11500; // 06:30 – 17:30
    }

    // ================================================================== действия

    public static void enroll(ServerPlayer p) {
        PlayerRecord r = GorodData.get(p.serverLevel()).player(p.getUUID());
        if (r.legalEducation) {
            msg(p, "У вас уже есть диплом юриста.", ChatFormatting.YELLOW);
            return;
        }
        if (r.enrolled) {
            msg(p, "Вы уже студент.", ChatFormatting.YELLOW);
            return;
        }
        r.enrolled = true;
        msg(p, "🎓 Приказ о зачислении подписан: вы студент юридического института (бюджет, заочная форма — ст. 17 ФЗ № 273-ФЗ «Об образовании»).", ChatFormatting.GOLD);
        msg(p, "Что дальше: приходите днём в аудиторию 101 (" + coords(p) + ") и стойте там " + LECTURE_SECONDS
                + " сек — это одна лекция. По каждому предмету 2 лекции, потом экзамен. Подробно — G → «Юрфак».", ChatFormatting.YELLOW);
        Network.syncPlayer(p);
    }

    /** Каждые 20 тиков. */
    public static void tick(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        if (!r.enrolled || r.legalEducation) return;
        int s = currentSubject(r);
        if (s < 0 || r.lectures[s] >= LECTURES_PER_SUBJECT) return;
        if (!inAuditorium(p, d)) {
            if (r.lectureTicks > 0 && r.lectureTicks < LECTURE_SECONDS * 20) {
                r.lectureTicks = 0;
                p.displayClientMessage(Component.literal("Вы ушли с лекции — она не засчитана.").withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        if (!lectureTime(level)) {
            p.displayClientMessage(Component.literal("Пары с 06:30 до 17:30. Сейчас аудитория пустая.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (r.lectureSubject != s) {
            r.lectureSubject = s;
            r.lectureTicks = 0;
        }
        Subject subj = SUBJECTS[s];
        int line = r.lectureTicks / 200; // каждые 10 секунд — новый тезис
        if (r.lectureTicks % 200 == 0) {
            int idx = (r.lectures[s] * 3 + line) % subj.lectureLines().length;
            String lecturer = lecturerName(level, d);
            p.sendSystemMessage(Component.literal("[Лекция: " + subj.name() + "] ").withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.literal(lecturer + ": «" + subj.lectureLines()[idx] + "»").withStyle(ChatFormatting.WHITE)));
        }
        r.lectureTicks += 20;
        int left = Math.max(0, LECTURE_SECONDS - r.lectureTicks / 20);
        p.displayClientMessage(Component.literal("Лекция «" + subj.name() + "» (" + (r.lectures[s] + 1) + "/" + LECTURES_PER_SUBJECT + "): осталось " + left + " сек.")
                .withStyle(ChatFormatting.AQUA), true);
        if (r.lectureTicks >= LECTURE_SECONDS * 20) {
            r.lectureTicks = 0;
            r.lectures[s]++;
            if (r.faction == Faction.CIVILIAN) r.balance += 100;
            level.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
            if (r.lectures[s] >= LECTURES_PER_SUBJECT) {
                msg(p, "✔ Лекции по «" + subj.name() + "» прослушаны. Можно сдавать экзамен: G → «Юрфак» → «Сдать экзамен» (находясь в институте).", ChatFormatting.GREEN);
            } else {
                msg(p, "✔ Лекция засчитана (" + r.lectures[s] + "/" + LECTURES_PER_SUBJECT + ")" + (r.faction == Faction.CIVILIAN ? ", стипендия +100 ₽." : "."), ChatFormatting.GREEN);
            }
            Network.syncPlayer(p);
        }
    }

    public static void startExam(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        int s = currentSubject(r);
        if (!r.enrolled || s < 0) return;
        if (r.lectures[s] < LECTURES_PER_SUBJECT) {
            msg(p, "Сначала прослушайте лекции: " + r.lectures[s] + "/" + LECTURES_PER_SUBJECT + ".", ChatFormatting.RED);
            return;
        }
        if (!inUniversity(p, d)) {
            msg(p, "Экзамен принимают только в институте (" + coords(p) + ").", ChatFormatting.RED);
            return;
        }
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < SUBJECTS[s].questions().length; i++) idx.add(i);
        Collections.shuffle(idx, new java.util.Random(level.random.nextLong()));
        r.examSubject = s;
        r.examQuestions = new int[EXAM_QUESTIONS];
        for (int i = 0; i < EXAM_QUESTIONS; i++) r.examQuestions[i] = idx.get(i);
        r.examIndex = 0;
        r.examCorrect = 0;
        msg(p, "Экзамен «" + SUBJECTS[s].name() + "»: " + EXAM_QUESTIONS + " вопроса, для «зачёта» нужно " + EXAM_PASS + " верных ответа. Удачи!", ChatFormatting.GOLD);
        askQuestion(p, r);
    }

    private static void askQuestion(ServerPlayer p, PlayerRecord r) {
        int s = r.examSubject;
        String[] q = SUBJECTS[s].questions()[r.examQuestions[r.examIndex]];
        List<Integer> order = new ArrayList<>(List.of(1, 2, 3, 4));
        Collections.shuffle(order, new java.util.Random(p.getRandom().nextLong()));
        List<String[]> opts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int src = order.get(i);
            opts.add(new String[]{"exam:" + (src == 1 ? "1" : "0") + ":" + i, (char) ('А' + i) + ") " + q[src]});
        }
        String title = "Экзамен: " + SUBJECTS[s].name() + " — вопрос " + (r.examIndex + 1) + "/" + EXAM_QUESTIONS;
        Network.sendTo(p, new OpenDialogPacket(-1, title, q[0], opts));
    }

    public static void answer(ServerPlayer p, String option) {
        PlayerRecord r = GorodData.get(p.serverLevel()).player(p.getUUID());
        if (r.examSubject < 0 || r.examQuestions.length != EXAM_QUESTIONS || r.examIndex >= EXAM_QUESTIONS) {
            r.examSubject = -1;
            return;
        }
        String[] parts = option.split(":");
        boolean right = parts.length > 1 && parts[1].equals("1");
        String[] q = SUBJECTS[r.examSubject].questions()[r.examQuestions[r.examIndex]];
        if (right) {
            r.examCorrect++;
            msg(p, "Верно.", ChatFormatting.GREEN);
        } else {
            msg(p, "Неверно. Правильный ответ: " + q[1] + ".", ChatFormatting.RED);
        }
        r.examIndex++;
        if (r.examIndex < EXAM_QUESTIONS) {
            askQuestion(p, r);
            return;
        }
        int s = r.examSubject;
        r.examSubject = -1;
        if (r.examCorrect >= EXAM_PASS) {
            r.examsPassed |= (1 << s);
            r.civRep += 2;
            msg(p, "✔ «" + SUBJECTS[s].name() + "» — зачтено (" + r.examCorrect + "/" + EXAM_QUESTIONS + "). Запись в зачётной книжке сделана.", ChatFormatting.GREEN);
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.2f);
            if (currentSubject(r) < 0) graduate(p, r);
            else msg(p, "Следующий предмет: «" + SUBJECTS[currentSubject(r)].name() + "».", ChatFormatting.YELLOW);
        } else {
            r.lectures[s] = Math.max(0, r.lectures[s] - 1);
            msg(p, "✖ «Незачёт» (" + r.examCorrect + "/" + EXAM_QUESTIONS + "). Для пересдачи прослушайте ещё одну лекцию по предмету.", ChatFormatting.RED);
        }
        Network.syncPlayer(p);
    }

    private static void graduate(ServerPlayer p, PlayerRecord r) {
        r.legalEducation = true;
        r.enrolled = false;
        r.civRep += 10;
        ItemStack diploma = new ItemStack(ModItems.DIPLOMA.get());
        diploma.setHoverName(Component.literal("Диплом бакалавра юриспруденции — " + p.getName().getString()).withStyle(ChatFormatting.GOLD));
        CompoundTag display = diploma.getOrCreateTagElement("display");
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Направление 40.03.01 «Юриспруденция»").withStyle(ChatFormatting.GRAY))));
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Юридический институт г. Новоберёзовска").withStyle(ChatFormatting.GRAY))));
        display.put("Lore", lore);
        if (!p.getInventory().add(diploma)) p.drop(diploma, false);
        msg(p, "🎓 Государственная итоговая аттестация пройдена! Вам выдан диплом бакалавра юриспруденции.", ChatFormatting.GOLD);
        msg(p, "Теперь доступны: Следственный комитет, прокуратура, ФСБ, суд (после стажа), офицерские звания в полиции, УФСИН и армии.", ChatFormatting.AQUA);
        GorodData.get(p.serverLevel()).addNews("Выпуск: " + p.getName().getString() + " окончил(а) юридический институт.");
    }

    private static String lecturerName(ServerLevel level, GorodData d) {
        BlockPos a = d.loc("auditorium");
        for (CitizenEntity c : level.getEntitiesOfClass(CitizenEntity.class, new net.minecraft.world.phys.AABB(a).inflate(20),
                c -> c.profession == Profession.PROFESSOR)) {
            return "Проф. " + c.lastName + " " + c.initials();
        }
        return "Доцент кафедры";
    }

    private static String coords(ServerPlayer p) {
        BlockPos a = GorodData.get(p.serverLevel()).loc("auditorium");
        return "X " + a.getX() + ", Z " + a.getZ();
    }

    private static void msg(ServerPlayer p, String s, ChatFormatting c) {
        p.sendSystemMessage(Component.literal(s).withStyle(c));
    }

    private LawSchool() {
    }
}
