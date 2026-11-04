package ru.gorod.rusgorod.job;

/**
 * Работы для гражданских. Трудоустройство — по трудовому договору (ст. 56, 67 ТК РФ),
 * оплата не ниже МРОТ (ст. 133 ТК РФ). С зарплаты удерживается НДФЛ 13% (ст. 224 НК РФ).
 */
public enum Job {
    DVORNIK("Дворник (МУП «Благоустройство»)", "Убирайте мусор на улицах города: ломайте блоки мусора на тротуарах.",
            "Сдельная оплата: 120 ₽ за единицу мусора (ст. 150 ТК РФ).", "warehouse"),
    GRUZCHIK("Грузчик на складе", "Берите груз на складе и доставляйте в магазин «Продукты» или на рынок.",
            "Сдельно: 350 ₽ за доставку.", "warehouse"),
    POCHTALION("Почтальон", "Берите письма на почте и разносите по адресам (подойдите к дому с письмом в инвентаре).",
            "Сдельно: 150 ₽ за письмо.", "post"),
    PRODAVETS("Продавец", "Находитесь у прилавка магазина или рынка в рабочее время (06:00–17:00).",
            "Почасовая оплата: 220 ₽ за каждую игровую «смену-минуту» (ст. 104 ТК РФ — суммированный учёт).", "shop"),
    STUDENT("Студент юридического института", "Посещайте лекции в институте (днём). 6 лекций — диплом о высшем юридическом образовании.",
            "Обучение на бюджетной основе (ст. 43 Конституции РФ, ФЗ № 273-ФЗ «Об образовании»). Стипендия 100 ₽ за лекцию.", "university");

    public final String title;
    public final String description;
    public final String pay;
    public final String placeKey;

    Job(String title, String description, String pay, String placeKey) {
        this.title = title;
        this.description = description;
        this.pay = pay;
        this.placeKey = placeKey;
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
