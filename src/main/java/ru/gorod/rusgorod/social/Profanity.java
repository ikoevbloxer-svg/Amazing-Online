package ru.gorod.rusgorod.social;

import java.util.Set;

/** Распознавание нецензурной брани (для ст. 20.1 КоАП РФ). Проверяет начала слов, чтобы «рубля» и «хлеба» не считались матом. */
public final class Profanity {
    private static final String[] PREFIXES = {"бля", "хуй", "хуе", "хуя", "хую", "хуи", "пизд", "еба", "ебл", "ебу", "ебн", "ебт", "заеб", "наеб",
            "уеб", "выеб", "отъеб", "проеб", "доеб", "долбоеб", "мудак", "мудил", "пидор", "пидар", "залуп", "гандон", "шлюх",
            "охуе", "нахуй", "похуй", "ахуе", "въеб", "съеб", "спизд", "распизд", "опизд"};
    private static final Set<String> WORDS = Set.of("сука", "суки", "сукин", "сучка", "сучий", "сучара", "мразь", "тварь", "ублюдок");

    public static boolean contains(String text) {
        String t = text.toLowerCase().replace('ё', 'е');
        for (String w : t.split("[^а-яa-z0-9]+")) {
            if (w.isEmpty()) continue;
            if (WORDS.contains(w)) return true;
            for (String p : PREFIXES) if (w.startsWith(p)) return true;
            if (w.contains("хуй") || w.contains("пизд")) return true;
        }
        return false;
    }

    private Profanity() {
    }
}
