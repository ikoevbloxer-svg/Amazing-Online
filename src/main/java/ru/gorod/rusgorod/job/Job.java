package ru.gorod.rusgorod.job;

/**
 * Работы для гражданских. Трудоустройство — по трудовому договору (ст. 56, 67 ТК РФ),
 * оплата не ниже МРОТ (ст. 133 ТК РФ). С зарплаты удерживается НДФЛ 13% (ст. 224 НК РФ).
 * Учёба на юрфаке — отдельно (G → «Юрфак»), её можно совмещать с работой.
 */
public enum Job {
    DVORNIK("Дворник (МУП «Благоустройство»)", "Убирайте мусор на улицах: ломайте кучки мусора на тротуарах.",
            "Сдельно: 120 ₽ за кучку (ст. 150 ТК РФ).", "warehouse", 0, false),
    GRUZCHIK("Грузчик на складе", "Возьмите груз на складе (кнопка «Взять груз» у склада) и доставьте в «Продукты» или на рынок.",
            "Сдельно: 350 ₽ за доставку.", "warehouse", 0, false),
    POCHTALION("Почтальон", "Возьмите письма на почте и разнесите по адресам — подойдите к дому с письмом в инвентаре.",
            "Сдельно: 150 ₽ за письмо.", "post", 0, false),
    PRODAVETS("Продавец", "Стойте у прилавка магазина «Продукты» или на рынке в рабочее время (06:00–17:00).",
            "Почасово: 220 ₽ за каждую минуту смены.", "shop", 220, false),
    ZAVOD("Рабочий машзавода", "Стойте у станков в цехе завода в рабочее время. Тяжёлый, но хорошо оплачиваемый труд.",
            "Почасово: 300 ₽ за минуту смены.", "factory", 300, false),
    OKHRANNIK("Охранник ЧОП в банке", "Несите службу в зале банка в рабочее время. Нужна чистая биография.",
            "Почасово: 260 ₽ за минуту. Закон РФ № 2487-1 — удостоверение охранника не выдаётся лицам с непогашенной судимостью.", "bank", 260, true),
    SANITAR("Санитар городской больницы", "Помогайте в приёмном покое больницы в рабочее время.",
            "Почасово: 240 ₽ за минуту.", "hospital", 240, false);

    public final String title;
    public final String description;
    public final String pay;
    public final String placeKey;
    /** Почасовая (поминутная) ставка; 0 — сдельная оплата. */
    public final int shiftPay;
    public final boolean needsCleanRecord;

    Job(String title, String description, String pay, String placeKey, int shiftPay, boolean needsCleanRecord) {
        this.title = title;
        this.description = description;
        this.pay = pay;
        this.placeKey = placeKey;
        this.shiftPay = shiftPay;
        this.needsCleanRecord = needsCleanRecord;
    }

    public static Job byName(String n) {
        if (n == null || n.isEmpty()) return null;
        try {
            return valueOf(n);
        } catch (Exception e) {
            return null;
        }
    }
}
