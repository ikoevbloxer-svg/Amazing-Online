package ru.gorod.rusgorod.world;

import java.util.LinkedHashMap;
import java.util.Map;

/** Справочник мест города для карты, такси, навигатора и подсказок. Общий для сервера и клиента. */
public final class Places {
    public enum Cat {
        STATE("Госорганы", 0xFF4A7BE0), SERVICE("Услуги", 0xFF3DBF6E), FOOD("Еда и магазины", 0xFFF0A030), LEISURE("Отдых", 0xFF9ED84A),
        EDU("Учёба", 0xFFB07CF0), CRIME("Криминал", 0xFFE04848), ARMY("Армия", 0xFF8A9A50), TRANSPORT("Транспорт и работа", 0xFF9AA4B0);
        public final String title;
        public final int color;

        Cat(String t, int c) {
            title = t;
            color = c;
        }
    }

    public record Place(String key, String name, String icon, Cat cat) {
    }

    public static final Map<String, Place> ALL = new LinkedHashMap<>();

    private static void p(String key, String name, String icon, Cat cat) {
        ALL.put(key, new Place(key, name, icon, cat));
    }

    static {
        p("admin", "Администрация города", "А", Cat.STATE);
        p("square", "Площадь Ленина", "★", Cat.STATE);
        p("police", "Отдел полиции УМВД", "П", Cat.STATE);
        p("gibdd", "Госавтоинспекция", "Г", Cat.STATE);
        p("gibdd_post", "Пост ДПС", "Д", Cat.STATE);
        p("sk", "Следственный комитет", "СК", Cat.STATE);
        p("prok", "Прокуратура", "Пр", Cat.STATE);
        p("fsb", "Управление ФСБ", "Ф", Cat.STATE);
        p("court", "Городской суд", "С", Cat.STATE);
        p("sizo", "СИЗО-1 (УФСИН)", "Т", Cat.STATE);
        p("hospital", "Городская больница", "+", Cat.SERVICE);
        p("bank", "Банк", "₽", Cat.SERVICE);
        p("mfc", "МФЦ «Мои документы»", "М", Cat.SERVICE);
        p("post", "Почта России", "@", Cat.SERVICE);
        p("church", "Храм", "†", Cat.SERVICE);
        p("market", "Рынок «Центральный» · «Калибр»", "Р", Cat.FOOD);
        p("shop", "Продукты 24", "П", Cat.FOOD);
        p("cafe", "Кафе «Шашлычный двор»", "К", Cat.FOOD);
        p("restaurant", "Ресторан «Империя»", "И", Cat.FOOD);
        p("shawarma", "Шаурма «У Ашота»", "Ш", Cat.FOOD);
        p("kiosks", "Ларьки", "Л", Cat.FOOD);
        p("bar", "Рюмочная «Встреча»", "Б", Cat.FOOD);
        p("park", "Парк Победы", "♣", Cat.LEISURE);
        p("park2", "Сквер у детсада", "♣", Cat.LEISURE);
        p("fountain", "Сквер с фонтаном", "~", Cat.LEISURE);
        p("stadium", "Стадион «Труд»", "О", Cat.LEISURE);
        p("dk", "Дом культуры", "Д", Cat.LEISURE);
        p("sauna", "Сауна «Берёзка»", "С", Cat.LEISURE);
        p("university", "Юридический институт", "Ю", Cat.EDU);
        p("school", "Школа №1", "1", Cat.EDU);
        p("kindergarten", "Детский сад", "д", Cat.EDU);
        p("garages", "Гаражный кооператив", "Г", Cat.CRIME);
        p("bikers", "Мотоклуб", "М", Cat.CRIME);
        p("pawnshop", "Ломбард «Золотой»", "$", Cat.CRIME);
        p("warehouse", "Склад", "С", Cat.CRIME);
        p("vokzal", "Вокзал", "В", Cat.TRANSPORT);
        p("azs", "АЗС", "Z", Cat.TRANSPORT);
        p("factory", "Машзавод", "З", Cat.TRANSPORT);
        p("autoservice", "Автосервис «Гараж»", "А", Cat.TRANSPORT);
        p("hostel", "Общежитие", "О", Cat.TRANSPORT);
        p("voenkomat", "Военкомат", "В", Cat.ARMY);
        p("army_kpp", "КПП воинской части", "КП", Cat.ARMY);
        p("army_hq", "Штаб части", "Ш", Cat.ARMY);
        p("army_range", "Стрельбище", "◎", Cat.ARMY);
    }

    public static Place get(String key) {
        return ALL.get(key);
    }

    public static String name(String key) {
        Place p = ALL.get(key);
        return p == null ? key : p.name();
    }

    private Places() {
    }
}
