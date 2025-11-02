package dev.yourapp.blem3

enum class Action(val label: String) {
    PLAY_PAUSE("Play/Pause"),
    NEXT("Next"),
    PREV("Previous"),
    VOL_UP("Volume Up"),
    VOL_DOWN("Volume Down"),
    NONE("No action");
    companion object { fun fromLabel(l:String)=values().find{it.label==l}?:NONE }
}
