# Health Connect — Giustificazione dei permessi (Play Console / Appello)

Questo documento spiega **perché** KidBox richiede ciascun tipo di dato Health Connect.
Va usato come base per la dichiarazione Health Connect nel Play Console e per eventuali appelli.

## Caso d'uso principale: la Cartella Clinica

KidBox genera una **cartella clinica** dell'utente/bambino che può essere mostrata al medico.
Per essere clinicamente utile, la cartella deve riflettere anche la **forma fisica** della persona:
quanto si muove, se fa sport e quanto, e parametri corporei di base. Questi dati danno al medico
un quadro dello stile di vita (sedentario / attivo / sportivo) e del compenso cardiovascolare.

Il codice che costruisce questa sezione è in
`app/src/main/java/it/vittorioscocca/kidbox/data/health/clinical/ClinicalRecordAppleHealthNarrative.kt`,
alimentato dallo snapshot letto in
`app/src/main/java/it/vittorioscocca/kidbox/data/health/HealthConnectGateway.kt`.

## Mappatura permesso → feature

| Permesso Health Connect | Dato letto | Dove viene usato | Perché è necessario |
|---|---|---|---|
| `READ_HEART_RATE` | Frequenza cardiaca (campioni recenti + media a riposo 90gg) | Cartella clinica (profilo cardiovascolare), schermata Salute | Parametro vitale di base per il medico |
| `READ_STEPS` | Passi giornalieri (oggi + media 90gg) | Cartella clinica ("media passi/die", indice di movimento), schermata Salute | Indice oggettivo del livello di attività quotidiana |
| `READ_WEIGHT` | Peso corporeo più recente | Applicato al profilo del bambino e incluso nella cartella clinica | Parametro antropometrico essenziale per la valutazione medica |
| `READ_ACTIVE_CALORIES_BURNED` | Calorie attive (giorno + serie giornaliera) | Cartella clinica + schermata Salute | Stima del dispendio energetico / intensità dell'attività |
| `READ_EXERCISE` | Sessioni di allenamento (tipo, durata, minuti settimanali) | Cartella clinica (classificazione "pratica sportiva regolare / attività moderata / sedentaria") | Determina se e quanto la persona fa sport — informazione clinica rilevante per il medico |

## Flusso dati AI (importante per Data Safety e privacy)

La cartella clinica può usare una **sintesi AI opzionale** (previo consenso esplicito,
`AIConsentBottomSheet.kt`). In quel caso i dati salute vengono inviati a Firebase Cloud
Function `askAI` (`functions/index.js`, europe-west1) che li inoltra all'**API di Anthropic
(Claude)** — `https://api.anthropic.com/v1/messages`.

Implicazioni:
- Data Safety: Salute/fitness = **Raccolti: Sì**, **Condivisi: No** (Anthropic = fornitore di
  servizi/processore → escluso dalla definizione di "condivisione" di Google), cifrati in transito,
  trattamento temporaneo, raccolta facoltativa, scopo "Funzionalità dell'app".
- Il consenso in-app cita esplicitamente Anthropic ed è già conforme.
- La privacy policy (kidbox.app/privacy) deve citare l'invio dei dati salute ad Anthropic.
- NON usare mai la frase "mai caricati su server esterni": è falsa per il percorso AI.

## Note importanti per la dichiarazione

- I dati sono dichiarati esplicitamente come **indicativi, non diagnostici** (vedi disclaimer in
  `ClinicalRecordAppleHealthNarrative.kt`: "hanno valore indicativo, non diagnostico").
- Tutti i permessi sono in **sola lettura** (`getReadPermission`), nessuna scrittura su Health Connect.
- Nella dichiarazione Health Connect del Play Console: rimuovere eventuali tipi NON presenti nel
  manifest attuale (es. **CyclingPedalingCadence**, segnalato da Google ma NON più nel codice).
- Tenere allineati: `AndroidManifest.xml` ↔ `HealthConnectGateway.requiredPermissions` ↔ dichiarazione Play Console.

## Permessi attualmente richiesti (manifest)

```
android.permission.health.READ_HEART_RATE
android.permission.health.READ_STEPS
android.permission.health.READ_WEIGHT
android.permission.health.READ_ACTIVE_CALORIES_BURNED
android.permission.health.READ_EXERCISE
```

NOTA su "CyclingPedalingCadence": NON è un permesso separato. Google descrive il permesso
`READ_EXERCISE` come "CyclingPedalingCadence/ExerciseSession". Il bundle ha solo i 5 permessi
sopra (verificato in Play Console → Contenuti app → App per la salute → Step 1). Non c'è nulla
da rimuovere: i 3 permessi contestati (READ_WEIGHT, READ_ACTIVE_CALORIES_BURNED, READ_EXERCISE)
vanno GIUSTIFICATI nello Step 2 della dichiarazione, non rimossi.

## Checklist prima di ri-sottomettere

- [ ] Step 1 dichiarazione: funzionalità spuntate = Attività fisica e fitness, Gestione di malattie e patologie, Gestione di farmaci e cure (NON spuntare "Assistenza decisioni cliniche" né "App di dispositivi medici")
- [ ] Step 2 dichiarazione: giustificazione per ogni permesso (vedi mappatura sopra → cartella clinica)
- [ ] (Consigliato) Video dimostrativo che mostra la cartella clinica con i dati salute
- [ ] Risolto anche il prominent disclosure per ACCESS_BACKGROUND_LOCATION (issue separata)
