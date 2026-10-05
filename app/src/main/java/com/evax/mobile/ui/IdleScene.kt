package com.evax.mobile.ui

/** Local, fictional vignettes; none of these scenes performs a computer task. */
enum class IdleScene(val label: String, val durationMillis: Long) {
    SURFING("网上冲浪", 5_500L),
    READING("看书", 5_800L),
    STARS("接星星", 5_200L),
    COFFEE("喝咖啡", 5_500L),
    GAMING("玩掌机", 6_000L),
    STRETCHING("伸懒腰", 4_800L),
    NAPPING("打小盹", 7_500L),
    RAIN("看雨", 6_000L),
}

internal fun idleSceneScript(scene: IdleScene): AvatarScript {
    val normal = AvatarPose()
    val lookingDown = normal.copy(gazeY = 0.25f, openness = 0.9f, propAlpha = 1f)
    val happy = normal.copy(smile = 1f, propAlpha = 1f, brightness = 1.04f)
    return when (scene) {
        IdleScene.SURFING -> avatarScript(
            0L to normal, 150L to lookingDown.copy(propAlpha = 0f), 600L to lookingDown,
            720L to lookingDown.copy(gazeX = -0.22f), 1_280L to lookingDown.copy(gazeX = -0.22f),
            1_390L to lookingDown.copy(gazeX = 0.24f), 2_100L to lookingDown.copy(gazeX = 0.24f),
            2_300L to lookingDown.copy(openness = 1.14f, tilt = -3f),
            3_200L to lookingDown.copy(openness = 1.14f, tilt = -3f),
            3_400L to happy.copy(nod = -0.02f), 4_150L to happy.copy(nod = -0.02f),
            4_300L to normal.copy(gazeX = 0.15f, propAlpha = 1f),
            4_900L to normal.copy(propAlpha = 0f), scene.durationMillis to normal,
        )
        IdleScene.READING -> avatarScript(
            0L to normal, 500L to lookingDown.copy(gazeX = -0.20f),
            700L to lookingDown.copy(gazeX = -0.20f), 830L to lookingDown.copy(gazeX = 0.20f),
            1_400L to lookingDown.copy(gazeX = 0.20f), 1_530L to lookingDown.copy(gazeX = -0.20f, gazeY = 0.30f),
            2_200L to lookingDown.copy(gazeX = -0.20f, gazeY = 0.30f),
            2_330L to lookingDown.copy(gazeX = 0.20f, gazeY = 0.30f),
            2_950L to lookingDown.copy(gazeX = 0.20f, gazeY = 0.30f),
            3_200L to lookingDown.copy(asymmetry = 0.12f, tilt = 3f, openness = 1.06f),
            4_350L to lookingDown.copy(asymmetry = 0.12f, tilt = 3f, openness = 1.06f),
            4_900L to normal.copy(propAlpha = 1f), 5_350L to normal, scene.durationMillis to normal,
        )
        IdleScene.STARS -> avatarScript(
            0L to normal, 450L to normal.copy(gazeX = 0.30f, gazeY = -0.30f, propAlpha = 1f),
            900L to normal.copy(gazeX = 0.30f, gazeY = -0.30f, propAlpha = 1f),
            1_600L to lookingDown.copy(gazeX = 0.12f, openness = 1.10f),
            2_200L to lookingDown.copy(gazeX = 0.12f, openness = 1.10f),
            2_450L to happy.copy(nod = 0.025f), 3_250L to happy.copy(nod = -0.02f),
            3_800L to normal.copy(propAlpha = 1f), 4_350L to normal, scene.durationMillis to normal,
        )
        IdleScene.COFFEE -> avatarScript(
            0L to normal, 600L to lookingDown, 1_150L to lookingDown,
            1_700L to lookingDown.copy(scale = 1.035f, nod = 0.05f, openness = 0.72f),
            2_450L to lookingDown.copy(scale = 1.035f, nod = 0.05f, openness = 0.72f),
            2_800L to happy.copy(scale = 1.02f), 3_650L to happy.copy(scale = 1.02f),
            4_150L to normal.copy(propAlpha = 1f), 4_900L to normal, scene.durationMillis to normal,
        )
        IdleScene.GAMING -> avatarScript(
            0L to normal, 600L to lookingDown.copy(openness = 0.83f),
            750L to lookingDown.copy(gazeX = -0.20f), 1_400L to lookingDown.copy(gazeX = 0.18f),
            2_000L to lookingDown.copy(gazeX = -0.10f),
            2_200L to lookingDown.copy(tilt = -4f, asymmetry = -0.10f),
            2_900L to lookingDown.copy(tilt = -4f, asymmetry = -0.10f),
            3_150L to lookingDown.copy(gazeX = 0.22f), 3_600L to lookingDown.copy(gazeX = -0.18f),
            4_000L to happy.copy(nod = -0.025f), 4_650L to happy.copy(nod = -0.025f),
            5_100L to normal.copy(propAlpha = 1f), 5_550L to normal, scene.durationMillis to normal,
        )
        IdleScene.STRETCHING -> avatarScript(
            0L to normal, 500L to normal.copy(openness = 0.25f),
            900L to normal.copy(gazeX = -0.25f, width = 1.10f, asymmetry = 0.08f, tilt = -3f),
            1_750L to normal.copy(gazeX = -0.25f, width = 1.10f, asymmetry = 0.08f, tilt = -3f),
            2_250L to normal.copy(gazeX = 0.25f, width = 1.10f, asymmetry = -0.08f, tilt = 3f),
            3_150L to normal.copy(gazeX = 0.25f, width = 1.10f, asymmetry = -0.08f, tilt = 3f),
            3_550L to normal.copy(openness = 1.13f, nod = -0.035f),
            4_050L to normal.copy(openness = 1.13f, nod = -0.035f), scene.durationMillis to normal,
        )
        IdleScene.NAPPING -> avatarScript(
            0L to normal, 650L to normal.copy(openness = 0.55f),
            1_200L to normal.copy(openness = 0.85f), 1_850L to normal.copy(openness = 0.42f, nod = 0.025f),
            2_500L to normal.copy(openness = 0.06f, nod = 0.055f, propAlpha = 0.65f),
            4_900L to normal.copy(openness = 0.06f, nod = 0.055f, propAlpha = 0.65f),
            5_100L to normal.copy(openness = 1.14f, nod = -0.01f),
            5_600L to normal.copy(openness = 1.14f, tilt = -3f),
            6_350L to normal.copy(gazeX = 0.18f, tilt = -2f), scene.durationMillis to normal,
        )
        IdleScene.RAIN -> avatarScript(
            0L to normal, 500L to lookingDown.copy(gazeX = 0.25f, gazeY = 0.12f),
            1_300L to lookingDown.copy(gazeX = 0.25f, gazeY = 0.12f),
            2_000L to lookingDown.copy(gazeX = 0.20f, gazeY = 0.35f),
            2_600L to lookingDown.copy(gazeX = 0.20f, gazeY = 0.35f),
            3_000L to lookingDown.copy(openness = 0.40f, tilt = 2f),
            3_450L to lookingDown.copy(openness = 0.94f, tilt = 2f),
            4_400L to lookingDown.copy(openness = 0.94f, tilt = 2f),
            5_050L to normal.copy(propAlpha = 1f), 5_600L to normal, scene.durationMillis to normal,
        )
    }
}
