# Work order 0.0.1 — Session Porter for Claude Code

> **Destinatario:** l'agente di sviluppo che lavora su questo repository.
> **Ruolo:** sviluppatore del plugin IntelliJ qui contenuto.
> **Istruzione:** leggi l'intero documento prima di toccare il codice. Descrive il perimetro
> funzionale e i vincoli della **0.0.1**, la versione di sviluppo attuale: è la specifica di
> riferimento, non un elenco di modifiche incrementali. Ogni intervento futuro parte da qui e, al
> termine, deve superare la checklist di §8.

---

## 1. Contesto del progetto

Plugin IntelliJ in Kotlin che esporta e importa le sessioni di chat di Claude Code. È il gemello di
*Github Copilot sessions*: dove Claude Code si comporta diversamente da Copilot, questo documento
descrive Claude Code (vedi §3).

| Percorso | Ruolo |
|---|---|
| `build.gradle.kts` | build, `changeNotes` → riquadro **What's New**, `runIde` isolato, firma e pubblicazione |
| `gradle.properties` | `pluginName`, `pluginVersion`, `platformVersion`, `javaVersion=21` |
| `settings.gradle.kts` | `rootProject.name`, cioè il nome dello ZIP prodotto |
| `CHANGELOG.md` | formato *Keep a Changelog*, alimenta `changeNotes` |
| `README.md` | documentazione utente e di rilascio (italiano) |
| `LICENSE` | MIT, cioè la licenza d'uso (EULA) del plugin |
| `THIRD_PARTY_NOTICES.md`, `licenses/Apache-2.0.txt` | componenti di terze parti inclusi nello ZIP (Gson) e loro licenza; nota sui marchi |
| `PRIVACY.md` | informativa privacy: nessun dato raccolto né trasmesso |
| `src/main/resources/META-INF/plugin.xml` | `<name>`, `<vendor>`, `<description>` → riquadro **Overview**, `<resource-bundle>`, gruppo azioni del menu `Tools` |
| `src/main/resources/META-INF/pluginIcon*.svg` | icona su *Settings \| Plugins* e sul Marketplace |
| `src/main/resources/icons/` | icone 16×16 del menu (variante chiara e scura) |
| `src/main/resources/messages/ClaudeSessionsBundle*.properties` | unici testi localizzati |
| `core/PluginNames.kt` | nome utente del plugin, mai tradotto |
| `core/ClaudePaths.kt` | risoluzione di `~/.claude`, `encodeProjectPath()`, `normalizeProjectPath()` |
| `core/ClaudeSessionsBundle.kt` | risoluzione della lingua di ogni testo |
| `core/SessionScanner.kt` | elenco e lettura dei transcript locali (`listSessions()`, `existingTranscripts()`, `describe()`) |
| `core/SessionArchive.kt` | export/import e orchestrazione (`export()`, `readArchiveManifest()`, `import()`) |
| `core/TranscriptRewriter.kt` | riscrittura strutturale del transcript, liste contrattuali dei campi |
| `core/PathMapper.kt` | traduzione dei percorsi fra le due macchine |
| `core/TimestampShift.kt` | traslazione dei timestamp verso il momento dell'import |
| `core/SessionInfo.kt` | metadati di sessione, `ConflictPolicy`, `ImportedSession` (esito riletto da disco), `FailedCheck` |
| `core/ImportDiagnostics.kt` | diagnosi di visibilità `OK`/`KO` |
| `core/ImportLog.kt` | log diagnostico dell'import (`FileImportLog`, `NOOP`) |
| `ui/IdeDisplayLanguage.kt` | passa a `core/` la lingua dell'IDE |
| `ui/ClaudeSessionsActionGroup.kt` | sottomenu **Tools \| Claude Code sessions** con titolo tradotto |
| `ui/ClaudeSessionActionBase.kt` | base comune delle due azioni |
| `ui/ExportClaudeSessionsAction.kt` | azione di export + notifica di esito |
| `ui/ImportClaudeSessionsAction.kt` | azione di import + notifica di esito + log |
| `ui/ExportSessionsDialog.kt`, `ui/ImportSessionsDialog.kt`, `ui/SessionSelectionPanel.kt` | dialoghi di selezione |
| `ui/ClaudeNotifications.kt` | notifiche e loro azioni (*Show archive*, *Show import log*, *Copy resume command*) |
| `src/test/kotlin/.../core/SessionArchiveTest.kt` | round-trip export/import, politiche, cronologia dei file, diagnosi, log |
| `src/test/kotlin/.../core/TranscriptRewriterTest.kt` | campi riscritti e liste contrattuali |
| `src/test/kotlin/.../core/TimestampShiftTest.kt` | traslazione dei timestamp |
| `src/test/kotlin/.../core/ClaudePathsTest.kt` | codifica e normalizzazione dei percorsi, `PathMapper` |
| `src/test/kotlin/.../core/SessionScannerTest.kt` | lettura dei transcript |
| `src/test/kotlin/.../core/ClaudeSessionsBundleTest.kt` | fallback e completezza delle traduzioni, allineamento del nome |

### Vincoli architetturali da rispettare

1. **`core/` non deve dipendere dalle API IntelliJ.** Contiene solo Java/Kotlin stdlib e Gson, ed è
   così che i test girano senza IDE. Ogni dipendenza da `com.intellij.*` va confinata in `ui/`; la
   lingua dell'IDE arriva a `core/` attraverso `ClaudeSessionsBundle.displayLanguage`.
2. **Tolleranza al formato:** Claude Code aggiunge tipi di riga e chiavi a ogni versione. Una riga o
   una chiave sconosciuta si copia com'è, una riga illeggibile resta identica: mai assumere presente
   un campo che non si è verificato.
3. **Il contenuto della conversazione non si tocca mai.** Le riscritture sono strutturali
   (JSON per JSON, chiave per chiave), limitate ai campi e alle posizioni di §4.3, mai sostituzioni di
   testo sul transcript.
4. **Il log diagnostico non può far fallire l'import** e non contiene mai testo di conversazione —
   nemmeno il nome della sessione, che senza una riga `summary` è un'anteprima del primo messaggio.
5. **Nessuna API interna, sperimentale o deprecata.** Il plugin non dipende da altri plugin e la
   propria versione la legge dal `plugin.xml` incluso nel jar, non da `PluginManager`. Prima di usare
   un'API della piattaforma va controllato che non sia deprecata in nessuna delle build di §7.4: ad
   esempio `SimpleListCellRenderer.create()` è pianificata per la rimozione dalla 2026.2 e al suo posto
   si usa `ColoredListCellRenderer`.
6. Commenti in inglese, solo dove il codice non è autoesplicativo, nello stile già presente nel
   repository: spiegano il *perché*, non il *cosa*.
7. Ogni comportamento osservabile va documentato in `CHANGELOG.md` **e** in `README.md`.

### Comandi

```bash
./gradlew test            # round-trip export/import, riscrittura, timestamp, diagnosi, traduzioni
./gradlew patchPluginXml  # genera il plugin.xml finale (verifica di Overview e What's New)
./gradlew buildPlugin     # ZIP in build/distributions
./gradlew runIde          # IDE di prova, su build/claude-home-test invece di ~/.claude
./gradlew verifyPlugin    # IntelliJ Plugin Verifier sulle build di §7.4, ogni segnalazione è un errore
./gradlew signPlugin publishPlugin   # firma e pubblicazione (vedi README)
```

---

## 2. Perimetro funzionale della 0.0.1

* **`Tools | Claude Code sessions | Export Sessions...`** — elenco di tutte le sessioni locali
  (nome, cartella, branch, data, messaggi, dimensione, id) con filtro e selezione multipla; salva la
  selezione in un archivio ZIP.
* **`Tools | Claude Code sessions | Import Sessions...`** — legge un archivio, segnala le sessioni
  già presenti, applica la politica di conflitto scelta (*Skip*, *Replace*, *Duplicate*, che è il
  default) e offre l'opzione **"Attach the imported sessions to this folder"**.
* **Notifica di esito** per entrambe le operazioni: *Show archive* dopo un export; *Show import log*
  dopo ogni import e, quando almeno una sessione è stata importata, *Copy resume command*.

Il nome utente del plugin è **`Session Porter for Claude Code`** ovunque e non viene tradotto: `pluginName`,
`<name>`, id del `<notificationGroup>` (che **deve** coincidere con `ClaudeNotifications.GROUP_ID`,
altrimenti le notifiche smettono di comparire), titolo dei messaggi e `producer` scritto nel manifest
dell'archivio. Tutti derivano da `PluginNames.DISPLAY_NAME`, e `ClaudeSessionsBundleTest` ne verifica
l'allineamento. Il titolo del sottomenu (`group.ClaudeSessionsImportExport.Menu.text`) invece è un
testo dell'interfaccia e segue la lingua, come in *Sessioni Claude Code*.

**Non** vanno mai cambiati il `<id>` del plugin né i nomi dei package: cambiare l'id farebbe apparire
il plugin come una nuova installazione, lasciando orfana quella esistente.

---

## 3. Dove vive una sessione

Una sessione di Claude Code è fatta di tre pezzi, tutti sotto la home (`~/.claude`, o
`CLAUDE_CONFIG_DIR`, o la property `claude.home`):

| Pezzo | Dove vive | A cosa serve |
|---|---|---|
| transcript | `projects/<cartella>/<sessionId>.jsonl` | la conversazione: è ciò che `/resume` e `claude --resume` elencano e riprendono |
| dati ausiliari | `projects/<cartella>/<sessionId>/` | esecuzioni dei subagent, risultati degli strumenti |
| cronologia dei file | `file-history/<sessionId>/` | i backup richiamati dalle righe `file-history-snapshot` e `file-history-delta`: checkpoint e `/rewind` |

`<cartella>` è la directory di lavoro codificata: ogni carattere fuori da `[A-Za-z0-9]` diventa `-`
(`ClaudePaths.encodeProjectPath()`). **È la chiave della visibilità**: Claude Code legge solo la
cartella che corrisponde alla directory da cui viene avviato, quindi un transcript valido in qualunque
altra cartella esiste su disco ma non compare mai.

A differenza di GitHub Copilot, **non esiste una seconda metà della sessione dentro l'IDE**: nessun
database del plugin da aggiornare, nessun ponte verso le API di un altro plugin, nessun elenco letto
una sola volta all'apertura del progetto. L'elenco viene ricostruito da disco a ogni `--resume`, per
cui dopo un import **non serve alcun riavvio**; la notifica offre invece di copiare il comando di
ripresa. Sezioni, classi e azioni che nel progetto gemello riguardano quella seconda metà
(`IdeSessionRecord`, il bridge verso il plugin, *Restart IDE now*) qui non hanno equivalente. Lo
stesso vale per il plugin ufficiale *Claude Code [Beta]* (`com.anthropic.code.plugin`): apre la CLI nel
terminale e fornisce gli strumenti MCP dell'IDE, ma non tiene un elenco delle sessioni da aggiornare.

Anche l'installazione non richiede riavvio: il plugin è idoneo al caricamento dinamico (nessun
componente di applicazione o di progetto, solo azioni, un gruppo di notifiche e un resource bundle) e
IntelliJ lo carica subito, come registra `DynamicPluginsSupportImpl - load plugin` nel log dell'IDE.
Il plugin non deve quindi chiedere un riavvio né dopo l'installazione né dopo un import; se una
modifica futura lo rendesse non dinamico, `failureLevel = ALL` (`NOT_DYNAMIC`) fa fallire la build.

Non si esportano `history.jsonl` (cronologia dei prompt digitati, che ne contiene il testo e non
serve a riprendere una sessione), `session-env/`, `shell-snapshots/` e `sessions/` (stato dei
processi della macchina di origine).

---

## 4. Architettura

### 4.1 Formato dell'archivio

```
manifest.json                    # formatVersion, exportedAt, producer, sourceHome, sessions
sessions/<id>/transcript.jsonl   # il transcript
sessions/<id>/aux/...            # copia fedele di projects/<cartella>/<id>/
sessions/<id>/file-history/...   # copia fedele di file-history/<id>/
```

`SessionArchive.FORMAT_VERSION` vale **1**. L'archivio è autodescrittivo: una entry che manca si
gestisce per la sua assenza, mai in base al numero di formato, che serve solo a rifiutare un layout
incomprensibile a questa build. Ogni voce di `sessions` è un `SessionInfo`: un campo nuovo va
aggiunto come opzionale, perché un archivio precedente lo lascia vuoto.

* `export()` legge ogni file per intero **prima** di aprire la entry, così un file che non si legge
  non lascia una entry troncata: viene saltato, conteggiato in `ExportOutcome.unreadableFiles` e
  segnalato con un avviso. Il manifest è scritto per ultimo ed elenca solo le sessioni scritte.
* `import()` importa una sessione alla volta: un'eccezione su una finisce in
  `ImportOutcome.failures` e non ferma le altre. Le entry che uscirebbero dalla cartella di
  destinazione (*zip-slip*) vengono scartate e registrate nel log.

### 4.2 Conflitti

Una sessione è presente quando un transcript con il suo id esiste in **qualunque** cartella di
`projects/` (`SessionScanner.existingTranscripts()`), non solo in quella di destinazione.

* `SKIP`: la sessione locale resta intatta;
* `REPLACE`: la sessione locale — transcript, cartella ausiliaria e cronologia dei file, ovunque si
  trovi — viene rimossa prima di scrivere quella dell'archivio, così l'id resta unico;
* `DUPLICATE` (default): nuovo UUID, propagato al nome del file, ai campi id del transcript, alla
  cartella ausiliaria e alla cartella della cronologia dei file.

`ImportedSession.action` vale `new`, `replaced` o `duplicated`, e ogni `ImportedSession` è **riletta
da disco** (`SessionScanner.describe()`) dopo la scrittura: notifica e diagnosi descrivono ciò che
Claude Code troverà, non ciò che l'import intendeva scrivere.

### 4.3 Riscrittura del transcript

`TranscriptRewriter` adatta il transcript alla macchina di destinazione, **chiave per chiave**, solo
nelle posizioni in cui Claude Code scrive i campi che descrivono la macchina: il primo livello della
riga, lo `snapshot` delle righe `file-history-snapshot` (con la mappa `trackedFileBackups`, indicizzata
per percorso del file) e il `backup` delle righe `file-history-delta`. Le liste sono contrattuali e
verificate da `TranscriptRewriterTest`:

| Lista | Chiavi | Trattamento |
|---|---|---|
| `ID_KEYS` | `sessionId`, `session_id` | nuovo id, solo con *Duplicate* |
| `CWD_KEYS` | `cwd` | `PathMapper.mapCwd()` |
| `FILE_PATH_KEYS` | `realParentDir`, `trackingPath` (+ chiavi di `trackedFileBackups`) | `PathMapper.mapFile()` |
| `ISO_TIMESTAMP_KEYS` | `timestamp`, `backupTime` | traslazione nella stessa forma |
| `EPOCH_MILLIS_KEYS` | `startTime` | traslazione dell'epoch in millisecondi |

**Niente altro.** `message`, `toolUseResult`, `attachment` e ogni altra chiave contengono ciò che si
sono detti utente e Claude e non vengono mai visitati (vincolo §1.3), nemmeno quando contengono un
percorso o un timestamp. Allargare una lista significa rischiare di riscrivere un valore che
appartiene alla conversazione: va fatto solo con un test che dimostri il contrario.

Una riga in cui nessun campo cambia resta **byte per byte** com'era. Le altre vengono riemesse con
Gson configurato con `serializeNulls()` e `disableHtmlEscaping()`, altrimenti i campi `null`
sparirebbero e `<`, `>`, `=`, `'`, `&` diventerebbero `\u003c`… I fine riga si conservano, compreso
l'a-capo finale: senza, la prima riga accodata da Claude Code alla ripresa si incollerebbe
all'ultima e corromperebbe il JSONL. Se nulla va riscritto (nessun aggancio, nessuno spostamento,
nessun nuovo id) il transcript è copiato così com'è.

### 4.4 Percorsi

`ClaudePaths.normalizeProjectPath()` porta la cartella di destinazione nella forma in cui Claude
Code registra i percorsi: separatore dello stile del percorso (lettera di unità o `\` significa
Windows; IntelliJ comunica `C:/Users/...`, Claude Code scrive `C:\Users\...`) e nessun separatore
finale, salvo una radice nuda. La lettera di unità **non** cambia maiuscola: a differenza della CLI di
Copilot, Claude Code la conserva com'è.

`PathMapper(sourceRoot, target)`:

* `mapCwd()` — una directory sotto `sourceRoot` conserva il resto relativo, qualunque altra diventa
  `target`: indica una cartella che esiste solo sulla macchina di origine;
* `mapFile()` — un file sotto `sourceRoot` conserva il resto relativo, qualunque altro resta com'è:
  ricondurlo a `target` farebbe scrivere a un checkpoint un file al posto di una cartella;
* il resto conservato prende il separatore di `target`; il confronto ignora maiuscole/minuscole e la
  differenza fra `\` e `/`; `...\Demo` non è mai antenato di `...\Demo2`.

`encodeProjectPath()` sostituisce ogni carattere fuori da `[A-Za-z0-9]`, lettere non ASCII comprese,
come fa Claude Code.

### 4.5 Timestamp

Tutti i timestamp di una sessione sono traslati dello **stesso** scarto, calcolato **per sessione**
dal suo ultimo messaggio: la sessione termina nel momento dell'import e compare in cima all'elenco,
mentre la spaziatura fra i messaggi resta invariata e nessun timestamp finisce nel futuro. I file
ripristinati hanno come data di modifica il momento dell'import.

`TimestampShift` riconosce i valori **per forma**, non per nome del campo, e li riscrive nella stessa
forma in cui li ha letti: ISO-8601 UTC con suffisso `Z` e lo stesso numero di cifre decimali (anche
zero), epoch in millisecondi a 13 cifre. Un valore che non corrisponde a nessuna delle due forme resta
**invariato**: meglio un timestamp non traslato che una riga corrotta.

---

## 5. Diagnostica e messaggi

### 5.1 La checklist di visibilità

`ImportDiagnostics` produce **tredici** controlli `OK`/`KO`, numerati esattamente così
(`ImportDiagnostics.CHECK_COUNT`). Il numero fa parte del contratto: è il riferimento usato quando si
legge un log senza avere accesso alla macchina, e la notifica lo riporta per ogni `KO`.

| # | Controllo |
|---|---|
| 1 | il transcript esiste, è leggibile e non è vuoto |
| 2 | ogni riga del transcript è JSON valido |
| 3 | ogni id di sessione (`ID_KEYS`) coincide con il nome del file |
| 4 | il transcript è nella cartella di `projects/` che corrisponde alla cartella agganciata (o alla prima `cwd`) |
| 5 | è registrata una `cwd` |
| 6 | ogni `cwd` è la cartella agganciata o si trova al suo interno |
| 7 | ogni `cwd` è in forma canonica (`normalizeProjectPath()` la lascia invariata) |
| 8 | la cartella della `cwd` esiste su questa macchina |
| 9 | c'è almeno un messaggio `user`/`assistant` fuori dalle sidechain |
| 10 | il transcript termina con un a-capo |
| 11 | nessun `cwd` né percorso della cronologia dei file punta ancora sotto la radice di origine |
| 12 | l'ultimo timestamp cade al momento dell'import (±1 minuto) e nessuno è nel futuro |
| 13 | ogni `backupFileName` richiamato dalle righe di cronologia esiste in `file-history/<id>/` |

Il **#4** è quello decisivo per l'elenco: tutti gli altri possono essere `OK` e la conversazione
restare invisibile, perché Claude Code non legge quella cartella. Il **#13** decide se checkpoint e
`/rewind` funzionano. La diagnosi rilegge tutto da disco e si chiude con il confronto con la sessione
più recente già presente nella cartella di destinazione (versione di Claude Code e chiavi della prima
riga di conversazione).

### 5.2 Messaggi

* il suggerimento *"Close the Claude Code sessions that use these files and export again"* va
  mostrato **solo** quando un file non è stato davvero letto, che è l'unico caso che risolve;
* il suggerimento dopo l'import spiega che non serve riavviare e come ritrovare la sessione
  (`claude --resume` dalla cartella agganciata);
* ogni import scrive `<log dell'IDE>/claude-sessions-import/import-yyyyMMdd-HHmmss.log`, senza
  opzioni da abilitare, con ambiente, contesto, sotto-log per sessione, la checklist di §5.1,
  confronto con la sessione nativa e riepilogo. Valori stringa troncati a 300 caratteri, ultimi 20
  file conservati, nessun testo di conversazione: le sessioni si identificano per id.

---

## 6. Localizzazione

Ogni testo del plugin — sottomenu e azioni del menu `Tools`, dialoghi di export/import, colonne e
stato della tabella delle sessioni, politiche di conflitto, notifiche e loro azioni, avvisi di
export/import, messaggi di errore, testi di avanzamento, titoli delle sezioni del log e descrizioni
dei controlli di §5.1 — passa da `ClaudeSessionsBundle`. Gruppo e azioni non hanno testi in chiaro in
`plugin.xml`: li prende dal `<resource-bundle>` con le chiavi convenzionali (`group.<id>.text`,
`action.<id>.text`, `action.<id>.description`), e il gruppo (`ClaudeSessionsActionGroup`) e le azioni
li reimpostano nel proprio `update()` con la risoluzione di `ClaudeSessionsBundle`, che segue anche la
lingua del sistema operativo. Il gruppo di notifiche usa `bundle`/`key`.

La risoluzione è uguale per ogni chiave, in due passaggi: prima la lingua di visualizzazione dell'IDE
quando è una localizzazione esplicita (non l'inglese), poi le impostazioni internazionali del sistema
operativo; altrimenti inglese. Oltre all'inglese: italiano, francese, tedesco, spagnolo, portoghese,
giapponese, cinese, coreano. Per aggiungerne una basta creare
`src/main/resources/messages/ClaudeSessionsBundle_<lingua>.properties` con le stesse chiavi del file
inglese. `MessageFormat` si applica solo alle chiavi con segnaposto: solo lì un apostrofo va
raddoppiato. `ClaudeSessionsBundleTest` verifica che ogni traduzione abbia esattamente le chiavi del
file inglese e che gli apostrofi rispettino questa regola.

Non si traducono il nome del plugin, i comandi (`claude --resume`) e, nel log, chiavi, valori e
numeri dei controlli.

---

## 7. Versione e rilascio

### 7.1 Icone

Due asset distinti, perché servono a due scopi diversi, entrambi vettoriali:

* `META-INF/pluginIcon.svg` e `pluginIcon_dark.svg` — il **logo** 40×40 mostrato su *Settings |
  Plugins* e sul Marketplace: anello nel colore di Claude Code, freccia verde di import verso il basso
  a sinistra, freccia blu di export verso l'alto a destra, contrapposte, e al centro un prompt di
  terminale per la CLI a cui appartengono le sessioni. Nessuna nuvola di chat, nessun marchio di terzi.
* `icons/claudeSessions.svg` e `claudeSessions_dark.svg` — l'**icona d'azione** 16×16 usata nei
  menu: a 16 px l'emblema completo sarebbe illeggibile, quindi riprende solo le due frecce
  contrapposte, con gli stessi colori del logo. È dichiarata su gruppo e azioni in `plugin.xml`, e il
  loro `update()` imposta `ActionUtil.SHOW_ICON_IN_MAIN_MENU`, così compare anche nel menu principale di
  macOS, che altrimenti nasconde le icone.

IntelliJ cerca il logo del plugin con `PluginLogo`: in una cartella installata dentro i jar di `lib/`,
in un file ZIP (il dialogo *Install Plugin from Disk*) alla radice dell'archivio. Il logo resta dentro
il jar, come prevede il formato di distribuzione: nel dialogo di installazione da disco compare quindi
l'icona generica, mentre il plugin installato e la pagina del Marketplace mostrano il logo. Non va
aggiunto un `META-INF/` alla radice dello ZIP: lo ZIP di un plugin contiene una sola cartella radice.

### 7.2 Versione

`gradle.properties` → `pluginVersion=0.0.1`: il progetto è in sviluppo e non ha ancora versioni
rilasciate sul Marketplace.

`CHANGELOG.md` contiene una sezione **datata** per versione, la più recente in cima:
`## [0.0.1] - 2026-09-23` e `## [0.0.0] - 2026-09-23`. Le sezioni precedenti non si cancellano mai. La
data è obbligatoria:
alimenta l'intestazione `[versione] - [data]` del riquadro **What's New**. La sezione
`## [Unreleased]` resta vuota, perché senza data non potrebbe alimentare quell'intestazione.

### 7.3 Licenze, privacy e marchi

* il plugin è MIT (`LICENSE`), che è anche la licenza d'uso da indicare al Marketplace insieme al link
  al sorgente;
* lo ZIP include solo il proprio jar e `gson` (Apache 2.0): la dipendenza transitiva
  `error_prone_annotations` è esclusa perché serve solo a compilare Gson. Il jar porta in
  `META-INF/licenses/` `LICENSE`, `THIRD_PARTY_NOTICES.md` e `Apache-2.0.txt`: ogni nuova libreria
  inclusa va aggiunta a `THIRD_PARTY_NOTICES.md` con la sua licenza;
* il plugin non raccoglie né trasmette dati (`PRIVACY.md`): un'eventuale raccolta richiederebbe il
  consenso esplicito dell'utente e l'aggiornamento dell'informativa;
* la descrizione dichiara che Claude e Claude Code sono marchi di Anthropic e che il progetto non è
  affiliato; i link a licenza, privacy, sorgente e issue tracker puntano al branch `main` del
  repository.

### 7.4 Compatibilità

`since-build` = `261`, `until-build` aperto. `verifyPlugin` verifica le build:

| IDE | Build |
|---|---|
| IntelliJ IDEA 2026.1.5 | IU-261.27258.48 |
| IntelliJ IDEA 2026.2.3 | IU-262.10968.63 |
| IntelliJ IDEA 2026.3 EAP | IU-263.5153.40 |

con `failureLevel = ALL`: qualunque segnalazione del Plugin Verifier, deprecazioni e idoneità al
caricamento dinamico comprese, fa fallire la build.

### 7.5 Firma e pubblicazione

Firma e pubblicazione leggono **solo** variabili d'ambiente (`CERTIFICATE_CHAIN`, `PRIVATE_KEY`,
`PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN`): nessuna credenziale entra nel repository. Il canale è
dedotto da `pluginVersion`. Procedura completa nel README, sezione *Pubblicazione sul JetBrains
Marketplace*.

---

## 8. Checklist di verifica finale

1. `./gradlew test` verde.
2. `./gradlew patchPluginXml`: in `build/tmp/patchPluginXml/plugin.xml` il `<name>` è
   `Session Porter for Claude Code` e `<change-notes>` inizia con `[0.0.1] - 2026-09-23`.
3. `./gradlew buildPlugin`: lo ZIP si chiama `session-porter-for-claude-code-0.0.1.zip` e il jar che contiene
   include `icons/claudeSessions.svg`, `icons/claudeSessions_dark.svg`, `META-INF/pluginIcon.svg`,
   `META-INF/pluginIcon_dark.svg`, i dieci `messages/ClaudeSessionsBundle*.properties` e
   `META-INF/licenses/`; accanto al jar, in `lib/`, c'è solo `gson-2.11.0.jar`.
4. `./gradlew verifyPlugin` verde: **Compatible** su tutte le build di §7.4, senza alcuna
   segnalazione.
5. L'id del `<notificationGroup>` in `plugin.xml` coincide con `ClaudeNotifications.GROUP_ID`
   (`ClaudeSessionsBundleTest`).
6. `core/` non importa nulla da `com.intellij.*`.
7. Ogni traduzione ha esattamente le chiavi del file inglese (`ClaudeSessionsBundleTest`).
8. `README.md` e `CHANGELOG.md` descrivono ogni comportamento osservabile.
9. Nessun riferimento a versioni del plugin diverse dalla 0.0.1 in codice, documentazione e messaggi
   utente, salvo lo storico di `CHANGELOG.md` e l'informativa privacy, che vale dalla 0.0.0.
10. `<vendor>` ha `url` ed `email` validi, e i link della descrizione rispondono sul branch `main`.
