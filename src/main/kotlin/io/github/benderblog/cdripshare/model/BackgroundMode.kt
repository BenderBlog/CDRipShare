package io.github.benderblog.cdripshare.model

enum class BackgroundMode(val label: String) {
    Auto("自动提取"),
    DarkBluePurple("深蓝紫"),
    Navy("藏蓝"),
    GitHubDark("GitHub 暗"),
    DarkGray("深灰"),
    DarkGreen("墨绿"),
    DarkBrown("深棕"),
    ClassicBlack("经典黑");

    /** 固定色模式下返回对应的 ARGB 颜色值；Auto 模式返回 0（标记使用自动提取） */
    val colorHex: Int
        get() = when (this) {
            Auto           -> 0
            DarkBluePurple -> 0xFF1E2130L.toInt()
            Navy           -> 0xFF0F1A2EL.toInt()
            GitHubDark     -> 0xFF0D1117L.toInt()
            DarkGray       -> 0xFF2C2C2CL.toInt()
            DarkGreen      -> 0xFF1B2A1EL.toInt()
            DarkBrown      -> 0xFF2A1F1AL.toInt()
            ClassicBlack   -> 0xFF111111L.toInt()
        }
}
