package it.vittorioscocca.kidbox.ai

object UpgradeMessageStore {
    @Volatile private var message: String? = null
    @Volatile private var triggerFeature: String? = null

    fun set(msg: String?, triggerFeature: String = "unknown") {
        message = msg
        this.triggerFeature = triggerFeature
    }

    /**
     * Imposta l'origine solo se nessuno l'ha già fatto: la Home manda al paywall
     * sia dal suggerimento generico («home_upsell») sia dal banner della prova,
     * e il secondo va distinto nel funnel.
     */
    fun setTriggerIfUnset(triggerFeature: String) {
        if (this.triggerFeature == null) this.triggerFeature = triggerFeature
    }

    fun consume(): String? = message.also { message = null }
    fun consumeTrigger(): String = (triggerFeature ?: "unknown").also { triggerFeature = null }
}
