package it.vittorioscocca.kidbox.data.health.clinical

object ClinicalRecordPromptRules {
    val supplementalRules: String = """
        UNITÀ DI MISURA FARMACI (obbligatorio):
        Le unità dei farmaci devono essere corrette: compresse/capsule → mg o mcg (mai ml); farmaci liquidi orali → ml; iniettabili → mg/ml o UI.
        Esempio: Ezetimibe 10 mg (compressa), NON "10 millilitri".

        TRANSAMINASI E SOSPENSIONE STATINA:
        Se nei dati compaiono terapia sospesa/sostituita e rialzo transaminasi (GOT/GPT) nello stesso periodo, esplicita il nesso causale in prosa.

        PRESSIONE ARTERIOSA:
        NON creare una sezione standalone "PRESSIONE ARTERIOSA": i dati pressori vanno narrati solo dentro CARDIOLOGIA.
        Con più di 4 misurazioni PA nello stesso anno NON elencarle tutte: indica range min-max, valore più recente e tendenza.

        APPLE HEALTH / WEARABLE (solo se presenti nei dati):
        Sezione opzionale con disclaimer: dati da dispositivo consumer, valore indicativo non diagnostico.
        Commenta FC a riposo, VO2 max, minuti attività settimanali, SpO2 notturna, passi, HRV se disponibili; confronta con visite quando possibile;
        usa fasce di riferimento per età per VO2; chiudi con sintesi sul livello di attività fisica.
    """.trimIndent()
}
