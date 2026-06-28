package io.github.benderblog.cdripshare.model

enum class BackgroundMode(val label: String) {
    Auto("自动计算-HSL"),
    AutoPalette("自动计算-Android"),
    DarkBluePurple("深蓝紫"),
    Navy("藏蓝"),
    DarkGray("深灰"),
    DarkGreen("墨绿"),
    DarkBrown("深棕"),
    ClassicBlack("经典黑"),
    Custom("自定义");

    val colorHex: Int
        get() = when (this) {
            Auto, AutoPalette, Custom -> 0
            DarkBluePurple -> 0xFF505A88L.toInt()
            Navy           -> 0xFF3A5070L.toInt()
            DarkGray       -> 0xFF585858L.toInt()
            DarkGreen      -> 0xFF425A46L.toInt()
            DarkBrown      -> 0xFF584A3EL.toInt()
            ClassicBlack   -> 0xFF383838L.toInt()
        }
}
