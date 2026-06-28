package io.github.benderblog.cdripshare.model

enum class BackgroundMode(val label: String) {
    Auto("自动提取"),
    DarkBluePurple("深蓝紫"),
    Navy("藏蓝"),
    GitHubDark("GitHub 暗"),
    DarkGray("深灰"),
    DarkGreen("墨绿"),
    DarkBrown("深棕"),
    ClassicBlack("经典黑"),
    Custom("自定义");

    /** 固定色模式下返回对应的 ARGB 颜色值；Auto 返回 0，Custom 不使用此值 */
    val colorHex: Int
        get() = when (this) {
            Auto           -> 0
            DarkBluePurple -> 0xFF3A3D60L.toInt()
            Navy           -> 0xFF1F2D48L.toInt()
            GitHubDark     -> 0xFF1A1F2EL.toInt()
            DarkGray       -> 0xFF3C3C3CL.toInt()
            DarkGreen      -> 0xFF2A3A2EL.toInt()
            DarkBrown      -> 0xFF3C2E24L.toInt()
            ClassicBlack   -> 0xFF222222L.toInt()
            Custom         -> 0
        }
}
