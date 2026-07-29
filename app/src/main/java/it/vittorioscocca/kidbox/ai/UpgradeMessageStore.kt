package it.vittorioscocca.kidbox.ai

object UpgradeMessageStore {
    @Volatile private var message: String? = null
    fun set(msg: String?) { message = msg }
    fun consume(): String? = message.also { message = null }
}
