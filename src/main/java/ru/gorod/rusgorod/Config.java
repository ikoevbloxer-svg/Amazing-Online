package ru.gorod.rusgorod;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue GENERATE_CITY;
    public static final ForgeConfigSpec.IntValue CITY_OFFSET_X;
    public static final ForgeConfigSpec.IntValue BASE_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue POPULATION_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SENTENCE_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue BLOCKS_PER_TICK_TASKS;
    public static final ForgeConfigSpec.BooleanValue NPC_SPEECH;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("city");
        GENERATE_CITY = b.comment("Генерировать город рядом со спавном при первом запуске мира")
                .define("generateCity", true);
        CITY_OFFSET_X = b.comment("Смещение западного края города от точки спавна по X (центр города должен быть в пределах 100 блоков)")
                .defineInRange("cityOffsetX", 8, -100, 100);
        BASE_DISTANCE = b.comment("Расстояние от восточного края города до воинской части")
                .defineInRange("militaryBaseDistance", 100, 40, 400);
        BLOCKS_PER_TICK_TASKS = b.comment("Сколько задач генерации выполнять за тик (больше = быстрее, но лагов больше)")
                .defineInRange("generationTasksPerTick", 2, 1, 50);
        b.pop();

        b.push("population");
        POPULATION_MULTIPLIER = b.comment("Множитель численности NPC всех фракций")
                .defineInRange("populationMultiplier", 1.0, 0.1, 5.0);
        NPC_SPEECH = b.comment("NPC говорят фразы в чат игрокам поблизости")
                .define("npcSpeech", true);
        b.pop();

        b.push("law");
        SENTENCE_MULTIPLIER = b.comment("Множитель сроков ареста/заключения (игровое время)")
                .defineInRange("sentenceMultiplier", 1.0, 0.1, 10.0);
        b.pop();

        SPEC = b.build();
    }
}
