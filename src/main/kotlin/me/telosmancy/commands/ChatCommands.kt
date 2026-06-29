package me.telosmancy.commands

import com.github.stivais.commodore.Commodore
import com.github.stivais.commodore.utils.GreedyString
import me.telosmancy.features.impl.misc.ChatModule
import me.telosmancy.features.impl.misc.ServerChatCategory

val acCommand = Commodore("ac") {
    executable {
        param("message") {}
        runs { message: GreedyString ->
            ChatModule.enqueueMessage(ServerChatCategory.DEFAULT, message.string)
        }
    }
}

val gcCommand = Commodore("gc") {
    executable {
        param("message") {}
        runs { message: GreedyString ->
            ChatModule.enqueueMessage(ServerChatCategory.GUILD, message.string)
        }
    }
}

val grcCommand = Commodore("grc") {
    executable {
        param("message") {}
        runs { message: GreedyString ->
            ChatModule.enqueueMessage(ServerChatCategory.GROUP, message.string)
        }
    }
}