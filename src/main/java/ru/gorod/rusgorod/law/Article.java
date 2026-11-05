package ru.gorod.rusgorod.law;

import ru.gorod.rusgorod.faction.Faction;

/**
 * Статьи УК РФ и КоАП РФ, используемые в игре.
 * Подследственность — по ст. 150–151 УПК РФ (упрощённо: дознание/следствие МВД или СК).
 * Категории — по ст. 15 УК РФ. Сроки погашения судимости — по ст. 86 УК РФ (сжаты до игровых суток).
 */
public enum Article {
    // ---------- КоАП РФ (административные) ----------
    KOAP_5_61("ст. 5.61 КоАП РФ", "Оскорбление", true, Faction.PROK, Severity.ADMIN, 0, 3000),
    KOAP_6_1_1("ст. 6.1.1 КоАП РФ", "Побои", true, Faction.UMVD, Severity.ADMIN, 1200, 5000),
    KOAP_19_3("ст. 19.3 КоАП РФ", "Неповиновение законному требованию сотрудника полиции", true, Faction.UMVD, Severity.ADMIN, 1800, 2000),
    KOAP_20_1("ст. 20.1 КоАП РФ", "Мелкое хулиганство", true, Faction.UMVD, Severity.ADMIN, 1200, 1000),
    KOAP_20_2("ст. 20.2 КоАП РФ", "Нарушение порядка проведения публичного мероприятия", true, Faction.UMVD, Severity.ADMIN, 1800, 10000),
    KOAP_20_3("ст. 20.3 КоАП РФ", "Пропаганда либо публичное демонстрирование нацистской символики", true, Faction.UMVD, Severity.ADMIN, 1800, 2000),
    KOAP_20_17("ст. 20.17 КоАП РФ", "Нарушение пропускного режима охраняемого объекта", true, Faction.UMVD, Severity.ADMIN, 1800, 3000),
    KOAP_20_20("ст. 20.20 КоАП РФ", "Потребление алкоголя в общественных местах", true, Faction.UMVD, Severity.ADMIN, 0, 500),
    KOAP_20_21("ст. 20.21 КоАП РФ", "Появление в общественных местах в состоянии опьянения", true, Faction.UMVD, Severity.ADMIN, 1200, 1000),
    KOAP_19_13("ст. 19.13 КоАП РФ", "Заведомо ложный вызов специализированных служб", true, Faction.UMVD, Severity.ADMIN, 0, 1500),
    KOAP_20_8("ст. 20.8 КоАП РФ", "Нарушение правил ношения и использования оружия", true, Faction.UMVD, Severity.ADMIN, 1200, 3000),
    KOAP_20_25("ст. 20.25 КоАП РФ", "Неуплата административного штрафа в срок", true, Faction.UMVD, Severity.ADMIN, 1800, 2000),

    // ---------- УК РФ ----------
    UK_105("ст. 105 УК РФ", "Убийство", false, Faction.SK, Severity.ESPECIALLY_GRAVE, 24000, 0),
    UK_111("ст. 111 УК РФ", "Умышленное причинение тяжкого вреда здоровью", false, Faction.UMVD, Severity.GRAVE, 12000, 0),
    UK_115("ст. 115 УК РФ", "Умышленное причинение лёгкого вреда здоровью", false, Faction.UMVD, Severity.MINOR, 2400, 0),
    UK_119("ст. 119 УК РФ", "Угроза убийством или причинением тяжкого вреда здоровью", false, Faction.UMVD, Severity.MINOR, 2400, 0),
    UK_158("ст. 158 УК РФ", "Кража", false, Faction.UMVD, Severity.MINOR, 3600, 0),
    UK_161("ст. 161 УК РФ", "Грабёж", false, Faction.UMVD, Severity.MEDIUM, 6000, 0),
    UK_162("ст. 162 УК РФ", "Разбой", false, Faction.UMVD, Severity.GRAVE, 9600, 0),
    UK_163("ст. 163 УК РФ", "Вымогательство", false, Faction.UMVD, Severity.MEDIUM, 7200, 0),
    UK_167("ст. 167 УК РФ", "Умышленное уничтожение или повреждение имущества", false, Faction.UMVD, Severity.MINOR, 2400, 0),
    UK_175("ст. 175 УК РФ", "Сбыт имущества, заведомо добытого преступным путём", false, Faction.UMVD, Severity.MINOR, 3000, 0),
    UK_210("ст. 210 УК РФ", "Организация преступного сообщества или участие в нём", false, Faction.SK, Severity.ESPECIALLY_GRAVE, 18000, 0),
    UK_210_1("ст. 210.1 УК РФ", "Занятие высшего положения в преступной иерархии", false, Faction.SK, Severity.ESPECIALLY_GRAVE, 18000, 0),
    UK_213("ст. 213 УК РФ", "Хулиганство", false, Faction.UMVD, Severity.MEDIUM, 4800, 0),
    UK_214("ст. 214 УК РФ", "Вандализм", false, Faction.UMVD, Severity.MINOR, 2400, 0),
    UK_222("ст. 222 УК РФ", "Незаконный оборот оружия", false, Faction.UMVD, Severity.MEDIUM, 7200, 0),
    UK_223("ст. 223 УК РФ", "Незаконное изготовление оружия (укорачивание ствола)", false, Faction.UMVD, Severity.MEDIUM, 7200, 0),
    UK_228_1("ст. 228.1 УК РФ", "Незаконный сбыт наркотических средств", false, Faction.UMVD, Severity.ESPECIALLY_GRAVE, 14000, 0),
    UK_282_1("ст. 282.1 УК РФ", "Организация экстремистского сообщества", false, Faction.SK, Severity.GRAVE, 12000, 0),
    UK_290("ст. 290 УК РФ", "Получение взятки", false, Faction.SK, Severity.GRAVE, 12000, 0),
    UK_291("ст. 291 УК РФ", "Дача взятки", false, Faction.SK, Severity.MEDIUM, 6000, 0),
    UK_309("ст. 309 УК РФ", "Подкуп или принуждение к даче показаний", false, Faction.SK, Severity.MINOR, 3000, 0),
    UK_313("ст. 313 УК РФ", "Побег из места лишения свободы или из-под стражи", false, Faction.UMVD, Severity.MEDIUM, 6000, 0),
    UK_317("ст. 317 УК РФ", "Посягательство на жизнь сотрудника правоохранительного органа", false, Faction.SK, Severity.ESPECIALLY_GRAVE, 30000, 0),
    UK_318("ст. 318 УК РФ", "Применение насилия в отношении представителя власти", false, Faction.SK, Severity.MEDIUM, 7200, 0),
    UK_319("ст. 319 УК РФ", "Оскорбление представителя власти", false, Faction.SK, Severity.MINOR, 2400, 0);

    public enum Severity {
        ADMIN("административное правонарушение", 0),
        MINOR("небольшой тяжести", 2),
        MEDIUM("средней тяжести", 4),
        GRAVE("тяжкое", 8),
        ESPECIALLY_GRAVE("особо тяжкое", 12);

        public final String title;
        /** Срок погашения судимости в игровых сутках (ст. 86 УК РФ, сжатый масштаб). */
        public final int recordDays;

        Severity(String title, int recordDays) {
            this.title = title;
            this.recordDays = recordDays;
        }
    }

    public final String code;
    public final String title;
    public final boolean administrative;
    public final Faction investigator;
    public final Severity severity;
    /** Базовый срок содержания (тики). Для КоАП — административный арест в ИВС отдела полиции. */
    public final int sentenceTicks;
    /** Штраф (руб.) — только для административных. */
    public final int fine;

    Article(String code, String title, boolean adm, Faction investigator, Severity severity, int sentenceTicks, int fine) {
        this.code = code;
        this.title = title;
        this.administrative = adm;
        this.investigator = investigator;
        this.severity = severity;
        this.sentenceTicks = sentenceTicks;
        this.fine = fine;
    }

    public String full() {
        return code + " — " + title;
    }

    public static Article byName(String n) {
        try {
            return valueOf(n);
        } catch (Exception e) {
            return KOAP_20_1;
        }
    }
}
