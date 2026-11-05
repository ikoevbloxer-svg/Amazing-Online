package ru.gorod.rusgorod.util;

import ru.gorod.rusgorod.RusGorod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Простой планировщик отложенных действий на серверном тике (диалоги с паузами, судебные заседания, ответы в чате). */
public final class Scheduler {
    private record Task(long due, Runnable run) {
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();
    private static long now;

    public static void later(int ticks, Runnable r) {
        synchronized (PENDING) {
            PENDING.add(new Task(now + Math.max(1, ticks), r));
        }
    }

    public static void tick(long gameTime) {
        now = gameTime;
        synchronized (PENDING) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
        Iterator<Task> it = TASKS.iterator();
        List<Task> due = new ArrayList<>();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.due <= gameTime) {
                due.add(t);
                it.remove();
            }
        }
        for (Task t : due) {
            try {
                t.run.run();
            } catch (Exception e) {
                RusGorod.LOGGER.error("[RusGorod] Ошибка отложенной задачи", e);
            }
        }
    }

    public static void clear() {
        TASKS.clear();
        synchronized (PENDING) {
            PENDING.clear();
        }
    }

    private Scheduler() {
    }
}
