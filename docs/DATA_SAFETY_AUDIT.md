# Audit Data Safety — coerenza codice ↔ dichiarazione Play Console

Inventario dei dati che KidBox **raccoglie e trasmette** (verificato nel codice), da
confrontare con il modulo "Sicurezza dei dati" del Play Console. Tutto ciò che lascia il
dispositivo (Firestore / Firebase Storage / Cloud Functions / Anthropic) va dichiarato come
**Raccolto**.

Backend: Firebase (Firestore, Storage, Functions, Auth, Messaging) + Anthropic (per AI).
Login: anche Facebook Login SDK. Nessun Crashlytics/Analytics/AdMob.

## Tipi di dato da dichiarare (Raccolti = Sì)

| Categoria Data Safety | Tipo specifico | Dove nel codice | Note |
|---|---|---|---|
| **Info personali** | Nome | Auth / profilo (Firestore) | account |
| **Info personali** | Indirizzo email | Firebase Auth | account |
| **Info personali** | ID utente | Firebase Auth / Firestore | account |
| **Info personali** | Altre info (credenziali salvate) | `PasswordRemoteStore` | password manager — sensibile |
| **Info finanziarie** | Cronologia acquisti | Billing client | abbonamenti |
| **Info finanziarie** | Altre info finanziarie | `ExpenseRemoteStore` | spese famiglia (già 1/4) |
| **Salute e fitness** | Info sanitarie | Health Connect → cartella clinica | ✅ già fatto |
| **Salute e fitness** | Info attività fisica | Health Connect (passi/cal/exercise) | ✅ aggiunto |
| **Messaggi** | Altri messaggi in-app | `ChatRemoteStore` (cifrati) | chat famiglia |
| **Foto e video** | Foto | `PhotoVideoStorageManager`, `AvatarRemoteStore`, chat | upload Storage |
| **Foto e video** | Video | `VideoCompressor` + Storage | upload Storage |
| **File audio** | Registrazioni vocali | `AudioRecorderManager` + `ChatStorageService` | note vocali chat |
| **File e documenti** | File e documenti | `DocumentStorageManager` / `DocumentRemoteStore` | documenti |
| **Calendario** | Eventi calendario | `CalendarRemoteStore` | eventi famiglia |
| **Contatti** | Contatti | chat (contatto condiviso nome+tel), contatti emergenza profilo | trasmessi a Firestore |
| **Posizione** | Posizione approssimativa | `LocationRemoteStore` | condivisione famiglia |
| **Posizione** | Posizione precisa | `LocationRemoteStore` + geofence | background |
| **App activity** | Altre azioni generate dall'utente | note (`NoteRemoteStore`), to-do (`TodoRemoteStore`), eventi animali/veicoli | contenuti utente |
| **ID dispositivo o altri** | Token notifiche (FCM) | `FirebaseMessaging` | push |

## Punti di attenzione

- **Condivisi**: per i dati salute → No (Anthropic = processore). Per gli altri, valutare: i dati
  vanno al TUO backend (Firebase) = raccolti, non "condivisi". Facebook Login SDK invece può
  trasmettere dati a Meta → vedi sotto.
- **Facebook Login SDK**: auto-logging ATTIVO e MANTENUTO (serve per campagne pubblicitarie /
  attribuzione installazioni). Implica:
  - Data Safety: ID dispositivo + Attività nell'app (Interazioni) = **Condivisi: Sì**, scopo
    anche **Pubblicità o marketing**.
  - ⚠️ NORME FAMIGLIE: l'uso dell'advertising ID e la condivisione con Meta sono VIETATI per app
    rivolte a bambini. Il "Pubblico di destinazione" in Play Console DEVE essere solo adulti (18+),
    NON includere minori. Se include minori → conflitto → rifiuto. Da verificare assolutamente.
- **Cifratura in transito**: Sì per tutto (Firebase/HTTPS).
- **Cancellazione dati**: l'app deve offrire un modo per richiedere la cancellazione
  dell'account/dati (requisito Play). Verificare che esista (es. "Elimina account").
- **Crittografia chat**: la chat è cifrata (`ChatCryptoService`) — punto a favore, ma va comunque
  dichiarata come raccolta.

## Prossimo passo

Confrontare questa tabella con il riepilogo "Sicurezza dei dati" attuale e aggiungere i tipi
mancanti. Probabili lacune: Messaggi, Foto/video, Audio, File/documenti, Calendario, Contatti,
App activity, ID dispositivo.
