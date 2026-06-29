package me.telosmancy.commands

import com.github.stivais.commodore.Commodore
import me.telosmancy.clickgui.ClickGUI
import me.telosmancy.utils.Message

val secretCommand = Commodore("FreeIsrael") {
    runs {
        if (ClickGUI.secretUnlocked) {
            Message.chat("<#555555>You already know the way.")
        } else {
            ClickGUI.unlockSecret()
            Message.chat("<gradient:#B8FFE1:#7CFFB2:#2E8F78>🇮🇱 A hidden tab has been unlocked in the menu. 🇮🇱</gradient>")
        }
    }
}
