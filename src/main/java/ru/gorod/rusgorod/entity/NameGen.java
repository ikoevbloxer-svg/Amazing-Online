package ru.gorod.rusgorod.entity;

import net.minecraft.util.RandomSource;

/** Генератор русских Ф.И.О. и «погонял». */
public final class NameGen {
    static final String[] MALE = {"Александр", "Дмитрий", "Сергей", "Андрей", "Алексей", "Иван", "Михаил", "Николай", "Владимир",
            "Евгений", "Павел", "Артём", "Максим", "Олег", "Виктор", "Юрий", "Игорь", "Роман", "Константин", "Денис", "Григорий", "Степан"};
    static final String[] MALE_PATR = {"Александрович", "Дмитриевич", "Сергеевич", "Андреевич", "Алексеевич", "Иванович", "Михайлович",
            "Николаевич", "Владимирович", "Евгеньевич", "Павлович", "Артёмович", "Максимович", "Олегович", "Викторович", "Юрьевич",
            "Игоревич", "Романович", "Константинович", "Денисович", "Григорьевич", "Степанович"};
    static final String[] FEMALE = {"Анна", "Елена", "Ольга", "Наталья", "Татьяна", "Ирина", "Светлана", "Мария", "Екатерина",
            "Юлия", "Галина", "Людмила", "Валентина", "Дарья", "Ксения", "Надежда"};
    static final String[] SURNAME = {"Иванов", "Смирнов", "Кузнецов", "Попов", "Васильев", "Петров", "Соколов", "Михайлов",
            "Новиков", "Фёдоров", "Морозов", "Волков", "Алексеев", "Лебедев", "Семёнов", "Егоров", "Павлов", "Козлов", "Степанов",
            "Николаев", "Орлов", "Андреев", "Макаров", "Никитин", "Захаров", "Зайцев", "Соловьёв", "Борисов", "Яковлев", "Григорьев",
            "Романов", "Воробьёв", "Сергеев", "Кузьмин", "Фролов", "Александров", "Дмитриев", "Королёв", "Гусев", "Киселёв", "Ильин",
            "Белов", "Тарасов", "Белоусов", "Комаров", "Жуков", "Ковалёв", "Богданов", "Медведев", "Носков"};
    static final String[] SURNAME_SKI = {"Ковальский", "Савицкий", "Лозинский", "Березовский", "Островский", "Добровольский"};

    static final String[] KAVKAZ_FIRST = {"Руслан", "Тимур", "Арсен", "Заур", "Рустам", "Ашот", "Магомед", "Мурад", "Карен", "Гиви", "Ислам", "Аслан"};
    static final String[] KAVKAZ_LAST = {"Алиев", "Гасанов", "Магомедов", "Арутюнян", "Мамедов", "Абрамян", "Кадыров", "Гаджиев", "Бериашвили", "Оганесян"};

    static final String[] NICK = {"Седой", "Шрам", "Кирпич", "Бородач", "Хмурый", "Танкист", "Лысый", "Кнут", "Гвоздь", "Батя",
            "Малой", "Боцман", "Клещ", "Штырь", "Грек", "Мастер", "Сапёр", "Дизель", "Ржавый", "Филин", "Профессор", "Ворон"};

    public static String[] generate(RandomSource r, boolean female, boolean kavkaz) {
        if (kavkaz) {
            int i = r.nextInt(KAVKAZ_FIRST.length);
            return new String[]{KAVKAZ_FIRST[i], KAVKAZ_LAST[r.nextInt(KAVKAZ_LAST.length)], ""};
        }
        String patr = MALE_PATR[r.nextInt(MALE_PATR.length)];
        String last;
        if (r.nextInt(10) == 0) {
            last = SURNAME_SKI[r.nextInt(SURNAME_SKI.length)];
            if (female) last = last.substring(0, last.length() - 2) + "ая";
        } else {
            last = SURNAME[r.nextInt(SURNAME.length)];
            if (female) last = last + "а";
        }
        if (female) {
            patr = patr.substring(0, patr.length() - 3) + "вна";
            if (patr.endsWith("ьвна")) patr = patr.substring(0, patr.length() - 4) + "ьевна";
            return new String[]{FEMALE[r.nextInt(FEMALE.length)], last, patr};
        }
        return new String[]{MALE[r.nextInt(MALE.length)], last, patr};
    }

    public static String nickname(RandomSource r) {
        return NICK[r.nextInt(NICK.length)];
    }

    private NameGen() {
    }
}
