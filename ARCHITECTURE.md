# KidBox Android — Architettura

Documento di riferimento per l'app KidBox Android. Generato analizzando il codebase in `/Users/vscocca/KidBox/KidBoxAndroid/`. Tutti i riferimenti `file:riga` sono presi direttamente dai sorgenti.

L'app Android è una **porting 1:1 della gemella iOS** (commenti ricorrenti come *"Allineato 1:1 con iOS InviteCrypto.swift"*, *"Mirror del comportamento iOS AppCoordinator.switchFamilyIfNeededThenNavigate"*). Tutti gli accessi a Firestore/Storage condividono lo schema iOS, quindi qualsiasi divergenza di nome campo, tipo serializzato o algoritmo crittografico è un bug cross-platform.

---

## 1. Overview del progetto

### 1.1 Scopo e target

KidBox è un'app di organizzazione famigliare multi-piattaforma che mette in un unico contenitore: documenti crittografati, note, calendario, todo, lista della spesa, chat di famiglia (con audio/video/menzioni), foto/video famiglia, spese, biglietti wallet, password manager (con AutoFill Android), pediatria (visite/esami/terapie/vaccini/dose log), Health Connect, animali, casa (oggetti + bollette/contratti), garage (veicoli + interventi), viaggi (planner AI), localizzazione con geofence, "Chiedi all'esperto" (AI assistant).

**Target utenti**: genitori / nuclei familiari italiani. UI hardcoded in italiano (vedi §11.4).

**Package**: `it.vittorioscocca.kidbox` (manifest + `applicationId`).

### 1.2 Configurazione Gradle (`app/build.gradle.kts`)

- `namespace = "it.vittorioscocca.kidbox"`
- `applicationId = "it.vittorioscocca.kidbox"`
- `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`
- `versionCode = 16`, `versionName = "1.0.0"`
- `sourceCompatibility = JavaVersion.VERSION_17`, `jvmTarget = "17"`
- `buildFeatures { compose = true; buildConfig = true }`
- `buildConfigField`: `MAPS_API_KEY` (da `local.properties` — fail-fast se mancante) e `AI_ENABLED = true`
- `packaging.jniLibs.useLegacyPackaging = false` (supporto page-size 16 kB Android 15+)
- `buildTypes.release { isMinifyEnabled = false; proguardFiles(...) }`
- **Nessun signingConfig**, **nessun flavor**: solo build type `release` standard (+ `debug` implicito)
- `configurations.configureEach { resolutionStrategy { force(...) } }` forza `androidx.core:core(-ktx):1.15.0` e tutte le CameraX a `1.5.3`
- Plugin: `android.application`, `kotlin.android`, `kotlin.compose`, `hilt`, `ksp`, `google.services`. **Niente `kapt`**: tutto KSP

### 1.3 Build script root e settings

- `build.gradle.kts` (root): dichiara plugin con `apply false`.
- `settings.gradle.kts`: `rootProject.name = "KidBox"`, **unico modulo `:app`** (monomodulo).
- `gradle.properties`: `org.gradle.parallel=true`, `org.gradle.configuration-cache=true`, `android.useAndroidX=true`, `kotlin.code.style=official`, `android.nonTransitiveRClass=true`.
- `repositories`: `google()`, `mavenCentral()`, `gradlePluginPortal()` con `RepositoriesMode.FAIL_ON_PROJECT_REPOS`.

### 1.4 Manifest, servizi, capability (`app/src/main/AndroidManifest.xml`)

**Permessi runtime principali**: `INTERNET`, `POST_NOTIFICATIONS`, `CAMERA`, `RECORD_AUDIO`, `READ_CONTACTS`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` (Android 14+, per la condivisione posizione live in background via foreground service), `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, e i permessi Health Connect (`health.READ_HEART_RATE`, `READ_STEPS`, `READ_WEIGHT`, `READ_ACTIVE_CALORIES_BURNED`, `READ_EXERCISE`). Vengono **rimossi** con `tools:node="remove"` `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` (accesso foto/video tramite Photo Picker). `<queries>` dichiara `com.google.android.apps.healthdata`.

**Application class**: `.KidBoxApplication` (vedi §3.4):
- `@HiltAndroidApp`, implementa `Configuration.Provider` per WorkManager con `HiltWorkerFactory` iniettato
- Il manifest **rimuove** l'auto-init `androidx.work.WorkManagerInitializer` (`tools:node="remove"`) per garantire che la factory Hilt sia disponibile prima dei worker `@HiltWorker`

**Activity**:
- `.MainActivity` (launcher + `SEND application/pdf`, splash via `core-splashscreen:1.0.1`, `@AndroidEntryPoint`)
- `.ui.share.ShareReceiverActivity` (theme `Theme.Translucent`, intent filters `ACTION_SEND`/`ACTION_SEND_MULTIPLE` per `text/plain`, `image/*`, `video/*`, `application/pdf`, `*/*`)
- 5 Activity traslucide del modulo password autofill in `.feature.passwords.autofill.*`

**Servizi**:
- `.notifications.KidBoxFirebaseMessagingService` (FCM)
- `.data.location.LocationSharingService` (`foregroundServiceType="location"`) — foreground service che invia la posizione live a Firestore anche con app in background/chiusa
- `.feature.passwords.autofill.KidBoxCredentialProviderService` (Credential Manager, permission `BIND_CREDENTIAL_PROVIDER_SERVICE`)
- `.feature.passwords.autofill.KidBoxAutofillService` (autofill legacy, permission `BIND_AUTOFILL_SERVICE`)

**Receivers**:
- `.data.location.GeofenceTransitionReceiver`
- `.notifications.TodoReminderReceiver`, `.notifications.HealthReminderReceiver`
- `.notifications.BootReceiver` (esportato, `BOOT_COMPLETED`)
- `.ui.screens.ai.planning.WeeklySummaryBroadcastReceiver`, `DailyBriefingBroadcastReceiver`, `HealthPatternBroadcastReceiver`

**Content provider**: `FileProvider` con authorities `${applicationId}.fileprovider` (paths in `@xml/file_paths`).

**Meta-data**: `com.facebook.sdk.ApplicationId/ClientToken` da `strings.xml`, `com.google.android.geo.API_KEY` con placeholder.

---

## 2. Struttura dei package

Sotto `app/src/main/java/it/vittorioscocca/kidbox/`:

```
ai/             — Servizi AI lato app (no UI)
billing/        — Google Play Billing
data/           — Layer dati (Room + Firebase + sync)
di/             — Moduli Hilt
domain/         — Modelli puri + 1 sola UseCase
feature/        — Feature isolate (password autofill)
health/         — Helpers di dominio salute
network/        — Header HTTP costanti
notifications/  — Scheduler + receiver notifiche
ui/             — Compose UI
util/           — KBLog, crash handler, utility
KidBoxApplication.kt, MainActivity.kt   (root)
```

### 2.1 `data/` — il package più popolato

- **`data/local/`** — persistenza locale (Room + SharedPreferences)
  - `db/KidBoxDatabase.kt` — `@Database(version = 34, exportSchema = false)` con 49 entità
  - `dao/` — 47 DAO (`KBChatMessageDao`, `KBDocumentDao`, `KBFamilyDao`, `KBTodoItemDao`, `WalletTicketDao`, `PetDao`, `VehicleDao`, `PasswordEntryDao`, `PwnedPrefixCacheDao`, `KBTreatmentDao`, `KBVaccineDao`, `KBTripDao`, …)
  - `entity/` — 48 entità Room (parallele ai DAO)
  - `mapper/` — 5 mapper Entity↔Domain (solo modulo salute)
  - File DataStore/Preferences a piatto: `OnboardingPreferences`, `ThemePreference`, `FamilySessionPreferences`, `DocumentsUiPreferences`, `MessageSettingsPreferences`, `TravelProfilePreferences`, `ActiveFamilyResolver`, `AiConsentStore`

- **`data/remote/`** — integrazione Firebase / Storage / server-side
  - Top-level: `AppCheckStoragePreflight.kt`, `AppCheckTokenCache.kt`, `DocumentCryptoManager.kt`, `DocumentRemoteStore.kt`, `DocumentStorageManager.kt`, `FirebaseStorageUploadExtras.kt`, `PhotoVideoRemoteStore.kt`, `PhotoVideoStorageManager.kt`
  - Sotto-pacchetti per dominio: `auth/`, `ai/`, `family/`, `health/`, `calendar/`, `chat/`, `expenses/`, `grocery/`, `life/`, `location/`, `notes/`, `passwords/`, `support/`, `todo/`, `travel/`, `user/`, `wallet/`

- **`data/repository/`** — 31 repository centrali che orchestrano local/remote/sync (es. `ChatRepository.kt` ~23 KB, `DocumentRepository.kt` 1829 righe, `PhotoVideoRepository.kt` ~30 KB, …)

- **`data/sync/`** — 9 SyncCenter per mirror bidirezionale Firestore ↔ Room: `FamilySyncCenter.kt` (~55 KB, 975 righe), `MembershipSyncService.kt`, `DoseLogSyncCenter.kt`, `MedicalExamSyncCenter.kt`, `MedicalVisitSyncCenter.kt`, `PediatricProfileSyncCenter.kt`, `TreatmentSyncCenter.kt`, `VaccineSyncCenter.kt`, + `FamilyMemberFirestoreMappers.kt`

- **`data/crypto/`** — crittografia: `FamilyKeyStore.kt` (EncryptedSharedPreferences), `FamilyKeyEscrow.kt`, `InviteCrypto.kt`, `PasswordCryptoEngine.kt`, `PasswordCypher.kt`

- **`data/chat/`** — `crypto/`, `local/`, `model/` (specifici chat E2E)

- **`data/notification/`** — `CountersService.kt`, `HomeBadgeManager.kt`, `PushNotificationManager.kt`, `TodoReminderScheduler.kt`

- **`data/location/`** — `GeofenceMonitorService.kt`, `GeofenceMonitorState.kt`, `GeofenceTransitionReceiver.kt`, `LocationSharingService.kt` (foreground service condivisione live), `GeofenceMonitorRestorer.kt` + `GeofenceMonitorEntryPoint.kt` (ri-registrazione geofence all'avvio app e dopo il boot)
  - **Condivisione live in background**: gestita da `LocationSharingService` (foreground service di tipo `location`, notifica persistente), avviato/fermato da `FamilyLocationViewModel`; è il writer autoritativo verso Firestore. Lo stream interno al ViewModel serve solo alla UI mentre la schermata è aperta.
  - **Monitoraggio geofence**: `GeofenceMonitorService.syncMonitoring(...)` è **indipendente** dalla condivisione live (non più gated su `isSharing`); richiede solo `ACCESS_BACKGROUND_LOCATION`, verificato internamente. Le geofence di sistema persistono via `GeofencingClient`; `GeofenceMonitorRestorer` le ri-registra dalla cache Room all'avvio app (`KidBoxApplication`) e dopo il `BOOT_COMPLETED` (l'OS le azzera al reboot).

- **`data/passwords/`** — `AutoFillSnapshot.kt`, `AutoFillSnapshotEncryptedStore.kt`, `FaviconResolver.kt`, `RebuildAutoFillSnapshotWorker.kt`, + `otp/` e `security/` (pwned-password / breach check)

- **`data/wallet/`** — `WalletPdfParser.kt` (~23 KB), `PendingWalletImport.kt`, `WalletReminderPrefs.kt`

- **`data/health/`** — `HealthConnectGateway.kt`, `HealthAttachmentService.kt`, `HealthLinkStore.kt`, `HealthDocumentTextExtractor.kt`, `HealthFolderResolver.kt`, `HealthOcrRecoveryMigration.kt`, + `ai/` e `clinical/` (26 file clinici)

- **`data/home/`, `data/life/`, `data/pets/`, `data/travel/`, `data/user/`, `data/vehicles/`, `data/ai/`** — attachment-tags, calcolatori scadenze, repo profilo utente, store extras viaggi, store impostazioni AI

### 2.2 `domain/` — sottile (no Android API)

- `domain/model/` — 40+ `data class` `KB*` (es. `KBFamily`, `KBChild`, `KBChatMessage`, `KBDocument`, `KBNote`, `KBEvent`, `KBExpense`, `KBGroceryItem`, `KBTodoItem/List`, `KBMedicalExam/Visit`, `KBPediatricProfile`, `KBVaccine`, `KBTreatment`, `KBDoseLog`, `KBCustodySchedule`, `KBRoutine/Check`, `KBSyncState`, `KBVisibilityScope`, `KBFamilyPhoto/Album`, `KBAIConversation/Message`, `KBPlan`, `KBCalendarEvent`, …). Più `HealthImportSnapshot.kt`, `HealthTimelineEvent.kt`, `TodoListExposure.kt`, `TreatmentSchedulePeriod.kt`, `WalletTicketKind.kt`, `KidBoxEnums.kt`, `KidBoxHealthEmbedded.kt`, sotto-cartella `ai/`
- `domain/auth/LogoutUseCase.kt` — **l'unica vera UseCase** del progetto
- `domain/family/FamilySubscriptionAccess.kt`, `FirestoreFamilyOwnership.kt`
- `domain/health/DrugCatalog.kt`, `HealthAgeFormatting.kt`

### 2.3 `feature/passwords/` — feature isolata

- `PasswordGenerator.kt`, `PasswordStrength.kt`
- `autofill/` — `KidBoxAutofillService.kt`, `KidBoxCredentialProviderService.kt`, 4 Activity traslucide, `AutofillFamilyIdResolver.kt`, `DomainMatcher.kt`, `NativeAppDomainResolver.kt`, `KidBoxAutofillEntryPoint.kt`, `AutoFillProviderSettingsActivity.kt`
- `io/` — `PasswordsTxt{Exporter,Importer,Models,Parser}.kt`

### 2.4 `ui/` — Compose

- `ui/navigation/` — `AppDestination.kt` (~24 KB, sealed class delle route), `AppNavGraph.kt` (1825 righe, l'unico `NavHost`), `KidBoxDeepLinkMessages.kt`, `NavBackStack.kt`, `NavControllerTravelPlanning.kt`
- `ui/theme/Theme.kt` — `KidBoxTheme`
- `ui/components/` — `KidBoxHeaderCircleButton.kt`, `KidBoxIosFormChrome.kt` (look "iOS-style"), `PasswordStrengthMeter.kt`
- `ui/permissions/RuntimePermissions.kt`
- `ui/family/FamilySwitcherBottomSheet.kt`, `FamilySwitcherViewModel.kt`
- `ui/share/` — `ShareReceiverActivity.kt`, `ShareBottomSheet.kt`, `ShareActionHandler.kt`, `ShareDestination.kt`, `ShareContentClassifier.kt`, `ShareAIClassifier.kt`
- `ui/splash/SplashScreen.kt`, `ui/state/BannerMessageStore.kt`, `ui/subscription/`, `ui/util/`

#### `ui/screens/` — 24 aree feature

Convenzione: per ciascuna area `*Screen.kt` + `*ViewModel.kt` (`@HiltViewModel`).

`auth/`, `onboarding/`, `home/`, `chat/`, `documents/`, `notes/`, `calendar/`, `todo/`, `grocery/`, `expenses/`, `photos/`, `wallet/`, `health/`, `location/`, `pets/`, `vehicles/`, `homeitems/`, `passwords/`, `travel/` (38 file), `settings/` (con `family/` e `support/`), `ai/common/` (componenti UI), `ai/planning/` (planning AI: `PlanningAIChatScreen.kt`, `PlanningAIChatViewModel.kt` ~42 KB, `WeeklySummaryService.kt`, `DailyBriefingService.kt`, `HealthPatternAnalyzerService.kt`, `FamilyMemoryService.kt`, …).

### 2.5 `notifications/`

- `KidBoxFirebaseMessagingService.kt` (FCM)
- `NotificationDeepLinkRouter.kt` (singleton, deep link da push)
- Scheduler `AlarmManager`/`WorkManager`: `ExactAlarmScheduler.kt`, `ExamReminderScheduler.kt`, `HousePaymentReminderScheduler.kt`, `VaccineReminderScheduler.kt`, `VehicleDeadlineReminderScheduler.kt`, `VisitReminderScheduler.kt`, `WalletReminderScheduler.kt`
- Receiver: `BootReceiver.kt`, `TodoReminderReceiver.kt`, `HealthReminderReceiver.kt`
- Manager: `TreatmentNotificationManager.kt`, `SecurityNotifier.kt`, `NotificationBadgeStore.kt`, `VehicleReminderEntryPoint.kt`

### 2.6 `util/`

- `KBLog.kt` + `KBFileLogger.kt` — logging centralizzato proprietario
- `KBCrashHandler.kt`, `CrashAnalyzer.kt`, `CrashReportPreferences.kt`
- `KidBoxApplicationHolder.kt`, `BitmapOrientationUtils.kt`, `ChatDocumentFileNaming.kt`, `StringListJson.kt`, `VideoCompressor.kt` (~13 KB, basato su `MediaCodec`)

### 2.7 Risorse

- `app/google-services.json`
- `app/src/main/res/values/`: `strings.xml`, `colors.xml`, `themes.xml`, `google_fonts_certs.xml`
- `app/src/main/res/values-night/`: tema dark
- `app/src/main/res/xml/`: `file_paths.xml` (FileProvider), `provider.xml` (Credential Provider), `autofill_service.xml`
- Test: solo `app/src/test/java`; **nessun `androidTest`**
- `scripts/`: `generate_launcher_assets.py`, `install-debug-device.sh`, `migrate_to_kblog.py` (script di migrazione `Log.*` → `KBLog.*`)

---

## 3. Architettura generale

**KidBox Android NON è MVVM puro né Clean Architecture stretta**: è un **MVVM con Repository pattern** + layered architecture leggera (`data/` + `domain/` + `ui/`), con `domain/` molto sottile.

### 3.1 `domain/` — molto sottile

Solo modelli puri + pochissimi helper. **Un solo `*UseCase.kt`** in tutto il progetto:

- `domain/model/` (~40 file): `KBNote.kt`, `KBMedicalVisit.kt`, `KBFamily.kt`, `KBPlan.kt`, `KBChild.kt`, `KBDocument.kt`, `KBDoseLog.kt`, `KBTreatment.kt`, `KBVaccine.kt`, `KBChatMessage.kt`… — `data class` immutabili senza dipendenze Android.
- `domain/auth/LogoutUseCase.kt:14-44` — unica vera UseCase. Ha `logout()` e `logoutAndWipeLocalData()`; orchestra Firebase signOut + `database.clearAllTables()` + reset preferenze + stop `MembershipSyncService`.
- `domain/family/FamilySubscriptionAccess.kt:13-28`, `domain/family/FirestoreFamilyOwnership.kt`
- `domain/health/DrugCatalog.kt`, `domain/health/HealthAgeFormatting.kt`

**Non ci sono interfacce repository nel `domain/`**: i repository vivono in `data/repository/` e i ViewModel ne dipendono direttamente. Eccezione unica: `SubscriptionRepository` ha l'interfaccia in `data/repository/SubscriptionRepository.kt:6-20` con impl bound via Hilt (vedi §4).

### 3.2 `data/repository/` — orchestrazione locale+remoto

31 repository, tutti `@Singleton` + `@Inject constructor`. Esempio canonico:

```30:36:app/src/main/java/it/vittorioscocca/kidbox/data/repository/NoteRepository.kt
class NoteRepository @Inject constructor(
    private val noteDao: KBNoteDao,
    private val familyDao: KBFamilyDao,
    private val remoteStore: NoteRemoteStore,
    private val auth: FirebaseAuth,
) {
```

Espongono `Flow` che combinano Room + listener Firestore (pattern `startRealtime()/stopRealtime()`). Ogni repository ha un proprio `CoroutineScope(SupervisorJob() + Dispatchers.IO)` + `Mutex` per la concorrenza.

Repository più complessi:
- `DocumentRepository.kt` (1829 righe) — documenti + categorie + storage + estrazione testo
- `ChatRepository.kt`, `PhotoVideoRepository.kt`, `TripRepository.kt`, `MedicalVisitRepository.kt`

### 3.3 `data/local/` — Room

- `KidBoxDatabase.kt` — `@Database(version = 34, exportSchema = false)` con 49 entità e altrettanti DAO accessor
- `dao/` — 47 DAO con pattern `@Query` + `@Insert(onConflict = REPLACE) suspend fun upsert(...)`, `observeByFamilyId(): Flow<List<...>>`
- `entity/` — 48 `@Entity` con FK + Index
- `mapper/` — 5 mapper espliciti (solo modulo salute); per le altre entità i mapper sono inline nei repository
- **Niente Jetpack DataStore**: tutte le preferenze sono wrapper su SharedPreferences

### 3.4 `data/remote/` — Firebase

Sotto-package per ogni feature: `auth/`, `notes/`, `chat/`, `health/`, `passwords/`, `family/`, `wallet/`, `calendar/`, …
+ classi top-level per Storage/App Check (`DocumentStorageManager.kt`, `DocumentRemoteStore.kt`, `PhotoVideoStorageManager.kt`, `AppCheckStoragePreflight.kt`, `AppCheckTokenCache.kt`).

### 3.5 Application class

```19:43:app/src/main/java/it/vittorioscocca/kidbox/KidBoxApplication.kt
@HiltAndroidApp
class KidBoxApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    private val appInitScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override fun onCreate() {
        KidBoxApplicationHolder.applicationContext = applicationContext
        KBFileLogger.init(this)
        KBCrashHandler.install()
        super.onCreate()
        WorkManager.initialize(this, workManagerConfiguration)
        KidBoxFirebaseMessagingService.createNotificationChannels(this)
        appInitScope.launch { CrashAnalyzer.analyzeIfNeeded(this@KidBoxApplication) }
    }
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
```

Inizializza: file-logger custom (`KBFileLogger`), crash handler proprietario (`KBCrashHandler`), WorkManager manuale (vedi commento in `AndroidManifest.xml:144-158` che spiega perché disabilitano l'auto-init Startup di WorkManager — serve a passare `HiltWorkerFactory`), notification channels FCM, crash analyzer asincrono.

### 3.6 MainActivity

```31:48:app/src/main/java/it/vittorioscocca/kidbox/MainActivity.kt
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var themePreference: ThemePreference
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        title = ""
        WindowCompat.setDecorFitsSystemWindows(window, true)
        applySystemBarAppearance(resolveDarkTheme())
        val onboardingPreferences = OnboardingPreferences(applicationContext)
        NotificationDeepLinkRouter.handleLaunchIntent(this, intent)
        setContent { ... }
    }
```

Punto degno di nota: **NON è edge-to-edge** — usa `setDecorFitsSystemWindows(window, true)` e colora manualmente status/navigation bar con colori fissi `#FFF5F3EE` (light) / `#FF1C1C1E` (dark). Compose è inizializzato in `setContent { KidBoxTheme(...) { Box { AppNavGraph(...); CrashReportConsentDialog(...) } } }` con `rememberNavController()`. Override `onNewIntent` re-instrada il deep link da notifica.

---

## 4. Dependency Injection (Hilt)

Tutti i moduli sono in `app/src/main/java/it/vittorioscocca/kidbox/di/`, tutti `@InstallIn(SingletonComponent::class)`.

### 4.1 Moduli (6 file)

#### `AIModule.kt` — `object`
- `@Provides @Singleton fun provideKBAIRepository(conversationDao, messageDao): KBAIRepository`

#### `AuthModule.kt` — `object`
- `provideFirebaseAuth(): FirebaseAuth`
- `provideCredentialManager(@ApplicationContext): CredentialManager`
- `provideFacebookCallbackManager(): CallbackManager`
- `provideAuthFacade(appleAuthService, googleAuthService, firebaseAuth): AuthFacade` — costruisce una `Map<AuthProvider, AuthService>` con APPLE + GOOGLE

#### `BillingModule.kt` — `object`
- `provideBillingClient(@ApplicationContext): BillingClient` (Play Billing, `enableOneTimeProducts()`)
- `provideFirebaseFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance("europe-west1")`
- `provideKBBillingManager(...)`

#### `DatabaseModule.kt` — modulo "monstre" (1271 righe)
- `provideKidBoxDatabase(@ApplicationContext): KidBoxDatabase` costruisce `Room.databaseBuilder("kidbox.db")` registrando **31 migrazioni** esplicite `MIGRATION_4_5` … `MIGRATION_33_34` + `.fallbackToDestructiveMigration()`
- Poi ~46 `@Provides fun provideXxxDao(database): XxxDao = database.xxxDao()`, uno per ogni DAO

#### `PasswordsModule.kt` — `object`
- `providePasswordSecurityOkHttpClient(): OkHttpClient` — client custom per HIBP (timeout 8s, no cookie, `MODERN_TLS`, no-redirects)
- `provideClock(): Clock = Clock.systemUTC()`

#### `SubscriptionModule.kt` — `abstract class`, unico `@Binds`

```13:20:app/src/main/java/it/vittorioscocca/kidbox/di/SubscriptionModule.kt
abstract class SubscriptionModule {
    @Binds @Singleton
    abstract fun bindSubscriptionRepository(
        impl: SubscriptionRepositoryImpl,
    ): SubscriptionRepository
}
```

`SubscriptionRepository` è **l'unica interfaccia repository** astratta. Tutte le altre sono classi concrete iniettate direttamente.

### 4.2 `@HiltViewModel` — ~74 ViewModel

Esempi: `HomeViewModel.kt:116-132`, `ChatViewModel.kt:69-78`, `NoteDetailViewModel.kt:43-49`, `DocumentsViewModel.kt:64-74`, `MedicalVisitDetailViewModel.kt:65-76`. Ottenuti dai Composable via `hiltViewModel()` dal modulo `androidx.hilt:hilt-navigation-compose`.

### 4.3 `SavedStateHandle`

Usato come parametro nel costruttore (auto-iniettato da Hilt). 18 ViewModel lo usano direttamente per estrarre gli argomenti di route:

```31:38:app/src/main/java/it/vittorioscocca/kidbox/ui/screens/photos/PhotoAlbumDetailViewModel.kt
class PhotoAlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PhotoVideoRepository,
) : ViewModel() {
    private val familyId: String = savedStateHandle.get<String>("familyId").orEmpty()
    private val albumId: String = savedStateHandle.get<String>("albumId").orEmpty()
```

Altri: `HousePaymentDetailViewModel`, `VehicleDetailViewModel`, `PetsViewModel`, `HomeItemsViewModel`, `HealthAIChatViewModel`, `GeofenceEditViewModel`, `VehicleInterventionsListViewModel`, `GroceryListViewModel`.

I ViewModel che **non** usano `SavedStateHandle` ricevono i parametri di nav via `bind(...)` chiamato dallo screen in `LaunchedEffect` (es. `NoteDetailViewModel.bind()`).

### 4.4 `@AndroidEntryPoint` (8 punti)

- `MainActivity.kt:31`
- `ui/share/ShareReceiverActivity.kt`
- `data/location/GeofenceTransitionReceiver.kt`
- 5 Activity di autofill in `feature/passwords/autofill/`

`KidBoxFirebaseMessagingService` e gli altri Receiver dichiarati nel manifest **non** sono `@AndroidEntryPoint` — gestiscono internamente le dipendenze tramite API statiche.

### 4.5 `@Inject constructor` vs field injection

Tutti i repository, ViewModel, service interni usano `@Inject constructor`. Field injection solo nelle Activity/Application: `MainActivity.themePreference`, `KidBoxApplication.workerFactory`.

### 4.6 `@Qualifier` custom

**Nessuno.** Si usa solo `@ApplicationContext` di Hilt (es. `BillingModule.kt:25`, `DocumentRepository.kt:76`, `DatabaseModule.kt:1083`) e iniezione per tipo. Nessun `@Named`.

### 4.7 `@HiltWorker` (5 worker)

```28:37:app/src/main/java/it/vittorioscocca/kidbox/data/passwords/RebuildAutoFillSnapshotWorker.kt
@HiltWorker
class RebuildAutoFillSnapshotWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val entryDao: PasswordEntryDao,
    private val passwordCypher: PasswordCypher,
    private val auth: FirebaseAuth,
    private val familySessionPreferences: FamilySessionPreferences,
    private val encryptedStore: AutoFillSnapshotEncryptedStore,
) : CoroutineWorker(appContext, workerParams) {
```

Altri: `data/passwords/security/SecurityScanWorker.kt`, `DailyBriefingWorker`, `WeeklySummaryWorker`, `HealthPatternWorker` (annidati nei service in `ui/screens/ai/planning/`).

---

## 5. Gestione dello stato

### 5.1 Pattern UiState — sempre `data class`, mai sealed class

```47:80:app/src/main/java/it/vittorioscocca/kidbox/ui/screens/chat/ChatViewModel.kt
data class ChatUiState(
    val isLoading: Boolean = true,
    val familyId: String = "",
    val currentUid: String = "",
    val messages: List<UiChatMessage> = emptyList(),
    val inputText: String = "",
    val typingUsers: List<String> = emptyList(),
    ...
)

@HiltViewModel
class ChatViewModel @Inject constructor(...) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState(currentUid = auth.currentUser?.uid.orEmpty()))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
```

Pattern identico in `HomeViewModel`, `NoteDetailViewModel`, `DocumentsViewModel`, `MedicalVisitDetailViewModel`, `PhotoAlbumDetailViewModel`.

**Sealed class per stato UI: nessuna.** Sealed class sono usate solo per:
- destinazioni navigazione (`AppDestination.kt:3`)
- enum-like eventi interni (es. `HeroDownloadOutcome` privato in `HomeViewModel.kt:96-100`)

### 5.2 Convenzioni

- Backing field: sempre `private val _uiState = MutableStateFlow(...)` + `val uiState: StateFlow<...> = _uiState.asStateFlow()`. Talvolta più `StateFlow` nello stesso VM
- Coroutine: `viewModelScope.launch { ... }` ovunque. Spesso con `Dispatchers.IO` esplicito per lavoro pesante. Uso massiccio di `collectLatest`, `combine`, `map` di kotlinx.flow
- Collect lato Compose: sempre `collectAsStateWithLifecycle()` da `androidx.lifecycle.compose`
- **`LiveData` mai usato.** Tutto è `StateFlow`/`Flow`

Esempio canonico di `combine` con Flow Room:

```253:263:app/src/main/java/it/vittorioscocca/kidbox/ui/screens/home/HomeViewModel.kt
combine(
    familyDao.observeAll(),
    familyMemberDao.observeActiveByFamilyId(familyId),
    sharedLocationDao.observeActiveByFamilyId(familyId),
) { fams, members, sharedUsers ->
    Triple(
        fams.firstOrNull { it.id == familyId },
        members.size,
        sharedUsers,
    )
}.collect { (fam, memberCount, sharedUsers) ->
```

---

## 6. Firebase

> **Progetto Firebase**: `kidbox-42cd7` — **Bucket Storage**: `kidbox-42cd7-eu` (EU) — **Region Cloud Functions**: `europe-west1` (hard-coded in tutti i client) — **Firebase BoM**: `33.7.0`

### 6.1 Firestore — Pattern di accesso

L'accesso al client Firestore è ovunque della forma:

```kotlin
private val db get() = FirebaseFirestore.getInstance()
```

Non esistono singleton iniettati globalmente (volutamente: dopo i `FirebaseFirestore.terminate()` / `clearPersistence()` del `LoginViewModel` il singleton si rigenera).

### 6.2 Albero delle collezioni

```
diagnostics/{doc}                                  (open r/w — diag-only)

users/{uid}                                        UserProfileRepository, ProfileViewModel
├─ memberships/{familyId}                          MembershipSyncService (listener real-time)
├─ fcmTokens/{token}                               KidBoxFirebaseMessagingService.onNewToken
├─ notificationPrefs                               AIRemotePreferences
└─ aiPrefs                                         AIRemotePreferences

families/{familyId}                                FamilyRepository, FamilyHeroPhotoService, FamilySyncCenter
├─ members/{uid}                                   FamilyFirestoreCreationRepository, MembershipSyncService
├─ children/{childId}                              HealthConnectAppViewModel
├─ invites/{inviteId}                              InviteWrapService, JoinWrapService
├─ counters/{uid}                                  HomeBadgeManager (listener), CountersService (reset)
├─ memberKeyBackups/{userId}                       FamilyKeyEscrow.backup/recover
├─ documents/{docId}                               DocumentRemoteStore
├─ documentCategories/{catId}                      DocumentRemoteStore
├─ walletTickets/{ticketId}                        WalletRemoteStore
├─ todoLists/{listId}, todos/{todoId}              TodoRemoteStore
├─ calendarEvents/{eventId}                        CalendarRemoteStore
├─ chatMessages/{messageId}, typing/{uid}          ChatRemoteStore
├─ notes/{noteId}                                  NoteRemoteStore (cifrate)
├─ passwords/{id}, passwordGroups/{id}             PasswordRemoteStore (cifrate)
├─ medicalExams, medicalVisits, pediatricProfiles, doseLogs, treatments, vaccines
├─ expenses/{expenseId}                            ExpenseRemoteStore
├─ groceries/{groceryId}                           GroceryRemoteStore
├─ geofences/{geofenceId}                          GeofenceRemoteStore
├─ photos/{photoId}, photoAlbums/{albumId}         PhotoVideoRemoteStore
├─ memoryFacts/{factId}                            MemoryFactRemoteStore (AI memory)
├─ housePayments/{paymentId}                       HousePaymentRemoteStore
├─ pets/{petId}, petEvents/{eventId}               PetRemoteStore, PetEventRemoteStore
└─ trips/{tripId}                                  TripRemoteStore
   ├─ legs/{legId}, dayPlans/{dayPlanId}, packingItems/{itemId}

invites/{code}                                     InviteRemoteStore (codici flat)
support_tickets/{ticketId}                         shape valida + platform in ['ios','android']
crash_reports/{reportId}                           append-only, opt-in, rawLogs <= 50 KB
```

> Tutto ciò che è **per-famiglia** vive sotto `families/{familyId}/*`. Ciò che è **per-utente** vive sotto `users/{uid}/*`. Tre eccezioni globali: `invites/{code}`, `support_tickets`, `crash_reports`.

### 6.3 DTO / Payload

Ogni `*RemoteStore.kt` definisce un `*RemoteDto` (data class Kotlin) + un `*RemoteChange` sealed interface (`Upsert | Remove`). Esempi:

- `DocumentRemoteStore` → `families/{familyId}/documents/{docId}`: `name`, `categoryId` (con fallback `parentId`/`folderId` per compat cross-client), `storagePath`, `downloadUrl`, `mimeType`, `sizeBytes`, `isDeleted`, `createdBy`, `updatedBy`, `createdAt`/`updatedAt` (Timestamp). DTO Kotlin: `createdAtEpochMillis: Long?`
- `WalletRemoteStore` → `families/{fid}/walletTickets`: `title`, `eventDate` (Timestamp), `eventEndDate`, `storagePath`, `downloadUrl`, `mimeType`, `sizeBytes`, `qrPayload`, `note`, audit, `isDeleted`
- `ChatRemoteStore` → `families/{fid}/chatMessages`: campi sensibili **cifrati** `textEnc` (Base64 di `nonce|ciphertext|tag`). Metadati: `senderId`, `senderName`, `createdAt`, `mediaPath`, `mediaMime`, `readBy`, `deletedFor`, `mentions`, `messageType`. `typing/{uid}` con `isTyping: Boolean`, `updatedAt`
- `NoteRemoteStore` → `families/{fid}/notes`: `titleEnc`, `bodyEnc` (cifrati con `NoteCryptoManager`), `colorRaw`, `pinned`, `createdAt`, `updatedAt`, audit, `isDeleted`
- `PasswordRemoteStore` → `families/{fid}/passwords` + `passwordGroups`: tutti i secrets sono `*CipherB64` (Base64 di dati AES-GCM): `siteCipherB64`, `usernameCipherB64`, `passwordCipherB64`, `notesCipherB64`, `iconHint`, `tsMillis`

### 6.4 Serializzazione tipi temporali

| Modello applicativo | Wire Firestore | Conversione write | Conversione read |
|---|---|---|---|
| `Long` (epoch millis) | `com.google.firebase.Timestamp` | `Timestamp(millis/1000, (millis%1000)*1_000_000)` (helper `millisToTimestamp`) | `(d["x"] as? Timestamp)?.toDate()?.time` |
| Server time | `FieldValue.serverTimestamp()` | per `createdAt/updatedAt/heroPhotoUpdatedAt` su create | lettura come `Timestamp` |

Cast difensivo dei numeri ricorrente:

```kotlin
val giorno: Int? = when (val v = d["giornoDiScadenzaMensile"]) {
    is Int -> v
    is Number -> v.toInt()
    else -> null
}
```

### 6.5 Divergenze potenziali con iOS

1. **`WalletRemoteStore`**: scrive `Timestamp(it / 1000, 0)` per `eventDate`, **azzerando i millisecondi**. Tutti gli altri store usano il helper preciso → possibili "scarti di 1 secondo" cross-platform
2. **`PhotoVideoRemoteStore`**: legge `createdAtEpochMillis`/`updatedAtEpochMillis` come `Long` diretti (non `Timestamp`). Verificare contro lo store iOS
3. **`DocumentRemoteStore`**: legge campo categoria con fallback `categoryId` → `parentId` → `folderId` (doppia convenzione legacy)
4. **`ChatStorageService`** carica i media chat **in chiaro** (`families/$fid/chat/$messageId/$safeName`), mentre tutti gli altri media sono `.kbenc`. La motivazione è "interoperabilità iOS + streaming"

### 6.6 Firestore Rules — impatto sul client

(`/Users/vscocca/KidBox/firestore.rules`)

- `families/{fid}/counters/{uid}` ha `allow create, update, delete: if false` → il reset client di `HomeBadgeManager.resetRemote` **non funziona** (`PERMISSION_DENIED`). Lo "zero counter" è imposto solo dalle Cloud Functions. Lato client si tiene per ridurre la finestra di flicker locale
- `families/{fid}/members/{uid}` ha rule `create, update, delete: if isSignedIn() && (uid == request.auth.uid || isOwner(fid))`
- `families/{fid}/invites/{inviteId}`: write praticamente libera per qualsiasi user autenticato (con `update` ristretto a `usedAt`/`usedBy`)
- Catch-all `families/{fid}/{subpath=**}: r/w per isMemberOrOwner` copre tutte le sottocollezioni payload

### 6.7 Authentication

#### Provider abilitati

```7:14:app/src/main/java/it/vittorioscocca/kidbox/data/remote/auth/AuthTypes.kt
enum class AuthProvider {
    APPLE,
    GOOGLE,
    FACEBOOK,
}
```

Email/password gestiti **fuori dalla facade**, direttamente da `EmailAuthService.kt`.

| Provider | Classe Service | Meccanismo |
|---|---|---|
| Google | `GoogleAuthService` | `androidx.credentials.CredentialManager` + `GetGoogleIdOption` → `GoogleAuthProvider.getCredential(idToken, null)` |
| Apple | `FirebaseAppleAuthService` | `OAuthProvider.newBuilder("apple.com")` + nonce/SHA-256 hash |
| Facebook | `FacebookAuthService` | Facebook SDK `LoginManager` + `CallbackManager` |
| Email/password | `EmailAuthService` | `signInWithEmailAndPassword`, `createUserWithEmailAndPassword`, `sendEmailVerification`, `sendPasswordResetEmail` |

#### UI login

`ui/screens/auth/LoginScreen.kt`:

- Background `#F2F0EB`, logo `kidbox_symbol_orange`, sottotitolo "La tua famiglia, in un'unica app."
- Tre `SocialLoginButton` neri (Apple, Google, Facebook), divider "o", outline "Continua con email" che apre un `ModalBottomSheet` con `EmailAuthSheetContent`
- Footer Termini/Privacy, loading overlay, `LaunchedEffect(authCheckState)` su `Authenticated` chiama `onLoginSuccess(hasFamily)` con `delay(500)`

Il `LoginViewModel.checkHasFamilyOnce()` ha tripla fallback:
1. `KBFamilyDao.observeAll()` locale
2. Firestore: `users/{uid}/memberships`
3. `collectionGroup("members").whereEqualTo("userId", uid)` (resilienza al bug di sincronizzazione)

Dopo i cambi di account: `FirebaseFirestore.getInstance().terminate().await()` + `clearPersistence().await()` per scartare la cache di Firestore del vecchio utente e prevenire `PERMISSION_DENIED` transienti.

#### LogoutUseCase

```27:35:app/src/main/java/it/vittorioscocca/kidbox/domain/auth/LogoutUseCase.kt
suspend fun logout() {
    membershipSyncService.stop()
    FirebaseAuth.getInstance().signOut()
    withContext(Dispatchers.IO) {
        database.clearAllTables()
    }
    familySessionPreferences.clearActiveFamilyId()
    familyMemoryService.clearFirestoreLoadCache()
}
```

⚠️ **Non viene cancellato il documento `users/{uid}/fcmTokens/{token}`** al logout — push consegnate al dispositivo dopo logout finché il token non ruota.

#### ID token e Cloud Functions

Nessun interceptor OkHttp/Retrofit. Client Android usa **esclusivamente Firebase Functions SDK** (`FirebaseFunctions.getHttpsCallable("...").call(payload)`), che aggiunge automaticamente:
- `Authorization: Bearer <id_token>` (preso da `FirebaseAuth.currentUser`)
- `X-Firebase-AppCheck: <appcheck_token>`

### 6.8 FCM e notifiche

#### `KidBoxFirebaseMessagingService.kt`

**`onNewToken(token)`** — salva su `users/{uid}/fcmTokens/{token}` con `{token, platform: "android", updatedAt}`.

**`onMessageReceived(remoteMessage)`** — estrae `type` da `data["type"] | data["deep_link"] | data["route"]`, costruisce il titolo (con override `chat_mention` → `"$senderName ti ha menzionato"`), e chiama `showNotification(...)`:
- incrementa `NotificationBadgeStore` (cap 9999)
- costruisce `Intent` per `MainActivity` con tutti gli extras di deep link (`push_type`, `push_family_id`, `push_child_id`, `push_doc_id`, `push_note_id`, …)
- crea `NotificationCompat.Builder(this, CHANNEL_ID_FAMILY_UPDATES)` con `BigTextStyle`, `PRIORITY_HIGH`, `setNumber(unreadCount)`, `BADGE_ICON_SMALL`, `CATEGORY_MESSAGE`, `VISIBILITY_PUBLIC`

#### Canali

```125:128:app/src/main/java/it/vittorioscocca/kidbox/notifications/KidBoxFirebaseMessagingService.kt
const val CHANNEL_ID_FAMILY_UPDATES = "family_updates_v2"
private const val CHANNEL_ID_LEGACY = "family_updates"
```

`createNotificationChannels(context)` (chiamato da `KidBoxApplication.onCreate()`) elimina il canale legacy `family_updates` (aveva `lockscreenVisibility` errata) e crea `family_updates_v2` con `IMPORTANCE_HIGH`, `enableLights`, `enableVibration`, `VISIBILITY_PUBLIC`. **Un solo canale attivo** per tutte le notifiche.

#### `NotificationDeepLinkRouter.kt`

Singleton object con tre `StateFlow` pubblici:
- `pendingRoute: StateFlow<String?>` — route Compose da navigare
- `pendingFamilyId: StateFlow<String?>` — famiglia target (richiede switch *prima* di navigare)
- `pendingChatMessageId: StateFlow<String?>` — per scroll/highlight su menzioni chat

`handleLaunchIntent(context, intent)` mappa `push_type`:

| `type` | Destinazione | Extras |
|---|---|---|
| `daily_briefing`, `weekly_summary`, `health_pattern` | `AiChat` | salva `fullText` in draft store |
| `geofenceEvent`, `location_sharing_started/stopped` | `FamilyLocation` | — |
| `new_chat_message`, `chat_mention` | `Chat` | `push_message_id` |
| `new_document` | `DocumentsHome(highlight)` | `docId` |
| `todo_reminder/assigned/reassigned/due_changed` | `TodoList` | `childId`, `listId`, `todoId` |
| `new_grocery_item` | `ShoppingList` | — |
| `new_note` | `NoteDetail` | `noteId` |
| `new_calendar_event`, `calendar_event` | `Calendar` | — |
| `visit_reminder` | `MedicalVisitDetail` | `childId`, `visitId` |
| `treatment_reminder` | `TreatmentDetail` | `childId`, `treatmentId` |
| `exam_reminder` | `MedicalExamDetail` | `childId`, `examId` |
| `new_expense` | `ExpensesHome(highlight)` | `expenseId` |
| `new_wallet_ticket`, `wallet_ticket_reminder` | `WalletDetail` | `ticketId` |
| `password_expiry_reminder` | `PasswordDetail` | `entryId` |
| `password_security_summary` | `PasswordsSecurity` | — |

Il `familyId` viene risolto in cascata: `push_family_id` → `familyId` → SharedPreferences `kidbox_prefs.active_family_id`.

#### `HomeBadgeManager` + `CounterField`

Listener real-time su `families/{fid}/counters/{uid}`. Espone `StateFlow<HomeBadges>` con 10 contatori (`chat`, `documents`, `photos`, `location`, `todos`, `shopping`, `notes`, `calendar`, `expenses`, `wallet`). `enum class CounterField(val raw: String)` mappa 1:1 le chiavi Firestore.

Operazioni: `startListening(familyId)` (idempotente), `clearLocal(field)` (zero ottimistico), `resetRemote(familyId, field)` (rifiutato dalle rules — vedi §6.6).

### 6.9 Cloud Functions / AI Endpoints

**Nessun Retrofit/Ktor/OkHttp custom** per le Functions. Solo `firebase-functions-ktx`. DI in `BillingModule`:

```31:34:app/src/main/java/it/vittorioscocca/kidbox/di/BillingModule.kt
@Provides
@Singleton
fun provideFirebaseFunctions(): FirebaseFunctions =
    FirebaseFunctions.getInstance("europe-west1")
```

⚠️ **Divergenza**: la maggior parte dei file istanzia un proprio `FirebaseFunctions.getInstance("europe-west1")` invece di iniettare quello del `BillingModule`.

#### Callable invocate da Android

| Callable | Caller Kotlin | Scopo |
|---|---|---|
| `askAI` | `AiRepository`, `AIService` | Chat AI; payload `{familyId, systemPrompt, messages[], purpose?}`. Per `purpose == "clinicalRecord"` timeout 120s |
| `getAIUsage` | `AIService` | Quota AI (usage/limit) |
| `suggestTravelDestinations` | `AIService` | AI suggerimenti viaggio |
| `generateTravelPlan` | `AIService` | AI itinerario completo |
| `searchTravelDestinations` | `TravelPlaceSearchService` | Ricerca testuale luoghi |
| `getTravelPlaceDetails` | `TravelPlacesService` | Dettaglio singolo POI |
| `getStorageUsage` | `ProfileViewModel`, `StorageUsageViewModel`, `ShareActionHandler` | Telemetria storage |
| `analyzeLogs` | `CrashAnalyzer` | Analisi LLM crash log |
| `deleteAccount` | `ProfileViewModel` | Cancellazione account |
| `deleteFamily` | `FamilyLeaveService` | Cancellazione famiglia |
| `updatePlan` | `SubscriptionRepositoryImpl` | Aggiornamento subscription post-purchase |

#### Sistema messaggi / token AI

KidBox astrae i token Anthropic dietro un'unità chiamata **"messaggio"**. L'utente vede un budget giornaliero (Pro 30, Max 100, Free 0); il server traduce ogni richiesta in N unità e le scala dal contatore famiglia `ai_usage/family_{fid}/daily/{day}.count`.

Le unità si basano sui **caratteri del payload**, non sui token reali (`data/remote/ai/AIAskAIPayload.kt`):

| Costante | Valore | Significato |
|---|---|---|
| `STANDARD_CHARS` | 50.000 | 1 unità = 50k caratteri (system + storico + nuovo testo) |
| `ABSOLUTE_MAX_CHARS` | 500.000 | hard limit anti-abuso |
| `CLINICAL_RECORD_MIN_UNITS` | 3 | minimo fisso cartella clinica (Sonnet ~3× Haiku, no caching) |

`messageUnits(totalChars) = max(1, ceil(totalChars / 50.000))`; cartella clinica → `clinicalRecordMessageUnits = max(3, messageUnits)`.

**Flusso**: il client pre-stima le unità (`ClinicalRecordAISynthesizer.estimatePayload` → `ClinicalRecordOrchestrator.estimateAIMessageUnits`; chat: `HealthAIChatViewModel`) e blocca prima dell'invio se eccedono il rimanente. Il server (`functions/index.js` `askAI`, **condiviso con iOS**) ricalcola e incrementa atomicamente; scrive il costo USD reale su `ai_costs`.

**Modelli per purpose**: `clinicalRecord` → **Sonnet 4.5** (min 3 unità); `support` (`SupportViewModel`, `PURPOSE_SUPPORT = "support"`), chat salute/visite/esami e default → **Haiku 4.5**.

> ⚠️ **Parity obbligatoria**: `STANDARD_CHARS` (50k), `CLINICAL_RECORD_MIN_UNITS` (3), `ABSOLUTE_MAX_CHARS` (500k) sono duplicati in Android (`AIAskAIPayload.kt`), iOS (`AIAskAIPayload.swift`) e server (`functions/index.js`). Cambiando un valore, **allinea tutti e tre**.

### 6.10 Firebase Storage

#### Layout dei path

| Path Storage | File responsabile |
|---|---|
| `families/{fid}/documents/{docId}/{file}.kbenc` | `DocumentStorageManager.uploadEncrypted` |
| `families/{fid}/photos/{photoId}/original.enc` | `PhotoVideoStorageManager` |
| `families/{fid}/chat/{messageId}/{safeName}` | `ChatStorageService.upload` (**plain**, non cifrato) |
| `families/{fid}/wallet/{ticketId}/ticket.pdf.kbenc` | `WalletRepository` |
| `families/{fid}/hero/hero.jpg` | `FamilyHeroPhotoService` |
| `families/{fid}/avatars/{uid}.jpg` | `AvatarRemoteStore` |
| `users/{uid}/avatar.jpg` | `AvatarRemoteStore` |

Tutti i path Storage rispecchiano l'albero Firestore. Niente path "flat" o con UUID arbitrari.

#### Crittografia

**Algoritmo unificato per documenti/wallet/photos/notes/passwords**: **AES-GCM 256** con nonce 12 byte e tag 16 byte.

**`DocumentCryptoManager`**:
- Cipher: `"AES/GCM/NoPadding"`
- Chiave: 32 byte recuperata da `FamilyKeyStore`
- **Output write**: iOS CryptoKit combined → `[12-byte nonce][ciphertext][16-byte tag]`
- **Input read**: dual-mode compat layer (supporta sia file legacy Android `[4-byte ivSize][iv][cipher+tag]` sia formato CryptoKit iOS)

```34:41:app/src/main/java/it/vittorioscocca/kidbox/data/remote/DocumentCryptoManager.kt
// iOS CryptoKit combined format: nonce(12) + ciphertext + tag(16)
val out = ByteBuffer.allocate(iv.size + encrypted.size)
    .put(iv)
    .put(encrypted)
    .array()
```

**`FamilyKeyStore`**:
- File `kidbox_family_keys` con `EncryptedSharedPreferences` (Android Keystore, `AES256_GCM` value + `AES256_SIV` key)
- Fallback `kidbox_family_keys_fallback` in plain SharedPreferences se EncryptedSharedPreferences fallisce
- Chiave: `fk_{familyId}_{userId}`

**`InviteCrypto`** (allineato 1:1 con iOS `InviteCrypto.swift`):
- `randomBytes(32)` per `secret`
- `sha256Base64(secret)` per `secretHash`
- `deriveWrapKey(secret, salt, familyId)` con HKDF-SHA256 manuale (Android < API 33 non ha HKDF nativo). Info: `"kidbox-wrap:{familyId}"`
- `deriveEscrowWrapKey(userId, familyId)` con HKDF-SHA256, salt costante `"kidbox-escrow-salt-2026"`, info `"kidbox-key-escrow-v1:{userId}:{familyId}"`
- `wrapFamilyKey/unwrapFamilyKey` AES-GCM

**Schema invite cifrato** su `families/{fid}/invites/{inviteId}`:
```text
{ secretHash, kdfSalt, wrappedKeyCipher, wrappedKeyNonce, wrappedKeyTag,
  createdAt, expiresAt, usedAt?, usedBy? }
```

QR payload Android: `kidbox://join?familyId=...&inviteId=...&secret=<Base64URL>&code=<flat_code>`.

**`FamilyKeyEscrow`** — backup/recovery su `families/{fid}/memberKeyBackups/{userId}` cifrato con `deriveEscrowWrapKey(userId, familyId)`. Permette ri-installare l'app e recuperare automaticamente le chiavi senza re-scan QR.

#### App Check

- `AppCheckTokenCache` è un singleton con TTL 55 minuti e cooldown 30 minuti su `403`/`PERMISSION_DENIED`
- `FirebaseStorageUploadExtras` aggiunge retry e gestione errori specifici

⚠️ **Anomalia critica**: `AppCheckInstaller.install()` esiste in due varianti (`app/src/debug/.../` con `DebugAppCheckProviderFactory`, `app/src/release/.../` con `PlayIntegrityAppCheckProviderFactory`) ma **non viene mai chiamato** in `KidBoxApplication.onCreate()`. Se in Firebase Console viene attivato l'**enforcement** App Check su Storage/Functions, tutte le richieste Android falliranno con `403`.

### 6.11 Divergenze cross-platform da verificare

| # | Area | Problema | File Android |
|---|---|---|---|
| 1 | Storage / App Check | `AppCheckInstaller.install()` mai chiamato | `KidBoxApplication.kt` |
| 2 | Firestore | `WalletRemoteStore` perde i millisecondi nel `Timestamp` | `WalletRemoteStore.kt` |
| 3 | Firestore | `PhotoVideoRemoteStore` legge `createdAt/updatedAt` come `Long` (non `Timestamp`) | `PhotoVideoRemoteStore.kt` |
| 4 | Firestore | `DocumentRemoteStore` fallback `categoryId` → `parentId` → `folderId` | `DocumentRemoteStore.kt` |
| 5 | Storage / Chat | `chat/{messageId}` upload **in chiaro** | `ChatStorageService.kt` |
| 6 | Firestore | `counters/{uid}` `.set(...)` lato client rifiutato dalle rules | `HomeBadgeManager.kt`, `firestore.rules` |
| 7 | FCM | Logout non cancella `users/{uid}/fcmTokens/{token}` | `LogoutUseCase.kt` |
| 8 | DI | `FirebaseFunctions.getInstance("europe-west1")` duplicato in 10+ file | molti |
| 9 | Auth | `EmailAuthProvider` non passa dal `AuthFacade` | `LoginViewModel.kt` |

---

## 7. Navigazione

Libreria: **Compose Navigation** (`androidx.navigation.compose.NavHost` + `composable(...)`).

### 7.1 File centrali

- `ui/navigation/AppDestination.kt` (494 righe) — `sealed class AppDestination(val route: String)` con **~85 destinazioni** come `data object` annidati
- `ui/navigation/AppNavGraph.kt` (1826 righe) — `@Composable fun AppNavGraph(navController, startDestination, onboardingPreferences)` con tutti i `composable()` block
- `ui/navigation/NavBackStack.kt` — extension `popBackToHome()` (workaround per pop-back doppio su Samsung)
- `ui/navigation/KidBoxDeepLinkMessages.kt` — costanti stringa
- `ui/navigation/NavControllerTravelPlanning.kt` — helper di nav specifico per travel

### 7.2 Tre forme tipiche di destinazione

**1) Senza argomenti**:

```10:13:app/src/main/java/it/vittorioscocca/kidbox/ui/navigation/AppDestination.kt
data object Home : AppDestination("home")
data object Profile : AppDestination("profile")
data object Settings : AppDestination("settings")
```

**2) Con un argomento di path + builder typesafe**:

```25:27:app/src/main/java/it/vittorioscocca/kidbox/ui/navigation/AppDestination.kt
data object EditChild : AppDestination("edit_child/{childId}") {
    fun createRoute(childId: String): String = "edit_child/$childId"
}
```

**3) Con query string + URLEncoder** (per stringhe con spazi):

```28:38:app/src/main/java/it/vittorioscocca/kidbox/ui/navigation/AppDestination.kt
data object FamilyPhotos : AppDestination("family_photos/{familyId}?initialAlbumId={initialAlbumId}") {
    fun createRoute(familyId: String, initialAlbumId: String? = null): String {
        val base = "family_photos/$familyId"
        return if (initialAlbumId.isNullOrBlank()) base
        else {
            val enc = java.net.URLEncoder.encode(initialAlbumId, Charsets.UTF_8.name())
            "$base?initialAlbumId=$enc"
        }
    }
}
```

Convenzione naming: nelle health screens si usa `route()` invece di `createRoute()` (es. `HealthHome.route(familyId, childId)`).

### 7.3 Tipi argomento (`NavType`)

Tutti gli argomenti dichiarati esplicitamente nel `composable(route, arguments = ...)`:
- `NavType.StringType` per stringhe
- `NavType.BoolType` per boolean (`isTripAlbum`, `isNewNote`, `isListMode`)
- Argomenti opzionali → `nullable = true; defaultValue = null`

### 7.4 Ordine di registrazione critico

Diversi composable hanno commenti `//` importanti perché il match nav-compose è lineare:
- `MedicalVisitForm` registrata **prima** di `MedicalVisitDetail` altrimenti il segmento letterale `form` matcha come `{visitId}`
- `VisitsListAiChat` prima di `MedicalVisitDetail`
- Stesso pattern per `ExamsListAiChat`, `ExamAiChat`, `MedicalExamForm`, `TreatmentForm`

### 7.5 Niente bottom navigation / tab bar

**Non c'è** una bottom bar o tab bar globale (grep `NavigationBar|BottomNavigation|BottomAppBar` ha solo match in `Theme.kt` per le system bars). L'unica schermata "hub" è `HomeScreen.kt` con `LazyVerticalGrid` + `FloatingActionButton` per quick-actions + drag-and-drop riordino. Tutte le altre schermate hanno una topbar con `KidBoxHeaderCircleButton` (back) e sono raggiunte navigando via `onNavigate = { route -> navController.navigate(route) }` passato dalla Home/Settings.

### 7.6 DeepLink — gestiti **fuori** da Navigation Compose

`composable(deepLinks = ...)` **non è usato** (grep `deepLinks =|navDeepLink` → 0 match). Il manifest registra solo `MainActivity` come `LAUNCHER` + un filtro `ACTION_SEND` per PDF.

Le notifiche FCM/locali sono instradate da un **object singleton**, `notifications/NotificationDeepLinkRouter.kt`:

1. `MainActivity.onCreate` e `onNewIntent` chiamano `NotificationDeepLinkRouter.handleLaunchIntent(this, intent)`
2. Il router estrae `push_type` dagli extras e mappa ogni tipo nella `AppDestination` corretta
3. Espone tre `StateFlow` (`pendingRoute`, `pendingFamilyId`, `pendingChatMessageId`)
4. `AppNavGraph.kt:121-142` osserva questi flow con `LaunchedEffect`: se `pendingFamilyId != activeFamilyId`, prima invoca `familySwitcherVm.switchToFamily(...)` e **defer** la navigazione; altrimenti `navController.navigate(route) { launchSingleTop = true }` e poi `NotificationDeepLinkRouter.clear()`
5. Caso speciale chat: `composable(AppDestination.Chat.route)` osserva `pendingChatMessageId` con `LaunchedEffect` e chiama `chatViewModel.highlightMessage(id)`

### 7.7 Helper di navigazione

- **Back/Up**: ovunque `navController.popBackStack()`. Per uscire da una sotto-flow direttamente alla Home si usa `popBackToHome()`. **`navigateUp()` non usato**
- **`popUpTo(...) { inclusive = ... }`** per reset stack al login/onboarding/leave-family
- **`launchSingleTop = true`** per Profile/Settings/Plans/SupportChat/Chat per evitare doppi push
- **Condivisione del ViewModel tra due composable**: pattern `navController.getBackStackEntry(parentRoute)` + `hiltViewModel(parentEntry)`. Usato per `Chat`/`ChatMediaGallery` e per `TravelDiscover`/`TravelDestinationDetail`
- **Restart app post leave-family**: `packageManager.getLaunchIntentForPackage(...)` con `FLAG_ACTIVITY_CLEAR_TASK | NEW_TASK | CLEAR_TOP` + `Activity.finish()`

---

## 8. Modelli dati (3 layer)

### 8.1 Domain models — `domain/model/`

`data class` pure, **senza dipendenze Room né Firebase**. Tutti i timestamp in **epoch millis Long**. Visibilità membri come `List<String>` di uid. Tutti i tipi enum-like sono serializzati come `*Raw: String/Int` per cross-platform.

| File | Campi chiave |
|---|---|
| `KBFamily.kt` | `id`, `name`, `heroPhotoURL?`, `heroPhotoUpdatedAtEpochMillis?`, hero scale/offset, audit, `lastSyncAt/Error` |
| `KBFamilyMember.kt` | `id`, `familyId`, `userId`, `role` (`owner`/`member`), `displayName?`, `email?`, `photoURL?` |
| `KBChild.kt` | `id`, `familyId?`, `name`, `birthDateEpochMillis?`, `weightKg?`, `heightCm?` |
| `KBNote.kt` | `title`, `body` (HTML), `visibilityScope` (default `FAMILY`), `visibilityMemberIds: List<String>`, audit, sync. Helper `isVisibleTo(currentUid)` |
| `KBDocument.kt` | `localPath?`, `title`, `fileName`, `mimeType`, `fileSize`, `storagePath`, `downloadURL?`, `extractedText?`, `extractionStatusRaw: Int`, visibility, sync |
| `KBDocumentCategory.kt` | `title`, `sortOrder`, `parentId?` (albero) |
| `KBChatMessage.kt` | ~30 campi: `typeRaw`, `text?`, `latitude?`, `longitude?`, `mediaStoragePath?`, `mediaURL?`, `mediaThumbnailURL?`, `replyToId?`, `mediaGroupURLsJSON?` (max 10), `reactionsJSON?`, `readByJSON?`, `mentionsJSON?`, `contactPayloadJSON?`, transcript* |
| `KBCalendarEvent.kt` | `title`, `notes?`, `location?`, `startDate/endDateEpochMillis`, `isAllDay`, `categoryRaw` (`KBEventCategory`: CHILDREN/SCHOOL/HEALTH/FAMILY/ADMIN/LEISURE), `recurrenceRaw` (NONE/DAILY/WEEKLY/MONTHLY/YEARLY), `reminderMinutes?`, `linkedHealthItemId?` |
| `KBEvent.kt` | generico evento figlio (non calendar) |
| `KBExpense.kt` | `title`, `amount: Double`, `dateEpochMillis`, `categoryId?`, `attachedDocumentId?`, `receiptThumbnailData: ByteArray?` |
| `KBExpenseCategory.kt` | `name`, `icon`, `colorHex`, `isDefault`, `sortIndex` |
| `KBGroceryItem.kt` | `name`, `category?`, `notes?`, `isPurchased`, `purchasedAt/By` |
| `KBTodoItem.kt` / `KBTodoList.kt` | todo per bambino: `priorityRaw?`, `dueAtEpochMillis?`, `isDone`, `assignedTo?`, `reminderEnabled`, `reminderId?`, visibility |
| `KBRoutine.kt` / `KBRoutineCheck.kt` | routine giornaliere (`dayKey: String`, `checkedBy`) |
| `KBCustodySchedule.kt` | template settimanale custodia (`weekTemplateJSON`) |
| `KBFamilyPhoto.kt` / `KBPhotoAlbum.kt` | foto/video crittografati: `storagePath`, `downloadURL?`, `localPath?`, `thumbnailBase64?`, `caption?`, `takenAtEpochMillis`, `albumIdsRaw` |
| `KBPediatricProfile.kt` | `id == childId`; `emergencyContactsJson?`, `bloodGroup?`, `allergies?`, `doctorName/Phone/…?`, `doctorOfficeHoursJson?` |
| `KBEmergencyContact.kt` | `name`, `relation`, `phone` (embedded JSON) |
| `KBDoctorOfficeHourSlot.kt` | `weekday` (italiano), `fromTime`, `toTime`. + `object KBItalianWeekdays`, `ReferenceDoctorDraft` |
| `KBMedicalVisit.kt` | sub-strutture serializzate `*Json` (`travelDetailsJson`, `linkedTreatmentIdsJson`, `linkedExamIdsJson`, `asNeededDrugsJson`, `prescribedExamsJson`, `photoUrlsJson`), `visitStatusRaw`, `doctorSpecializationRaw` |
| `KBMedicalExam.kt` | `statusRaw`, `resultText?`, `resultDateEpochMillis?`, `prescribingVisitId?`, `reminderOn` |
| `KBVaccine.kt` | `vaccineTypeRaw`, `statusRaw`, `doseNumber`/`totalDoses`, `administered/scheduledDateEpochMillis?` |
| `KBTreatment.kt` | terapia pediatrica e animale (via `petId`). `prescribingVisitId?`, `dosageValue/Unit`, `isLongTerm`, `durationDays`, `dailyFrequency`, `intervalBetweenDosesDays` (cura ogni N giorni), `scheduleTimesData` (CSV `HH:mm`), `isActive` |
| `KBDoseLog.kt` | `treatmentId`, `dayNumber`, `slotIndex`, `scheduledTime`, `takenAtEpochMillis?`, `taken` |
| `KBCustomDrug.kt` | catalogo locale farmaci (no `familyId`, no sync) |
| `KBAIConversation.kt` / `KBAIMessage.kt` | `scopeId` unico, `summary?`, `summarizedMessageCount`, `roleRaw`, `isSummary` |
| `KBSyncState.kt` | enum `rawValue: Int`: SYNCED(0), PENDING_UPSERT(1), PENDING_DELETE(2), ERROR(3) |
| `KBVisibilityScope.kt` | **object** con costanti `FAMILY`, `MEMBERS`, `ONLY_CREATOR = "private"` + helper `normalized()`, `chipLabel()`, `isVisible(scope, memberIds, createdBy, currentUid)` |
| `KBPlan.kt` | enum FREE/PRO/MAX: `storageQuota` (200 MB / 5 GB / 20 GB), `aiDailyLimit` (0/30/100), `productId` IAP, `monthlyPrice`, `badge` |
| `WalletTicketKind.kt` | FLIGHT/TRAIN/FERRY/BUS/CONCERT/CINEMA/PARKING/MUSEUM/OTHER + gradient hex |
| `KidBoxEnums.kt` | enum trasversali: `KBTextExtractionStatus`, `KBEventCategory`, `KBEventRecurrence`, `KBExamStatus`, `VaccineType/Status`, `KBChatMessageType`, `KBTranscriptStatus/Source` |
| `KidBoxHealthEmbedded.kt` | `KBDoctorSpecialization`, `KBTherapyType`, `KBVisitStatus`, `KBPrescribedExam`, `KBAsNeededDrug`, `KBTravelDetails` |
| `HealthImportSnapshot.kt` | snapshot Health Connect: `birthDate`, `weightKg`, `bloodGroup`, `heartRateBpm`, `recentHeartRates`, `recentDailyActivity`, `recentWorkouts`, `recentECGs`, `restingHeartRateAvg90d`, `vo2Max`, `hrvSdnnMsAvg90d` |
| `HealthTimelineEvent.kt` | wrapper unificato VISIT/EXAM/TREATMENT/VACCINE + gradient |
| `TodoListExposure.kt` | regola visibilità lista todo |
| `TreatmentSchedulePeriod.kt` | enum fascia oraria MATTINA (06-11:59), PRANZO (12-15:59), SERA (16-21:59), NOTTE (22-05:59) |
| `ai/AIModels.kt` | `AIProvider` (CLAUDE/OPENAI), `AIMessageRole`, `AIResponse(reply, usageToday, dailyLimit)`, sealed `AIServiceError` |

### 8.2 Local entities (Room) — `data/local/entity/`

Tutte annotate `@Entity(tableName = ..., foreignKeys = [...], indices = [...])`. Convenzione: prefisso `kb_` per il "mondo KidBox", senza prefisso per moduli più recenti (pets, vehicles, home_items, house_payments, password_entries, password_groups, pwned_prefix_cache).

Le 49 entity registrate (`KidBoxDatabase.kt:102-155`):

| Entity | tableName | FK | Indici |
|---|---|---|---|
| `KBFamilyEntity` | `kb_families` | — | `updatedAtEpochMillis` |
| `KBFamilyMemberEntity` | `kb_family_members` | `familyId→kb_families CASCADE` | `familyId`, `userId` |
| `KBChildEntity` | `kb_children` | `familyId→kb_families SET NULL` | `familyId` |
| `KBUserProfileEntity` | `kb_user_profiles` | — | — |
| `KBNoteEntity` | `kb_notes` | `familyId CASCADE` | `familyId` |
| `KBDocumentEntity` | `kb_documents` | `familyId CASCADE`, `categoryId→kb_document_categories SET NULL` | `familyId`, `childId`, `categoryId` |
| `KBDocumentCategoryEntity` | `kb_document_categories` | `familyId CASCADE`, self-FK `parentId SET NULL` | `familyId`, `parentId` |
| `KBChatMessageEntity` | `kb_chat_messages` | `familyId CASCADE` | `familyId`, `senderId`, `createdAtEpochMillis`, `replyToId` |
| `KBCalendarEventEntity` | `kb_calendar_events` | `familyId CASCADE`, `childId SET NULL` | `familyId`, `childId`, `startDateEpochMillis` |
| `KBEventEntity` | `kb_events` | `familyId CASCADE` | `familyId`, `childId` |
| `KBCustodyScheduleEntity` | `kb_custody_schedules` | `familyId CASCADE` | `familyId`, `childId` |
| `KBRoutineEntity` | `kb_routines` | `familyId CASCADE` | `familyId`, `childId` |
| `KBRoutineCheckEntity` | `kb_routine_checks` | `familyId CASCADE`, `routineId CASCADE` | `familyId`, `routineId`, `dayKey` |
| `KBTodoListEntity` | `kb_todo_lists` | `familyId CASCADE`, `childId CASCADE` | `familyId`, `childId` |
| `KBTodoItemEntity` | `kb_todo_items` | `familyId CASCADE`, `childId CASCADE`, `listId SET NULL` | `familyId`, `childId`, `listId` |
| `KBGroceryItemEntity` | `kb_grocery_items` | `familyId CASCADE` | `familyId` |
| `KBExpenseEntity` | `kb_expenses` | `familyId CASCADE`, `categoryId SET NULL`, `attachedDocumentId SET NULL` | `familyId`, `categoryId`, `attachedDocumentId`, `dateEpochMillis` |
| `KBExpenseCategoryEntity` | `kb_expense_categories` | `familyId CASCADE` | `familyId` |
| `KBFamilyPhotoEntity` | `kb_family_photos` | `familyId CASCADE` | `familyId` |
| `KBPhotoAlbumEntity` | `kb_photo_albums` | `familyId CASCADE`, `coverPhotoId SET NULL` | `familyId`, `coverPhotoId` |
| `KBMedicalVisitEntity` | `kb_medical_visits` | `familyId CASCADE` | `familyId`, `childId`, `dateEpochMillis` |
| `KBMedicalExamEntity` | `kb_medical_exams` | `familyId CASCADE`, `prescribingVisitId SET NULL` | `familyId`, `childId`, `prescribingVisitId` |
| `KBPediatricProfileEntity` | `kb_pediatric_profiles` | `familyId CASCADE`, `childId CASCADE` | `familyId`, `childId` |
| `KBVaccineEntity` | `kb_vaccines` | `familyId CASCADE` | `familyId`, `childId` |
| `KBTreatmentEntity` | `kb_treatments` | `familyId CASCADE` | `familyId`, `childId`, `petId`, `prescribingVisitId` |
| `KBCustomDrugEntity` | `kb_custom_drugs` | — | — |
| `KBDoseLogEntity` | `kb_dose_logs` | `familyId CASCADE`, `treatmentId CASCADE` | `familyId`, `childId`, `treatmentId` |
| `KBSharedLocationEntity` | `kb_shared_locations` | `familyId CASCADE` | `familyId`, `userId` |
| `KBGeofenceEntity` | `kb_geofences` | `familyId CASCADE` | `familyId` |
| `KBAIConversationEntity` | `kb_ai_conversations` | — | `familyId`, `childId`, **unique** `scopeId` |
| `KBAIMessageEntity` | `kb_ai_messages` | — | `conversationId`, `createdAtEpochMillis` |
| `KBMemoryFactEntity` | `kb_memory_facts` | — | `familyId` |
| `KBHealthInsightEntity` | `kb_health_insights` | — | `familyId` (`monthKey`, `isRead`) |
| `KBWalletTicketEntity` | `kb_wallet_tickets` | `familyId CASCADE` | `familyId`, `(familyId, isDeleted)` |
| `PetEntity` | `pets` | `familyId CASCADE` | `familyId` |
| `PetEventEntity` | `pet_events` | `familyId CASCADE` | `familyId`, `(familyId, petId)` |
| `HomeItemEntity` | `home_items` | `familyId CASCADE` | `familyId` |
| `HousePaymentEntity` | `house_payments` | `familyId CASCADE` | `familyId` |
| `VehicleEntity` | `vehicles` | `familyId CASCADE` | `familyId` |
| `VehicleEventEntity` | `vehicle_events` | `familyId CASCADE` | `familyId`, `(familyId, vehicleId)` |
| `PasswordEntryEntity` | `password_entries` | `familyId CASCADE` | `familyId`, `(familyId, updatedAtEpochMillis)` |
| `PasswordGroupEntity` | `password_groups` | `familyId CASCADE` | `familyId` |
| `PwnedPrefixCacheEntity` | `pwned_prefix_cache` | — | PK `prefix` |
| `KBTripEntity` | `kb_trips` | — | `familyId`, `startDateEpoch` |
| `KBTripLegEntity` | `kb_trip_legs` | `tripId CASCADE` | `tripId`, `familyId` |
| `KBTripDayPlanEntity` | `kb_trip_day_plans` | `tripId CASCADE` | `tripId`, `familyId` |
| `KBTripExpenseEntity` | `kb_trip_expenses` | `tripId CASCADE` | `tripId`, `familyId` |
| `KBPackingItemEntity` | `kb_packing_items` | `tripId CASCADE` | `tripId`, `familyId` |
| `SupportTicketEntity` | `support_tickets_local` | — | — |

I `PasswordEntryEntity` hanno tutti i campi sensibili come `ByteArray` (`titleCipher`, `passwordCipher`, `usernameCipher?`, `notesCipher?`, `otpConfigCipher?`, `websiteCipher?`) + `pwnedCount?`, `pwnedCheckedAt?`, `isFavorite`, `visibility`, `visibilityMemberIdsJson`.

Esempio canonico DAO `KBNoteDao`:

```16:31:app/src/main/java/it/vittorioscocca/kidbox/data/local/dao/KBNoteDao.kt
    @Query("SELECT * FROM kb_notes WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<KBNoteEntity?>

    @Query("SELECT * FROM kb_notes WHERE familyId = :familyId AND isDeleted = 0 ORDER BY updatedAtEpochMillis DESC")
    fun observeByFamilyId(familyId: String): Flow<List<KBNoteEntity>>

    @Query("SELECT * FROM kb_notes WHERE familyId = :familyId AND syncStateRaw = :syncStateRaw")
    suspend fun getBySyncState(familyId: String, syncStateRaw: Int): List<KBNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KBNoteEntity)

    @Query("DELETE FROM kb_notes WHERE id = :id")
    suspend fun deleteById(id: String)
```

**Eccezione importante**: `KBFamilyDao` è `abstract class` e implementa `upsert` come `@Transaction { updateInternal(...) → insertIgnore(...) }` per evitare il DELETE+INSERT che innescava `ON DELETE CASCADE` sui `kb_family_members`. Stesso pattern in `KBFamilyMemberDao`.

`KBDocumentDao` ha query particolari:
- `observeRootVisibleByFamilyId` — esclude documenti spesa orfani e file con titolo `document:` (artefatti URL-encoded)
- `getHealthDocumentsNeedingExtraction` / `getLifeAreaDocumentsNeedingExtraction` — selettori OCR per moduli salute/casa/garage/pet
- `getOrphanedExpenseDocuments` — JOIN per spese con `categoryId` puntante a categorie soft-deleted

### 8.3 Remote DTO — `data/remote/...`

Non esiste cartella `dto/` centralizzata: i DTO sono `data class` dichiarati **nello stesso file del `*RemoteStore`** corrispondente.

Pattern uniforme: ogni `RemoteStore` definisce una **sealed interface `*RemoteChange`** con sub-types `Upsert(dto)` / `Remove(id)`, e il listener invoca `onChange(changes: List<*RemoteChange>)`.

**Payload veri e propri**:
- `data/remote/support/SupportTicketSubmitDto.kt` — DTO `support_tickets`: `id`, `familyId`, `uid`, `userEmail`, `type` (`question`/`bug`/`suggestion`), `title`, `summary`, `conversation`, `imagesBase64` (max 5, ≤ 5 MB ognuna), `appVersion`, `osVersion`, `device`, `rawLogs?`
- `data/remote/support/SupportTicketFirestorePayload.kt` — costruzione e shrinking per stare nel limite Firestore 1 MiB (max 1_048_576 bytes, margine 64 KB, log max 48 KB, immagini JPEG 720px @ q78)
- `data/remote/ai/AIAskAIPayload.kt` — `STANDARD_CHARS = 50_000`, `ABSOLUTE_MAX_CHARS = 500_000`, `CLINICAL_RECORD_MIN_UNITS = 3`; helper `messageUnits(totalChars)` e `clinicalRecordMessageUnits(totalChars)` per il metering AI (vedi §6.9 «Sistema messaggi / token AI»)

### 8.4 Mapper — `data/local/mapper/`

Solo **5 file**, tutti sul modulo salute:
- `MedicalVisitMappers.kt` — `KBMedicalVisitEntity.toDomain(): KBMedicalVisit` + `KBMedicalVisit.toEntity()`. Include JSON helpers top-level (`encodeStringList/decodeStringList` basati su `org.json.JSONArray`, `encode/decodeAsNeededDrugs`, `encode/decodeTherapyTypes`)
- `MedicalExamMappers.kt`, `VaccineMappers.kt`, `TreatmentMappers.kt`, `PediatricProfileMappers.kt` — stesso pattern

Per **tutto il resto** del codebase non esistono mapper centrali: i repository fanno il mapping inline tramite `private fun KBXxxEntity.toDomain(): KBXxx`.

Le liste di stringhe usano tre implementazioni convergenti di `encodeStringList`/`decodeStringList`:
- `util/StringListJson.kt:5-23` — versione "pulita" (trim + distinct, filtra blank)
- `data/local/mapper/MedicalVisitMappers.kt:113-125` — versione meno restrittiva
- `data/repository/PasswordsRepository.kt:155-163` — copia privata per il modulo passwords

### 8.5 Type Converters

**Nessun `@TypeConverters` registrato** in tutto il progetto. Tutte le strutture complesse sono pre-serializzate dai mapper o dai repository:
- `List<String>` → JSON array stringa via `util/StringListJson.kt`
- Timestamp → `Long` epoch millis (campi `*EpochMillis`)
- Strutture annidate → JSON via `org.json.JSONArray/JSONObject`
- `ByteArray` (campi cifrati password) → Room li gestisce nativamente come `BLOB`

### 8.6 Migrations

Definite **inline** nel `DatabaseModule.kt` come `object : Migration(from, to) { override fun migrate(db: SupportSQLiteDatabase) { … } }`. **31 migrations** numerate `MIGRATION_4_5` … `MIGRATION_33_34`, tutte registrate sul builder. Bump version: incrementare `version` in `@Database(...)` e aggiungere la migration corrispondente.

Esempi notevoli:
- `MIGRATION_4_5` — `ALTER TABLE kb_chat_messages ADD COLUMN contactPayloadJSON TEXT` + `deletedForJSON TEXT`
- `MIGRATION_8_9` / `MIGRATION_9_10` — pattern "CREATE _new + INSERT SELECT + DROP + RENAME" per cambi FK su `kb_treatments`, `kb_dose_logs`, `kb_medical_visits`, `kb_medical_exams`, `kb_vaccines`, con `PRAGMA foreign_keys=OFF/ON`
- `MIGRATION_10_11` — rebuild `kb_documents` per rilassare FK su `childId`
- `MIGRATION_14_15` / `15_16` / `16_17` / `17_18` — aggiungono `visibilityScope`/`visibilityMemberIdsJson` su note/todo/calendar/documents/wallet
- `MIGRATION_18_19` — bootstrap moduli `pets`, `pet_events`, `home_items`, `vehicles`, `vehicle_events`
- `MIGRATION_19_20` — `house_payments`
- `MIGRATION_21_22` — `password_entries` + `password_groups`
- `MIGRATION_29_30` — bootstrap travel: `kb_trips`, `kb_trip_legs`, `kb_trip_day_plans`, `kb_trip_expenses`, `kb_packing_items`
- `MIGRATION_32_33` — `kb_geofences`
- `MIGRATION_33_34` — `ALTER TABLE kb_chat_messages ADD COLUMN mentionsJSON TEXT`

`fallbackToDestructiveMigration()` attivo: se si parte da una versione senza migration registrata si perdono i dati locali (politica accettata perché Room è cache; il source of truth remoto è Firestore).

---

## 9. Repository e DataSource

I `*Repository` (32 file in `data/repository/`) sono `@Singleton` Hilt-injected. **Source of truth della UI = Room.** Il listener Firestore (in `RemoteStore` o `SyncCenter`) scrive su Room, e la UI osserva i `Flow<List<Entity>>` del DAO. Strategia Last-Write-Wins su `updatedAtEpochMillis` con guard anti-resurrect sui record `PENDING_*`.

### 9.1 Pattern canonico

```kotlin
@Singleton
class XxxRepository @Inject constructor(
    private val xxxDao: KBXxxDao,        // SSOT locale
    private val familyDao: KBFamilyDao,  // per ensureFamilyExists
    private val remoteStore: XxxRemoteStore,
    private val auth: FirebaseAuth,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inboundMutex = Mutex()      // serialize applyInbound
    private val realtimeMutex = Mutex()     // serialize start/stop
    private var listener: ListenerRegistration? = null

    fun observeByFamilyId(familyId): Flow<List<KBXxx>> =
        xxxDao.observeByFamilyId(familyId).map { it.toDomain() }
    fun observeById(id): Flow<KBXxx?> = xxxDao.observeById(id).map { it?.toDomain() }
    fun startRealtime(familyId, onPermissionDenied) { /* listener = remoteStore.listen(…); flushPending(familyId) */ }
    fun stopRealtime() { /* ... */ }
    suspend fun upsertXxx(...): String { /* optimistic: PENDING_UPSERT → push remoto → SYNCED, ERROR su fallimento */ }
    suspend fun softDelete(familyId, id) { /* PENDING_DELETE → remoteStore.softDelete → deleteById */ }
    private suspend fun applyInbound(changes) { /* inboundMutex.withLock { LWW + anti-resurrect } */ }
    private fun flushPending(familyId) { /* replay record PENDING_* */ }
}
```

### 9.2 Elenco repository

| Path | Dipendenze |
|---|---|
| `AuthRepository.kt` | wrapper su FirebaseAuth |
| `FamilyRepository.kt` | `KBFamilyDao`, `KBFamilyMemberDao` |
| `CalendarRepository.kt` | `KBCalendarEventDao`, `KBFamilyDao`, `KBChildDao`, `CalendarRemoteStore`, `FirebaseAuth` |
| `ChatRepository.kt` | `KBChatMessageDao`, `ChatRemoteStore`, `ChatStorageService`, `FirebaseAuth` |
| `DocumentRepository.kt` (1829 righe) | `KBDocumentDao`, `KBDocumentCategoryDao`, `KBExpenseDao`, `KidBoxDatabase`, `DocumentRemoteStore`, `DocumentStorageManager`, `ChatStorageService`, `FirebaseAuth`, `@ApplicationContext` |
| `DoseLogRepository.kt` | `KBDoseLogDao`, `DoseLogRemoteStore` |
| `ExpenseRepository.kt` | `KBExpenseDao`, `KBExpenseCategoryDao`, `KBFamilyDao`, `ExpenseRemoteStore`, `DocumentRepository`, `FirebaseAuth` |
| `FamilyLocationRepository.kt` | `KBSharedLocationDao`, `LocationRemoteStore`, `FirebaseAuth` |
| `GeofenceRepository.kt` | `KBGeofenceDao`, `GeofenceRemoteStore`, `FirebaseAuth` |
| `GroceryRepository.kt` | `KBGroceryItemDao`, `GroceryRemoteStore`, `FirebaseAuth`, `KBFamilyDao` |
| `HealthAIChatRepository.kt` | `KBAIConversationDao`, `KBAIMessageDao`, `AIService`, `MemoryFactRemoteStore`, `KBMemoryFactDao` |
| `HomeItemRepository.kt`, `HousePaymentRepository.kt` | DAO + RemoteStore + FirebaseAuth |
| `KBAIRepository.kt` | `KBAIConversationDao`, `KBAIMessageDao`, `AIService` |
| `MedicalExamRepository.kt`, `MedicalVisitRepository.kt`, `VaccineRepository.kt`, `TreatmentRepository.kt`, `PediatricProfileRepository.kt` | DAO + RemoteStore |
| `NoteRepository.kt` | `KBNoteDao`, `KBFamilyDao`, `NoteRemoteStore`, `FirebaseAuth` |
| `PasswordsRepository.kt` | `@ApplicationContext`, `PasswordEntryDao`, `PasswordGroupDao`, `PasswordRemoteStore`, `FirebaseAuth`, `AutoFillSnapshotScheduler` |
| `PetRepository.kt`, `PetEventRepository.kt`, `VehicleRepository.kt`, `VehicleEventRepository.kt` | DAO + RemoteStore + FirebaseAuth |
| `PhotoVideoRepository.kt` | `KBFamilyPhotoDao`, `KBPhotoAlbumDao`, `PhotoVideoRemoteStore`, `PhotoVideoStorageManager`, `FirebaseAuth` |
| `PlanningAIChatRepository.kt` | chat AI viaggi/planning |
| `SubscriptionRepository.kt` + `SubscriptionRepositoryImpl.kt` | wrapper Play Billing |
| `TodoRepository.kt` | `KBTodoListDao`, `KBTodoItemDao`, `TodoRemoteStore`, `FirebaseAuth`, `TodoReminderScheduler`, `KBFamilyDao`, `KBChildDao` |
| `TripRepository.kt` | DAO viaggi + `TripRemoteStore`, `FirebaseAuth` |
| `WalletRepository.kt` | `WalletTicketDao`, `WalletRemoteStore`, `DocumentStorageManager`, `FirebaseAuth` |

### 9.3 Strategia di scrittura e lettura

**Scrittura** (`upsertNote`, `NoteRepository.kt:79-133`): scrive Room con `KBSyncState.PENDING_UPSERT.rawValue`, poi `runCatching { remoteStore.upsert(target); noteDao.upsert(target.copy(syncStateRaw=SYNCED)) }.onFailure { noteDao.upsert(target.copy(syncStateRaw=ERROR, lastSyncError=err.message)) }`.

**Lettura inbound** (`applyInbound`, `NoteRepository.kt:162-216`): drop se `local.isDeleted || local.PENDING_DELETE` (anti-resurrect); LWW su `updatedAtEpochMillis` (con eccezione `localIsEmpty`); per record `MEMBERS` ricostruisce `visibilityMemberIdsJson`.

**Replay PENDING**: `startRealtime` chiama `flushPending(familyId)` che ripercorre `getBySyncState(familyId, PENDING_UPSERT)` (`PENDING_DELETE` analogamente).

### 9.4 Remote stores (`data/remote/.../`)

| Path | Listener / metodi |
|---|---|
| `notes/NoteRemoteStore.kt` | `listen(familyId, onChange, onError): ListenerRegistration` su `families/{id}/notes` con `MetadataChanges.INCLUDE`; `upsert(entity)` (cripta titolo/body → `titleEnc`/`bodyEnc`); `softDelete`; `decryptOrFallback(dto)` |
| `DocumentRemoteStore.kt` | `listenDocuments` + `listenCategories` (`EXCLUDE`), `fetchCategoriesOnce(familyId)` (prefetch gerarchia), `upsertDocument`, `upsertCategory`, `softDelete*`. Scrive ridondanti `parentId`/`folderId` per compat cross-client |
| `DocumentStorageManager.kt` | `uploadEncrypted(familyId, docId, fileName, mimeType, plainBytes): DocumentUploadResult` su `families/{fid}/documents/{docId}/{safeName}.kbenc` con `StorageMetadata` `kb_encrypted=1`, `kb_alg=AES-GCM`, `kb_orig_name`, `kb_orig_mime`. Helper `downloadDecrypted`, `decryptCachedDocumentBytes`, `delete` |
| `DocumentCryptoManager.kt` | `encrypt/decrypt`. AES/GCM/NoPadding, IV 12B, tag 16B. Output `encrypt` = formato iOS CryptoKit combined; `decrypt` dual-mode |
| `notes/NoteCryptoManager.kt` | `encryptToBase64/decryptFromBase64`. AES/GCM/NoPadding, output Base64 |
| `chat/ChatRemoteStore.kt` | `listenMessages(familyId, limit, onOldestDocument, onError)` con `MetadataChanges.INCLUDE`, paginazione via oldest doc |
| `chat/ChatStorageService.kt` | upload/download media chat (in chiaro), `downloadDecrypted` legacy `.kbenc` |
| `health/MedicalVisitRemoteStore.kt`, `MedicalExamRemoteStore.kt`, `VaccineRemoteStore.kt`, `TreatmentRemoteStore.kt`, `DoseLogRemoteStore.kt`, `PediatricProfileRemoteStore.kt` | usati dai rispettivi `*SyncCenter` |
| `life/{Pet,PetEvent,Vehicle,VehicleEvent,HomeItem,HousePayment}RemoteStore.kt` | listen + upsert per moduli "Vita" |
| `passwords/PasswordRemoteStore.kt` | listen entries + groups, scambia `ByteArray` via Base64 |
| `wallet/WalletRemoteStore.kt` | listen + upsert ticket wallet |
| `travel/TripRemoteStore.kt` | listen viaggi + leg + day-plans + spese + packing |
| `family/FamilyFirestoreCreationRepository.kt` | `createFamilyWithChildren(familyName, children): String` — batch `families/{fid}` + `members/{uid}` + `users/{uid}/memberships/{fid}` + figli |

### 9.5 Sync centers (`data/sync/`)

A differenza dei `RemoteStore`, i `*SyncCenter` sono **servizi singleton lifecycle-driven** che mantengono listener Firestore globali e mappano direttamente i DTO sulle entity Room.

**`FamilySyncCenter.kt`** — cuore del sync per la famiglia attiva. Costruttore: `KBFamilyDao`, `KBFamilyMemberDao`, `KBChildDao`, `KBUserProfileDao`, `KidBoxDatabase`, `FamilySessionPreferences`, `TripRepository`, `@ApplicationContext`. API:
- `startSync(familyId)` — apre 3 listener simultanei su `families/{fid}`, `.../members`, `.../children`. Pre-carica una volta i membri via `prefetchMembersAndChildren` con `Source.SERVER` (fallback `Source.CACHE`). Garantisce la chiave famiglia via `FamilyKeyEscrow.ensureFamilyKeyAvailable`
- `stopSync()` — rimuove listener, `tripRepository.stopRealtime()`, azzera `_initialSyncDone`, `sessionPrefs.clearActiveFamilyId()`
- `val initialSyncDone: StateFlow<Boolean>` — segnala alla Home il primo snapshot completo
- `val accessLostEvent: SharedFlow<Unit>` — emesso quando il proprio doc `members/{uid}` viene rimosso o `isDeleted=true`, **oppure** il `membersListener` riceve `PERMISSION_DENIED` (con bypass se l'utente è creator → glitch transitorio). All'access lost: `stopSync` + `database.clearAllTables()` + `FamilyKeyStore.deleteAllFamilyKeysForUser` + `sessionPrefs.clearActiveFamilyId()`
- LWW corretto su `updatedAt`: «accetta remoto se `local == null || remoteUpdatedAt == null || remoteUpdatedAt >= localUpdatedAt`» (quando `remoteUpdatedAt == null` cioè serverTimestamp non ancora applicato, accetta comunque per evitare lost updates)
- Patch ownership separata per propagare cambio proprietario senza scavalcare LWW completo

**`MembershipSyncService.kt`** — listener real-time su `users/{uid}/memberships`. `start(uid)` / `stop()`. Per ogni `ADDED`/`MODIFIED` scarica `families/{fid}` (+ self member doc) e lo upserta in Room così `FamilySwitcherViewModel.families` riflette in real time famiglie create su altri device. Soluzione al bug iOS-mirror "famiglia creata su un device non appare sull'altro" perché le 3 scritture (`families`, `members`, `memberships`) non sono atomiche → un solo `get()` può perderne una; con il listener persistente si ricompatta.

**`MedicalVisitSyncCenter.kt`** + analoghi (`MedicalExamSyncCenter`, `VaccineSyncCenter`, `TreatmentSyncCenter`, `DoseLogSyncCenter`, `PediatricProfileSyncCenter`) — mirror iOS `SyncCenter+*`. Pattern uniforme: `start(familyId)` apre listener e applica `applyInbound` con anti-resurrect + LWW:

```51:74:app/src/main/java/it/vittorioscocca/kidbox/data/sync/MedicalVisitSyncCenter.kt
    private suspend fun applyInbound(familyId: String, dtos: List<RemoteMedicalVisitDto>) {
        for (dto in dtos) {
            val local = dao.getById(dto.id)
            val remoteStamp = dto.updatedAtEpochMillis ?: 0L
            val localStamp = local?.updatedAtEpochMillis ?: 0L
            val localSync = local?.syncStateRaw ?: 0
            if (local != null && localSync == 1 && localStamp > remoteStamp) {
                continue
            }
            if (dto.isDeleted) {
                local?.let { dao.delete(it) }
                continue
            }
            if (remoteStamp >= localStamp) {
                dao.upsert(dto.toEntity(familyId))
            }
        }
        examSync.retryAfterVisitSnapshotPersisted(familyId)
    }
```

Particolarità: `MedicalExamSyncCenter` espone `retryAfterVisitSnapshotPersisted(familyId)` per riprovare esami con `prescribingVisitId` orfano dopo che la visita è arrivata.

---

## 10. Dipendenze esterne

### 10.1 Plugin Gradle

Dal version catalog `gradle/libs.versions.toml` e da `app/build.gradle.kts`:

| Plugin | ID | Versione |
|---|---|---|
| Android Application | `com.android.application` | **AGP 8.7.2** |
| Kotlin Android | `org.jetbrains.kotlin.android` | **Kotlin 2.0.21** |
| Kotlin Compose Compiler | `org.jetbrains.kotlin.plugin.compose` | 2.0.21 |
| Hilt | `com.google.dagger.hilt.android` | 2.52 |
| KSP | `com.google.devtools.ksp` | 2.0.21-1.0.28 |
| Google Services | `com.google.gms.google-services` | 4.4.2 |
| Firebase Crashlytics | – | **NON applicato** |
| kotlin-kapt | – | **NON applicato** (sostituito interamente da KSP) |

JDK target: **17**. Configuration cache attiva.

### 10.2 Dipendenze principali

#### AndroidX & Lifecycle / Activity / Compose foundation
- `androidx.appcompat:appcompat:1.7.0`
- `com.google.android.material:material:1.12.0`
- `androidx.core:core-splashscreen:1.0.1`
- `androidx.datastore:datastore-preferences:1.1.1`
- `androidx.lifecycle:lifecycle-runtime-ktx:2.8.7`
- `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7`
- `androidx.lifecycle:lifecycle-runtime-compose:2.8.7`
- `androidx.activity:activity-compose:1.9.3`
- `androidx.concurrent:concurrent-futures-ktx:1.1.0`
- `androidx.core:core(-ktx):1.15.0` (forzata)

#### Jetpack Compose
- `androidx.compose:compose-bom:2024.12.01` (platform BOM)
- `androidx.compose.ui:ui`, `ui-graphics`, `ui-tooling-preview`, `ui-tooling` (debug)
- `androidx.compose.material3:material3`
- `androidx.compose.material:material-icons-extended`
- `io.coil-kt:coil-compose:2.7.0`

#### Navigation & Hilt
- `androidx.navigation:navigation-compose:2.8.4`
- `androidx.hilt:hilt-navigation-compose:1.2.0`
- `com.google.dagger:hilt-android:2.52` + `hilt-android-compiler:2.52` (KSP)
- `androidx.hilt:hilt-work:1.2.0` + `androidx.hilt:hilt-compiler:1.2.0` (KSP)

#### Room
- `androidx.room:room-runtime:2.6.1`
- `androidx.room:room-ktx:2.6.1`
- `androidx.room:room-compiler:2.6.1` (KSP)

#### Firebase (via BOM 33.7.0)
- `firebase-auth`, `firebase-firestore`, `firebase-storage`, `firebase-storage-ktx`
- `firebase-messaging`
- `firebase-functions-ktx`
- `firebase-appcheck` + `firebase-appcheck-debug` (debug) + `firebase-appcheck-playintegrity` (release)

#### Coroutines
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0`
- `org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0`

#### Auth / Identità
- `androidx.credentials:credentials:1.3.0`
- `androidx.credentials:credentials-play-services-auth:1.3.0`
- `com.google.android.libraries.identity.googleid:googleid:1.1.1`
- `com.google.android.gms:play-services-auth:21.2.0`
- `com.facebook.android:facebook-login:18.2.3`

#### Billing
- `com.android.billingclient:billing-ktx:7.1.1`

#### CameraX (forzata a 1.5.3)
- `androidx.camera:camera-core/camera2/lifecycle/view` — tutti 1.5.3
- Nota: 1.6.x richiederebbe `compileSdk 36`/AGP 8.9+, quindi resta 1.5.3 per compatibilità

#### ML Kit & ZXing
- `com.google.mlkit:barcode-scanning:17.3.0`
- `com.google.mlkit:text-recognition:16.0.1`
- `com.google.zxing:core:3.5.3`
- `com.journeyapps:zxing-android-embedded:4.3.0` (`isTransitive = false`)

#### Maps & Location
- `com.google.android.gms:play-services-location:21.3.0`
- `com.google.android.gms:play-services-maps:18.2.0`
- `com.google.maps.android:maps-compose:2.11.4`
- `com.google.android.libraries.places:places:4.2.0`

#### Sicurezza / Crypto
- `androidx.security:security-crypto:1.1.0-alpha06` (EncryptedSharedPreferences in `FamilyKeyStore`)
- `com.google.crypto.tink:tink-android:1.13.0`
- `androidx.biometric:biometric-ktx:1.2.0-alpha05`

#### PDF & Health
- `com.tom-roush:pdfbox-android:2.0.27.0` (estrazione testo PDF wallet)
- `androidx.health.connect:connect-client:1.1.0-beta01` (la 1.1.0 stable richiederebbe `compileSdk 36`)

#### WorkManager
- `androidx.work:work-runtime-ktx:2.9.0`

#### HTTP & Guava
- `com.squareup.okhttp3:okhttp:4.12.0`
- `com.google.guava:guava:32.1.3-android` + workaround `listenablefuture:9999.0-empty-to-avoid-conflict-with-guava`

#### Test
- `junit:junit:4.13.2`, `mockwebserver:4.12.0`, `kotlinx-coroutines-test:1.8.1`
- `mockito-core:5.12.0`, `mockito-inline:5.2.0`, `mockito-kotlin:5.4.0`

---

## 11. Convenzioni di codice

### 11.1 Prefisso `KB` su modelli di dominio e persistenza

Sistematico ma con alcune eccezioni "moderne":
- **Tutti** i modelli "core famiglia" in `domain/model/` usano `KB*` (33 file su 35)
- Entity Room paralleli: `KB*Entity`
- DAO Room: `KB*Dao`
- **Eccezioni** (feature aggiunte più di recente): `Pet*`, `Vehicle*`, `HomeItem*`, `HousePayment*`, `PasswordEntry*`, `PasswordGroup*`, `PwnedPrefixCache*`, `SupportTicket*`. Anche in `domain/model/`: `HealthImportSnapshot`, `HealthTimelineEvent`, `TodoListExposure`, `TreatmentSchedulePeriod`, `WalletTicketKind`, `KidBoxEnums`

Il prefisso `KB` è lo stesso del progetto iOS gemello — è documentato esplicitamente in `KBLog.kt` con il commento *"Logging centralizzato KidBox (speculare a iOS KBLog)."*

### 11.2 ViewModel

- Suffisso `ViewModel` (es. `HomeViewModel`, `ChatViewModel`, `DocumentsViewModel`, `PlanningAIChatViewModel`)
- Tutti annotati `@HiltViewModel` con costruttore `@Inject`
- Ottenuti dai Composable via `hiltViewModel()` dal modulo `androidx.hilt:hilt-navigation-compose`

### 11.3 Composable

- **Schermate**: suffisso `Screen` in file `*Screen.kt` (es. `HomeScreen`, `DocumentBrowserScreen`, `ChatScreen`, `GeofenceEditScreen`)
- **Bottom sheet**: suffisso `Sheet` o `BottomSheet` (es. `FamilySwitcherBottomSheet`, `AddWalletTicketSheet`, `VisibilityPickerSheet`, `ShareBottomSheet`, `AIConsentBottomSheet`)
- **Dialog**: suffisso `Dialog` (es. `CrashReportConsentDialog`, `HousePaymentFormDialog`, `AiConsentDialog`)
- **Riga lista**: suffisso `Row` (es. `AIChatStandardMessageRow`, `CrashReportSettingsRow`)
- **Card/Bubble**: nome semantico (`TravelTripCard`, `ChatBubble`)
- I file di un'area UI sono raggruppati nella stessa cartella (`ui/screens/<area>/`), senza ulteriore separazione `components/` per area

### 11.4 Logging

**Logging centralizzato proprietario**: `it.vittorioscocca.kidbox.util.KBLog`. **Non** è usato Timber. `android.util.Log` compare ancora solo in pochi punti residui (`KidBoxApplication`, `CrashAnalyzer`, `KBLog` stesso, `SupportViewModel`); è in corso una migrazione totale (esiste `scripts/migrate_to_kblog.py`).

```12:25:app/src/main/java/it/vittorioscocca/kidbox/util/KBLog.kt
object KBLog {

    val app = CategoryLogger("app")
    val navigation = CategoryLogger("navigation")
    val data = CategoryLogger("data")
    val sync = CategoryLogger("sync")
    val auth = CategoryLogger("auth")
    val storage = CategoryLogger("storage")
    val ui = CategoryLogger("ui")
    val ai = CategoryLogger("ai")
    val security = CategoryLogger("security")
    val crypto = CategoryLogger("crypto")
    val persistence = CategoryLogger("persistence")
```

Caratteristiche:
- 11 sotto-logger per categoria
- Tag logcat composto come `KidBox/<category>[/<extraTag>]`
- Aggiunge automaticamente `[file:method():line]` ispezionando lo stacktrace
- Sink duale: `android.util.Log.*` **+** `KBFileLogger.appendSync(...)` su file persistente
- Livelli: `debug`, `info`, `warning`, `error` (con `Throwable`), `crash`
- Affiancato da `KBCrashHandler` (uncaught exception handler installato in `KidBoxApplication.onCreate`) e da `CrashAnalyzer` (post-mortem all'avvio successivo, con dialog di consenso `CrashReportConsentDialog`)

### 11.5 Code-style e lint

**Assenti**:
- Nessun `.editorconfig`
- Nessun `detekt.yml`; il plugin Detekt non è applicato
- Nessuna configurazione ktlint dedicata
- Nessun `AGENTS.md` nella root
- Nessuna cartella `.cursor/`
- Esiste invece `.claude/` (per workflow Claude Code, non per Cursor)

**Presenti**:
- `gradle.properties`: `kotlin.code.style=official`, `android.useAndroidX=true`, `android.nonTransitiveRClass=true`, `org.gradle.parallel=true`, `org.gradle.configuration-cache=true`
- `app/proguard-rules.pro`: **vuoto**. Combinato con `isMinifyEnabled = false` in `release`: l'app **non viene offuscata/minificata**
- `app/google-services.json` presente

### 11.6 KDoc e commenti

- `// region` / `// endregion` **non sono usati**
- KDoc `/** ... */` selettivo, per classi top-level e helper (es. `KBLog.kt`, `KBEvent.kt`, `HealthImportSnapshot.kt`). Non c'è policy "ogni metodo pubblico ha KDoc"
- Commenti inline prevalentemente in **italiano**, motivano scelte non ovvie (es. nota su CameraX 1.5.3 / `compileSdk 35`, nota sul manifest che rimuove `WorkManagerInitializer`)

### 11.7 File più lunghi (>1000 righe)

| Righe | File |
|---:|---|
| **2353** | `ui/screens/photos/FamilyPhotosScreen.kt` |
| **2191** | `ui/screens/documents/DocumentBrowserScreen.kt` |
| **2172** | `ui/screens/chat/ChatScreen.kt` |
| **1829** | `data/repository/DocumentRepository.kt` |
| **1825** | `ui/navigation/AppNavGraph.kt` |
| **1628** | `ui/screens/chat/ChatBubble.kt` |
| **1505** | `ui/screens/expenses/ExpensesHomeScreen.kt` |
| **1307** | `ui/screens/onboarding/OnboardingScreen.kt` |
| **1271** | `di/DatabaseModule.kt` (31 migrations Room) |
| **1212** | `ui/screens/vehicles/VehicleDetailScreen.kt` |
| **1146** | `ui/screens/calendar/CalendarScreen.kt` |
| **1090** | `ui/screens/travel/TravelItineraryBuilder.kt` |
|  **975** | `data/sync/FamilySyncCenter.kt` |

Un nuovo sviluppatore deve aspettarsi che la complessità si concentri nei tre grandi screen `FamilyPhotosScreen` / `DocumentBrowserScreen` / `ChatScreen`, nel super-NavHost `AppNavGraph.kt` e nel `DatabaseModule.kt` che ospita la cronologia completa delle migrations.

---

## 12. Flussi principali

### 12.1 Login / Onboarding / Creazione famiglia

1. **Splash + theme**: `MainActivity.onCreate` chiama `installSplashScreen()` (`androidx.core:core-splashscreen:1.0.1`), poi `setContent { KidBoxTheme(...) { AppNavGraph(...) } }` con `rememberNavController()`
2. **Start destination**: deciso da `OnboardingPreferences` (se `hasCompletedOnboarding` → `Home`, altrimenti `Login`/`Onboarding`)
3. **Login** (`LoginScreen.kt`): tap social button → `LoginViewModel.signInWithGoogle/Apple/Facebook` → `AuthFacade.signIn(provider, activityContext)` → `FirebaseAuth.signInWithCredential(...)` → `_authCheckState.value = Authenticated(hasFamily)`. `LoginViewModel.checkHasFamilyOnce()` con tripla fallback (DAO locale, `users/{uid}/memberships`, `collectionGroup("members")`)
4. **Switch account guard**: prima di ogni login, `LoginViewModel` esegue `FirebaseFirestore.getInstance().terminate().await() + clearPersistence().await()` per scartare la cache del precedente utente e prevenire `PERMISSION_DENIED` transienti
5. **Onboarding "create"** (`OnboardingScreen.kt:1307` righe): wizard nome famiglia + lista figli (nome, data nascita, altezza, peso). `OnboardingViewModel.createFamily()` → `FamilyFirestoreCreationRepository.createFamilyWithInitialChild()` (batch transazionale di `families/{fid}`, `families/{fid}/members/{uid}`, `users/{uid}/memberships/{fid}`, `families/{fid}/children/{cid}`). Poi `FamilyKeyStore.saveFamilyKey(randomBytes(32))` + `FamilyKeyEscrow.backup(...)` su `families/{fid}/memberKeyBackups/{uid}`
6. **Onboarding "join"** (`InviteCodeScreen.kt` + `InviteCodeViewModel.kt`): scan QR con CameraX + ML Kit Barcode Scanner. Parse `kidbox://join?familyId=...&inviteId=...&secret=<Base64URL>&code=...`. `JoinWrapService.joinFamily(...)` legge `families/{fid}/invites/{inviteId}`, deriva `wrapKey` con HKDF-SHA256 da `secret`, decifra `wrappedKeyCipher/Nonce/Tag` → `familyKey` 32 byte, salva in `FamilyKeyStore`, scrive `members/{uid}` + `memberships/{fid}`, marca invite come `usedAt/usedBy`
7. **Bootstrap**: post-onboarding `FamilySyncCenter.startSync(familyId)` apre i 3 listener famiglia + scarica `prefetchMembersAndChildren` (Source.SERVER → CACHE fallback) + `FamilyKeyEscrow.ensureFamilyKeyAvailable(...)`. `MembershipSyncService.start(uid)` ascolta `users/{uid}/memberships` per sincronizzare in real-time eventuali famiglie create su altri device

### 12.2 Caricamento documento PDF

1. **Entry**: `DocumentBrowserScreen.kt` (toolbar "Carica" / "Fotocamera" / "Libreria foto"). Prima si apre un picker visibilità (`pendingUploadScope`, `pendingMemberIds`)
2. **Picker file**: `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())`. Su `uri`:
   - `context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)`
   - MIME da `ContentResolver.getType`, `fileName` via `guessFileName`, bytes via `openInputStream(uri)?.use { it.readBytes() }`
   - `viewModel.importDocument(fileName, mimeType, bytes, targetFolderId)`
3. **ViewModel** (`DocumentsViewModel.importDocument`): risolve `parentId` (target folder o ultimo breadcrumb), calcola `scope/memberIds`, chiama `repository.uploadDocumentLocal(...)` + `repository.flushPending(familyId)`
4. **Repository** (`DocumentRepository.uploadDocumentLocal`):
   - Genera `id = UUID.randomUUID().toString()`
   - `localPath = persistPendingPlainFile(id, fileName, bytes).absolutePath` salva in `context.filesDir/kb_documents_pending/{id}_{safeFileName}`
   - Calcola `placeholderStoragePath = "families/$familyId/documents/$id/${safeFileName}.kbenc"`
   - Costruisce `KBDocumentEntity` con `syncStateRaw = PENDING_UPSERT`, `downloadURL = null`, `extractionStatusRaw = 0`, `visibilityScope` normalizzato, `createdBy = uid`
   - `documentDao.upsert(entity)` — l'UI riceve subito il doc "in upload"
5. **Flush pending → upload + Firestore** (`flushPending(familyId)`):
   - `ensureStorageUploaded(doc)`: se `downloadURL` ancora null e `localPath` esiste, chiama `storageManager.uploadEncrypted(...)` e aggiorna `storagePath` + `downloadURL`
   - `remoteStore.upsertDocument(...)` → su successo `documentDao.upsert(... SYNCED ...)`; su fallimento `lastSyncError`
6. **Crypto + Storage** (`DocumentCryptoManager.encrypt` + `DocumentStorageManager.uploadEncrypted`):
   - `Cipher.getInstance("AES/GCM/NoPadding")` + `Cipher.init(ENCRYPT_MODE, getFamilySecretKey(familyId))` — Android genera IV 12 byte automaticamente
   - Output `nonce(12) || encrypted` (formato iOS CryptoKit combined)
   - `getFamilySecretKey`: `FamilyKeyStore.loadFamilyKey(context, familyId, uid)` (EncryptedSharedPreferences, Android Keystore)
   - Storage: `families/$fid/documents/$id/$safeName.kbenc` con `StorageMetadata` `kb_encrypted=1`, `kb_alg=AES-GCM`, `kb_orig_name`, `kb_orig_mime`
7. **Firestore metadata** (`DocumentRemoteStore.upsertDocument`): path `families/{fid}/documents/{id}`, payload con metadata + ridondanti `parentId`/`folderId` (compat cross-client legacy) + `visibilityScope`/`visibilityMemberIds` + `updatedAt = serverTimestamp`. Campi OCR (`extractedText`, `extractionStatusRaw`) scritti **solo additivamente**
8. **Reazione UI**: `observeBrowser` (combine `categoryDao.observeByFamilyId` + 3 query documenti + filtri visibilità via `KBVisibilityScope.isVisible`) → `DocumentsViewModel.observeCurrentFolder` → Compose ricompone

**Preview/download** (`DocumentRepository.preparePreviewFile`): se `localPath` esiste → legge bytes (plain per `/chat/` o `kb_documents_pending`, altrimenti `storageManager.decryptCachedDocumentBytes`); senza local file → `storageManager.downloadDecrypted`. Scrive file decifrato in `cacheDir/kb_documents_preview/{id}_{safeFileName}` per Quick Look.

### 12.3 Note: tap → editor → save

**Lettura**:
1. `NotesHomeScreen` → tap su card → `onNavigate(AppDestination.NoteDetail.createRoute(familyId, noteId))`
2. `NoteDetailViewModel.bind(familyId, noteId, isNewRoute)` apre `combine(noteRepository.observeById(noteId), familyMemberDao.observeActiveByFamilyId(familyId))` e per ogni emission aggiorna `uiState`
3. `NoteRepository.observeById` mappa `noteDao.observeById(noteId): Flow<KBNoteEntity?>` → `Flow<KBNote?>` via `.toDomain()`
4. Realtime: `NoteRepository.startRealtime(familyId, onPermissionDenied)` apre `noteListener = remoteStore.listen(...)`. `NoteRemoteStore` usa `addSnapshotListener(MetadataChanges.INCLUDE)` su `families/{fid}/notes`
5. `applyInbound` per ogni cambio: drop se locale è `isDeleted` o `PENDING_DELETE`; LWW `remoteUpdated < localUpdated && !localIsEmpty → skip`; decripta `dto.titleEnc`/`bodyEnc` via `remoteStore.decryptOrFallback(dto)` (fallback `titlePlain`/`bodyPlain`); `noteDao.upsert(... SYNCED ...)`

**Scrittura** (tap "Salva" → Repository → Room PENDING → Firestore push → Room SYNCED):
1. `NoteDetailViewModel.save(onDone)`: normalizza title via `htmlToPlainText().trim()` e body via `trimEnd()`, **isDirty=false ottimistico**, chiama `noteRepository.upsertNote(...)`
2. `NoteRepository.upsertNote(...)`:
   - `ensureFamilyExists(familyId)` (crea placeholder `kb_families` se mancante per evitare FK violation)
   - Costruisce `KBNoteEntity` con `syncStateRaw = PENDING_UPSERT` e `noteDao.upsert(target)` (UI vede subito la modifica)
   - `runCatching { remoteStore.upsert(target); noteDao.upsert(target.copy(syncStateRaw = SYNCED)) }.onFailure { noteDao.upsert(target.copy(syncStateRaw = ERROR, lastSyncError = err.message)) }`
3. `NoteRemoteStore.upsert(note)`:
   - `titleEnc = crypto.encryptToBase64(note.title, note.familyId)`; idem `bodyEnc`
   - Scrive payload con `FieldValue.delete()` sui campi plain legacy `title`/`body`, `visibilityScope`/`visibilityMemberIds`, `isDeleted=false`, `updatedBy=uid`, `updatedAt = FieldValue.serverTimestamp()`. `createdBy`/`createdByName`/`createdAt` solo se `!exists`
   - `ref.set(payload, SetOptions.merge()).await()`
4. `NoteCryptoManager.encryptToBase64`: AES/GCM/NoPadding con `getFamilySecretKey(familyId)`. Output Base64 = `iv(12) || cipher+tag`
5. Il `noteListener` aperto su tutti i device riceve `MODIFIED` con il nuovo `updatedAt = serverTimestamp` → `applyInbound` riconferma SYNCED su Room (idempotente)

**Replay PENDING**: quando il device era offline, `startRealtime` chiama `flushPending(familyId)`:
- `noteDao.getBySyncState(familyId, PENDING_UPSERT).forEach { runCatching { remoteStore.upsert(it) → SYNCED } }`
- `noteDao.getBySyncState(familyId, PENDING_DELETE).forEach { runCatching { remoteStore.softDelete(...); noteDao.deleteById(it.id) } }`

### 12.4 Chat di famiglia

1. **Entry**: route `Chat` → `ChatScreen.kt:2172 righe`. Usa `coordinator.activeFamilyId` come fonte di verità (passato come arg di route) e monta `ChatViewModel(SavedStateHandle)` con `familyId` dall'arg
2. **`ChatViewModel.init`**: carica `inputText` da `UserDefaults["chatDraft_<familyId>"]` (SharedPreferences)
3. **Start listening**: due listener Firestore via `ChatRemoteStore`:
   - `listenMessages(familyId, limit=150, MetadataChanges.INCLUDE)` → `applyRemoteChanges` con `applyUpsert`
   - `listenTyping(familyId, excludeUID)` con throttling 500 ms
4. **`sendText()`**:
   - Trim + `pendingMentions(in: trimmed)` → risolve menzioni `@displayName` → `[ChatMention]`
   - Pulisce `inputText` e draft, poi `send(type=.text, text=trimmed, replyToId=replyId, mentions=mentions)`
5. **Crypto outbound**: `RemoteChatMessageDto` con `textEnc = NoteCryptoManager.encryptToBase64(text, familyId)` (Base64 AES-GCM). **I media chat NON vengono cifrati** (vedi `ChatStorageService.kt`: sicurezza affidata alle Storage Rules, streaming nativo non supporta AES-GCM)
6. **Upload media**: `ChatStorageService.upload(...)` su `families/{fid}/chat/{messageId}/{fileName}`. Video compressi con `VideoCompressor.kt` (~13 KB, MediaCodec) prima dell'upload
7. **Inbound realtime** (`applyUpsert`):
   - Decifra `textEnc` con `NoteCryptoManager.decryptFromBase64(...)`. Su fallimento fallback al `dto.text` plaintext invece di cancellare
   - Marca tombstone se `dto.isDeleted == true` (deleteForEveryone) ma mantiene la riga locale per visualizzare "messaggio eliminato"
   - Se `existing.type == AUDIO` e non sono io → `startTranscriptIfNeeded` (trascrizione locale via `TranscriptionService.kt`)
8. **Push chat E2E**: lato server (Cloud Function) invia push FCM con `data["type"]="new_chat_message"` o `chat_mention`. Il client decifra non in-process (Android non ha equivalente di Notification Service Extension iOS): la push contiene fallback testuale generico ("Hai un nuovo messaggio"), il messaggio cifrato viene decifrato solo quando l'utente apre l'app

Nota: la chat **non passa dall'outbox `KBSyncOp`** (assente su Android — vedi §13) — `ChatViewModel` chiama `remoteStore.upsert` direttamente.

### 12.5 Health Connect → AI analysis

1. **Entry**: `HealthConnectAppScreen.kt` (sezione settings)
2. **Permission flow**: `RuntimePermissions.kt` helper richiede `health.READ_HEART_RATE`, `READ_STEPS`, `READ_WEIGHT`, `READ_ACTIVE_CALORIES_BURNED`, `READ_EXERCISE` tramite `HealthConnectClient`
3. **Import**: `HealthConnectGateway.kt` legge dati ultimi 90 giorni → costruisce `HealthImportSnapshot` (con `restingHeartRateAvg90d`, `vo2Max`, `hrvSdnnMsAvg90d`, `recentWorkouts`, `recentECGs`, …)
4. **AI analysis** (background): `HealthPatternAnalyzerService.kt` (`@HiltWorker`) gira settimanalmente (via WorkManager). Costruisce un prompt con `HealthImportSnapshot` + storia clinica famiglia, chiama `AIService.askAI(purpose="healthPattern")` su Cloud Function. Salva risultato come `KBHealthInsightEntity` con `monthKey`, `isRead = false`
5. **Notifica**: al completamento, `HealthReminderReceiver` (locale via `AlarmManager`) o push remota (via `HealthPatternBroadcastReceiver`) → `NotificationDeepLinkRouter` → `AppDestination.AiChat` con `fullText` salvato in `HealthPatternDraftStore` per essere mostrato direttamente nella chat AI

#### Chat AI Salute — integrazione Health Connect nel contesto

**`HealthAIChatViewModel.kt`** carica lo snapshot persistito (`healthLinkStore.load(childId)`) e lo passa a **`HealthContextBuilder.buildSystemPrompt(…, healthSnapshot = …)`** — sia per il prompt standard (referti troncati) che per quello full.

**`HealthContextBuilder.kt`** (`data/health/ai/`) accetta ora il parametro opzionale `healthSnapshot: HealthImportSnapshot?`. Se non null, appende la sezione wearable tramite `ClinicalRecordAppleHealthNarrative.analyze(snapshot, null, emptyList())` (FC a riposo, VO₂ max, passi, workout, ECG) prima del footer `--- FINE CONTESTO SALUTE ---`.

> ⚠️ `ClinicalRecordAppleHealthNarrative.analyze()` accetta `List<KBMedicalVisitEntity>` per il hint FC cardiologica, ma `HealthContextBuilder` lavora con domain model `KBMedicalVisit`. Si passa `emptyList()` — il contesto wearable principale è indipendente dalle visite.

`HealthLinkStore` è injected via Hilt nel costruttore di `HealthAIChatViewModel`. Lo snapshot è null se l'utente non ha collegato Health Connect → la sezione viene semplicemente omessa.

---

## 13. Decisioni architetturali rilevanti

### 13.1 Niente Outbox `KBSyncOp` (a differenza di iOS)

iOS ha un'outbox SwiftData centralizzata (`KBSyncOp`) + un `SyncCenter` con `process(op:)` dispatcher su 22 estensioni. **Android replica la stessa semantica senza outbox formale**:
- I `syncStateRaw` (PENDING_UPSERT/PENDING_DELETE/ERROR) sono **inline** sulle entity Room
- Ogni repository ha il proprio `flushPending(familyId)` che ripercorre `getBySyncState(..., PENDING_*)`
- Il dispatcher è quindi distribuito tra i 31 repository invece che centralizzato

**Conseguenza**: l'auto-flush non è globale (non c'è `startAutoFlush()` come iOS). Ogni repository chiama `flushPending` solo all'avvio del proprio `startRealtime(familyId)` e quando l'utente fa un save (optimistic + flush immediato sul singolo). Se un upsert fallisce per network e il repository non viene ri-bind nella sessione, il record resta in `ERROR` finché non si riapre lo screen.

### 13.2 Source of truth della UI = Room

Tutta la UI osserva `Flow` da Room DAO, **mai direttamente da Firestore**. I listener Firestore scrivono solo su Room. Vantaggi: offline-first nativo, semplice da testare (mock DAO), zero "ghost states" da metadata changes Firestore.

LWW su `updatedAtEpochMillis`. Anti-resurrect: locale `PENDING_DELETE` blocca upsert remoti (evita che il listener Firestore "resusciti" un record che l'utente ha appena cancellato offline).

### 13.3 Cifratura lato client (E2E per famiglia)

KidBox cifra i payload sensibili: il backend Firestore/Storage vede solo ciphertext (per documenti/note/wallet/password/foto). Per chat, **solo il testo** è cifrato; i media sono in chiaro per consentire streaming nativo + interop iOS.

- **Chiave per famiglia**: 32 byte AES in `EncryptedSharedPreferences` (Android Keystore-backed) con fallback `SharedPreferences` plain in caso di Keystore corrotto. Identificazione: `fk_{familyId}_{userId}` (un dispositivo con due account ha due chiavi distinte per la stessa famiglia)
- **Escrow su Firestore**: la chiave è wrappata con una **escrow key derivata deterministicamente** da HKDF-SHA256 con info = `"kidbox-key-escrow-v1:{userId}:{familyId}"` e salt = `"kidbox-escrow-salt-2026"`. Backup su `families/{fid}/memberKeyBackups/{uid}`. Recovery solo con Firebase UID (security rules limitano a `request.auth.uid == userId`). Permette ri-installare l'app e recuperare automaticamente le chiavi senza re-scan QR
- **Formato CryptoKit-combined per Storage**: `[12-byte nonce][ciphertext][16-byte tag]`. Compat-layer per file Android legacy `[4-byte ivSize][iv][cipher+tag]` con `prefixedIvSize in 8..32`

### 13.4 KBLog + KBFileLogger + KBCrashHandler

Toolchain proprietaria invece di Timber/Firebase Crashlytics:
- `KBLog` con 11 categorie + sink duale (logcat + file persistente)
- `KBFileLogger` ruota log a 500 KB con retention 3 giorni
- `KBCrashHandler` installa `Thread.setDefaultUncaughtExceptionHandler` che dumpa l'eccezione + lo stato app in un file
- `CrashAnalyzer` al boot successivo legge il file, e se c'è un crash chiama la Cloud Function `analyzeLogs` (LLM) per generare un'analisi human-readable, mostra `CrashReportConsentDialog` per consenso utente prima di inviare a `crash_reports/{id}` Firestore (cap `rawLogs <= 50 KB`)

Razionale: controllo totale del flusso di crash reporting (consenso GDPR esplicito + analisi LLM contestuale invece di stack trace grezzo), evitando il vendor lock-in di Crashlytics.

### 13.5 Niente Jetpack DataStore

Tutte le preferenze sono wrapper su SharedPreferences (`OnboardingPreferences`, `ThemePreference`, `FamilySessionPreferences`, `DocumentsUiPreferences`, `MessageSettingsPreferences`, `TravelProfilePreferences`, `AiConsentStore`). Scelta deliberata: DataStore richiede gestione `Flow` + coroutine ovunque, mentre SharedPreferences sincrono è sufficiente per le piccole preferenze utente.

### 13.6 Niente edge-to-edge

`WindowCompat.setDecorFitsSystemWindows(window, true)` + colori system bars fissi (`#FFF5F3EE` light / `#FF1C1C1E` dark) invece del paradigma edge-to-edge raccomandato da Material 3 / Android 15. Scelta legacy ma intenzionale: la UI è progettata con `KidBoxIosFormChrome.kt` per replicare il look iOS, evitando le complicazioni del padding manuale per status/nav bar.

### 13.7 NavHost monolitico

`AppNavGraph.kt` (1825 righe) contiene **tutti** i `composable()` block dell'app. Niente nav sub-graph (`navigation { ... }`) né navigation moduli per area. Razionale: il grafo è già complesso ma resta lineare e debuggabile in un solo file; partizionarlo richiederebbe condivisione del `navController` tra moduli e aggiungerebbe boilerplate senza vantaggi reali per un'app monolitica.

### 13.8 Singleton ovunque per i sync center

`FamilySyncCenter`, `MembershipSyncService`, e i 6 `*SyncCenter` salute sono singleton Hilt. Mantengono `ListenerRegistration?` interni e si attivano via `start*/stop*`. Razionale: la sincronizzazione famiglia è ortogonale al ciclo di vita della UI (non deve fermarsi quando si naviga tra screen), quindi resta legata al `SingletonComponent`. Stop esplicito solo su logout/leave-family/access-lost.

### 13.9 Compose Navigation senza deep link nativi

`composable(deepLinks = ...)` **non è usato**. Tutti i deep link da notifica passano per `NotificationDeepLinkRouter` (singleton object) che espone `StateFlow` consumati in `LaunchedEffect` dentro `AppNavGraph`. Razionale: i deep link da push hanno spesso bisogno di **switch famiglia** prima di navigare (`pendingFamilyId != activeFamilyId`), il che richiede una sequenza asincrona (`familySwitcherVm.switchToFamily(...)` → wait for Room → navigate). `composable(deepLinks = ...)` è troppo dichiarativo per gestire questo workflow stateful.

### 13.10 Manifest rimuove `WorkManagerInitializer`

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        tools:node="remove" />
</provider>
```

Razionale: senza questa rimozione, `androidx.startup` inizializza WorkManager **prima** che Hilt abbia popolato `KidBoxApplication.workerFactory`, facendo crashare i `@HiltWorker` (es. `RebuildAutoFillSnapshotWorker`). Con la rimozione, l'init è esplicito in `KidBoxApplication.onCreate()` dopo che Hilt ha iniettato la factory.

### 13.11 CameraX bloccata a 1.5.3

`configurations.configureEach { resolutionStrategy { force("androidx.camera:camera-core:1.5.3") } }` (e analoghi per `camera2`, `lifecycle`, `view`). Razionale: 1.6.x richiederebbe `compileSdk 36` / AGP 8.9+, ma il progetto sta su `compileSdk 35` / AGP 8.7. La risoluzione automatica delle transitive può portare a versioni miste di CameraX `.so` (crash a runtime), quindi il `force` previene il problema.

### 13.12 Niente offuscamento R8/ProGuard

`isMinifyEnabled = false` in release + `proguard-rules.pro` vuoto. Razionale: l'app fa heavy use di **reflection per Hilt + Room + Firebase + Gson interno**. Senza un set completo di `-keep` rules il release crasherebbe in modo subdolo. Scelta temporanea: l'app non è ancora distribuita pubblicamente (le release `.aab` esistono ma non sono pubblicate). Da fare prima del Play Store: scrivere le rules R8 e abilitare il minify.

### 13.13 Localizzazione hardcoded in italiano

La gran parte dei testi UI è hardcoded in italiano nei sorgenti Kotlin (es. `Text("Solo io")`, alert "Famiglia non trovata", `KBVisibilityScope.chipLabel`). Le 6 chiavi password.group.* in `strings.xml` sono l'eccezione. Razionale: il target è italiano, e Compose non ha un equivalente diretto e ergonomico di `Localizable.strings` per stringhe dinamiche con interpolazione (`stringResource(R.string.x, arg)` richiede `R.string` per ogni testo, oneroso senza beneficio per il target attuale).

### 13.14 App Check installer dimenticato

Vedi §6.10 — `AppCheckInstaller.install()` esiste in due varianti debug/release ma **non viene mai chiamato** in `KidBoxApplication.onCreate()`. Da fixare appena App Check enforcement viene attivato in Firebase Console su Storage/Functions, altrimenti l'app fallirà con `403`.

### 13.15 `KidBoxFirebaseMessagingService` non `@AndroidEntryPoint`

I servizi FCM e i Receiver Android NON sono `@AndroidEntryPoint` — gestiscono dipendenze internamente tramite API statiche (`FirebaseAuth.getInstance()`, `FirebaseFirestore.getInstance()`, `NotificationDeepLinkRouter` singleton object). Razionale: instantiation di Hilt component per ogni FCM message sarebbe overhead inutile.

### 13.16 Limitazioni emulatore note

- **FCM su emulatore senza Google Play Services**: token non viene generato → push non funzionano. Testare su device fisico o emulatore con Google APIs
- **Health Connect su emulatore < API 34**: richiede installare manualmente l'APK "Health Connect by Android" o usare emulatore con Android 14+ pre-installato
- **Credential Manager (Google Sign-In)**: su emulatore senza account Google configurato, fallisce silenziosamente. Workaround: configurare account in Settings → Accounts prima di testare
- **CameraX QR scanner**: l'emulatore non simula la fotocamera per ML Kit Barcode Scanner; testare su device fisico
- **Geofencing**: l'emulatore non triggera reliably i transition `ENTER`/`EXIT`; usare "Extended controls → Location" con set di waypoints

---

## Appendice — Riferimenti rapidi

| Cosa | Dove |
|---|---|
| Entry point app | `KidBoxApplication.kt` (`@HiltAndroidApp`) |
| Activity launcher | `MainActivity.kt` |
| NavHost root | `ui/navigation/AppNavGraph.kt` |
| Routes | `ui/navigation/AppDestination.kt` |
| Room schema | `data/local/db/KidBoxDatabase.kt` (`version = 34`) |
| Migrazioni dati | `di/DatabaseModule.kt` (31 migrations inline) |
| Cache documenti locale | `context.filesDir/kb_documents_pending/` + `cacheDir/kb_documents_preview/` |
| Sync famiglia | `data/sync/FamilySyncCenter.kt` |
| Sync memberships | `data/sync/MembershipSyncService.kt` |
| Crypto core documenti | `data/remote/DocumentCryptoManager.kt` |
| Crypto note | `data/remote/notes/NoteCryptoManager.kt` |
| Keychain famiglia | `data/crypto/FamilyKeyStore.kt` (EncryptedSharedPreferences) |
| Escrow chiavi | `data/crypto/FamilyKeyEscrow.kt` |
| Invite QR crypto | `data/crypto/InviteCrypto.kt` |
| Auth facade | `data/remote/auth/AuthFacade.kt` |
| Logout | `domain/auth/LogoutUseCase.kt` |
| FCM service | `notifications/KidBoxFirebaseMessagingService.kt` |
| Deep link router | `notifications/NotificationDeepLinkRouter.kt` |
| Badge | `data/notification/HomeBadgeManager.kt` |
| Logging | `util/KBLog.kt` + `util/KBFileLogger.kt` |
| Crash handler | `util/KBCrashHandler.kt` + `util/CrashAnalyzer.kt` |
| Visibilità record | `domain/model/KBVisibilityScope.kt` |
| AI dispatcher | `data/remote/ai/AIService.kt` |
| Billing | `billing/KBBillingManager.kt` |
| Bootstrap Firebase | `app/google-services.json` (auto-init via `google-services` plugin) |
| Hilt modules | `di/{AIModule,AuthModule,BillingModule,DatabaseModule,PasswordsModule,SubscriptionModule}.kt` |
