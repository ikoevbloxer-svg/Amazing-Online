package ru.gorod.rusgorod.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import ru.gorod.rusgorod.entity.CitizenEntity;

/** Модель жителя с позами: воинское приветствие, руки вверх, лозунг, жестикуляция, руки за спиной, указание направления. */
public class CitizenModel extends PlayerModel<CitizenEntity> {
    public CitizenModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(CitizenEntity e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (e.getAnim() == CitizenEntity.ANIM_NONE && e.getMainHandItem().getItem() instanceof ru.gorod.rusgorod.item.GunItem) {
            if (e.isAggressive()) {
                // прицельная стойка: оружие у плеча, двумя руками
                rightArm.xRot = -(float) Math.PI / 2f + head.xRot + 0.1f;
                rightArm.yRot = -0.1f + head.yRot;
                rightArm.zRot = 0f;
                leftArm.xRot = -(float) Math.PI / 2f + head.xRot;
                leftArm.yRot = 0.55f + head.yRot;
                leftArm.zRot = 0f;
            } else {
                // «низкая готовность» — ствол вниз-вперёд
                rightArm.xRot = -0.85f + Mth.sin(limbSwing * 0.6662f) * 0.08f * limbSwingAmount;
                rightArm.yRot = -0.15f;
                leftArm.xRot = -0.95f;
                leftArm.yRot = 0.65f;
            }
        }
        switch (e.getAnim()) {
            case CitizenEntity.ANIM_SALUTE -> {
                // Воинское приветствие: правая рука к головному убору
                rightArm.xRot = -2.45f;
                rightArm.yRot = -0.55f;
                rightArm.zRot = 0.35f;
                leftArm.xRot = 0f;
                leftArm.zRot = -0.05f;
            }
            case CitizenEntity.ANIM_HANDS_UP -> {
                rightArm.xRot = (float) -Math.PI;
                rightArm.zRot = 0.25f;
                leftArm.xRot = (float) -Math.PI;
                leftArm.zRot = -0.25f;
            }
            case CitizenEntity.ANIM_PROTEST -> {
                rightArm.xRot = -2.9f + Mth.sin(ageInTicks * 0.5f) * 0.15f;
                rightArm.zRot = 0.15f;
            }
            case CitizenEntity.ANIM_TALK -> {
                rightArm.xRot = -0.75f + Mth.sin(ageInTicks * 0.35f) * 0.25f;
                rightArm.yRot = -0.2f;
            }
            case CitizenEntity.ANIM_CUFFED -> {
                rightArm.xRot = 0.55f;
                rightArm.zRot = -0.25f;
                leftArm.xRot = 0.55f;
                leftArm.zRot = 0.25f;
            }
            case CitizenEntity.ANIM_POINT -> {
                rightArm.xRot = -1.55f;
                rightArm.yRot = head.yRot;
            }
            default -> {
            }
        }
        rightSleeve.copyFrom(rightArm);
        leftSleeve.copyFrom(leftArm);
    }
}
