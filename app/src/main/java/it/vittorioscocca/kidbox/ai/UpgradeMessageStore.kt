package it.vittorioscocca.kidbox.ai

object UpgradeMessageStore {
    @Volatile private var message: String? = null
    @Volatile private var triggerFeature: String? = null

    fun set(msg: String?, triggerFeature: String = "unknown") {
        message = msg
        this.triggerFeature = triggerFeature
    }

    fun consume(): String? = message.also { message = null }
    fun consumeTrigger(): String = (triggerFeature ?: "unknown").also { triggerFeature = null }
}
