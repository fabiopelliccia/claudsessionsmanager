# Session Porter for Claude Code

Plugin IntelliJ (Kotlin) per **esportare e importare le sessioni di chat di
[Claude Code](https://claude.com/product/claude-code)** in un archivio ZIP portabile, pensato per
spostarle da una macchina (e da un utente) a un'altra.

Permette di scegliere puntualmente quali conversazioni esportare e quali ripristinare, di agganciarle
alla cartella di progetto della macchina di destinazione e di ritrovarle subito con
`claude --resume`, datate come se fossero appena avvenute.

> Versione di sviluppo **0.0.3**.

## Funzionalità

* **Tools → Claude Code sessions → Export Sessions...**
  Mostra tutte le sessioni presenti in locale (nome, cartella, branch, data, numero di messaggi,
  dimensione, id), con filtro di ricerca e selezione multipla tramite checkbox; salva la selezione in
  un file ZIP.
* **Tools → Claude Code sessions → Import Sessions...**
  Legge un archivio, elenca le sessioni contenute segnalando quelle già presenti e consente di
  scegliere quali importare e come gestire i conflitti:
  * *Skip* – ignora le sessioni già presenti;
  * *Replace* – sostituisce la sessione locale;
  * *Duplicate* – importa come nuova copia con un nuovo id (default).

  Il dialogo espone inoltre l'opzione **"Attach the imported sessions to this folder"**, spiegata
  in [Cartella di lavoro e visibilità](#cartella-di-lavoro-e-visibilità).

### Il nome della sessione

La colonna *Sessione* mostra lo stesso nome in export e in import. Le versioni attuali di Claude Code
non scrivono una riga `summary`, e la prima riga utente di una sessione di solito non è ciò che si è
chiesto ma il markup di un comando slash (`<command-name>/model</command-name>`), un avviso o un
promemoria di sistema. Il nome viene quindi scelto, nell'ordine, fra:

1. il titolo personalizzato, se la sessione è stata rinominata;
2. la riga `summary`, scritta dalle versioni precedenti di Claude Code;
3. il primo prompt davvero digitato, ripulito da markup dei comandi, output, promemoria di sistema e
   contesto dell'IDE (`<ide_opened_file>`…);
4. il comando slash con cui la sessione è iniziata, con i suoi argomenti, se non c'è alcun prompt;
5. l'ultimo prompt registrato da Claude Code (riga `last-prompt`).

Il testo viene ridotto a una riga e troncato a 140 caratteri; se la colonna è più stretta, passando
con il mouse sulla cella la si vede per intero. Una sessione senza nessuno di questi elementi compare come *(sessione senza titolo)*.
In import il nome viene ricalcolato dal transcript contenuto nell'archivio, quindi coincide con quello
dell'export anche per gli archivi prodotti dalla 0.0.0 e dalla 0.0.1.
* **Notifica di esito** per entrambe le operazioni. Dopo un import offre **"Show import log"** e,
  quando almeno una sessione è stata importata, **"Copy resume command"**.

Il sottomenu *Tools → Claude Code sessions* e le sue due voci mostrano l'icona del plugin anche nel
menu principale di macOS, dove IntelliJ nasconde le icone delle voci che non lo chiedono
esplicitamente (`ActionUtil.SHOW_ICON_IN_MAIN_MENU`).

## Installazione e riavvii

* **Dopo l'installazione non serve riavviare l'IDE.** Il plugin è idoneo al caricamento dinamico:
  IntelliJ lo carica subito (nel log dell'IDE compare
  `DynamicPluginsSupportImpl - load plugin 'Session Porter for Claude Code'`) e il menu *Tools* lo
  mostra immediatamente. Lo stesso vale per la disattivazione e la disinstallazione. Il Plugin
  Verifier lo conferma su ogni build verificata; se un giorno il caricamento dinamico non fosse
  possibile, sarebbe IntelliJ stesso a chiedere il riavvio.
* **Dopo un import non serve riavviare l'IDE.** Claude Code non tiene un elenco delle sessioni
  dentro l'IDE — nemmeno il plugin ufficiale *Claude Code [Beta]*, che apre la CLI nel terminale:
  `claude --resume` rilegge i transcript da disco ogni volta, quindi le sessioni importate compaiono
  subito. Vedi [Una sessione, un posto solo](#una-sessione-un-posto-solo).
* **Icona durante "Install Plugin from Disk".** Finché il plugin viene letto dallo ZIP, cioè nel
  dialogo di installazione da disco, IntelliJ cerca il logo alla radice dell'archivio e mostra
  l'icona generica dei plugin: il logo (`META-INF/pluginIcon.svg`) sta dentro il jar del plugin, come
  prevede il formato di distribuzione. Una volta installato, IntelliJ lo legge dal jar in `lib/`; sul
  Marketplace è il logo mostrato nella pagina del plugin. Se la pagina *Settings | Plugins* aperta
  durante l'installazione mostra ancora l'icona generica, basta riaprirla o riavviare l'IDE: è solo
  un'immagine già caricata, il plugin funziona comunque.

## Cosa viene esportato

Una sessione di Claude Code vive in **tre** punti della sua home (`~/.claude`), tutti inclusi
nell'archivio:

| Origine | Contenuto |
|---|---|
| `~/.claude/projects/<cartella-progetto>/<sessionId>.jsonl` | il **transcript**: la conversazione, una riga JSON per evento |
| `~/.claude/projects/<cartella-progetto>/<sessionId>/` | i dati **ausiliari** della sessione (esecuzioni dei subagent, risultati degli strumenti, ...) |
| `~/.claude/file-history/<sessionId>/` | la **cronologia dei file**: i backup su cui si basano i checkpoint e `/rewind` |

La posizione della home può essere sovrascritta con la variabile d'ambiente `CLAUDE_CONFIG_DIR`
(la stessa che usa Claude Code) o con la property di sistema `claude.home`.

**Non** vengono esportati, perché non servono a ritrovare né a riprendere una sessione:
`~/.claude/history.jsonl` (la cronologia dei prompt digitati, che contiene il loro testo),
`session-env/`, `shell-snapshots/` e `sessions/` (stato dei processi in esecuzione sulla macchina di
origine).

### Formato dell'archivio

```
manifest.json                        # versione formato, data, producer, elenco sessioni
sessions/<id>/transcript.jsonl       # il transcript
sessions/<id>/aux/...                # copia fedele di projects/<cartella>/<id>/, se presente
sessions/<id>/file-history/...       # copia fedele di file-history/<id>/, se presente
```

L'archivio è **autodescrittivo**: `manifest.json` dichiara la versione del formato, oggi **1**, ed
elenca solo le sessioni effettivamente scritte. Una entry che manca — ad esempio la cronologia dei file
di una sessione che non ha mai modificato file — viene gestita per la sua assenza, non in base al
numero di formato, che serve solo a rifiutare un layout incomprensibile a questa build. Dalla 0.0.3 il
manifest non registra più la home Claude Code della macchina di origine: era solo informativa e conteneva
sempre il nome utente del sistema operativo di chi ha esportato.

### Privacy dell'export

Oltre al transcript stesso, Claude Code scrive in ogni sessione due tipi di riga `attachment` per
proprio conto, mai perché l'utente le abbia scritte: una riga `session_context` con l'indirizzo email
dell'account e l'esito di `git status` (che include il nome configurato in `user.name`), e una riga
`environment` con, fra l'altro, la cartella di lavoro temporanea che Claude Code usa per la sessione
(`scratchpadDirectory`), sempre sotto la cartella temporanea del sistema operativo e quindi sempre con
il nome dell'account che l'ha creata.

A partire dalla 0.0.3 l'export rimuove questi due elementi da **ogni** file `.jsonl` che scrive — il
transcript principale e ogni transcript di subagent nella cartella ausiliaria — prima di scriverli
nell'archivio: l'archivio stesso non li contiene mai, indipendentemente da chi lo legga e da se venga
mai importato. La stessa email e lo stesso testo di `git status`, quando Claude Code li ripete anche nel
promemoria di sistema già pronto che tiene accanto all'attachment, vengono tolti anche lì, lasciando
intorno il resto del blocco intatto. La notifica di export riporta quante righe sono state ripulite.

La cartella di lavoro `environment.workingDirectory` (e le eventuali cartelle aggiuntive) **non** viene
toccata qui: è un percorso di progetto vero e proprio, non un dettaglio dell'esportatore, e segue invece
la stessa rimappatura di `cwd` descritta in [Riscrittura del transcript](#riscrittura-del-transcript),
quando la sessione viene agganciata a una cartella in fase di import.

**Cosa non viene toccato, e perché.** Se l'utente ha digitato la propria email o il proprio nome in una
conversazione, o se Claude ha riletto un file che li contiene, quel testo resta nel transcript esportato
esattamente come nella conversazione originale: è contenuto della conversazione, non un dettaglio
tecnico della macchina, e il plugin non lo tocca mai, per lo stesso motivo per cui non riscrive mai
`message` o `toolUseResult` (vedi [Riscrittura del transcript](#riscrittura-del-transcript)). Provare a
individuare e cancellare "informazioni che sembrano personali" dentro al testo della conversazione
significherebbe rischiare di corrompere codice o dati legittimi che le contengono per altri motivi.

## Una sessione, un posto solo

A differenza di altri assistenti, Claude Code **non** tiene un secondo registro della sessione dentro
l'IDE: l'elenco di `/resume` e di `claude --resume` è costruito leggendo ogni volta i transcript della
cartella di progetto. Ne seguono due conseguenze:

* ripristinare i file sopra elencati basta perché la sessione sia di nuovo elencata e riprendibile;
* **non serve alcun riavvio**, né dell'IDE né di Claude Code: la sessione compare al primo
  `claude --resume` lanciato dalla cartella a cui è stata agganciata. Per questo la notifica di import
  offre di copiare il comando di ripresa (`claude --resume <id>` se è stata importata una sola
  sessione, altrimenti `claude --resume`) invece di un riavvio.

La cronologia dei file non è necessaria per elencare la sessione, ma senza di essa i checkpoint e
`/rewind` non possono ripristinare i file modificati: per questo viaggia sempre con il transcript.

## Cartella di lavoro e visibilità

Claude Code cerca le sessioni in una cartella di `~/.claude/projects/` il cui nome è ricavato dalla
directory da cui viene avviato: ogni carattere diverso da `A-Z`, `a-z`, `0-9` diventa `-`
(`C:\Users\bob\work\demo` → `C--Users-bob-work-demo`). Una sessione esportata da
`C:\Users\alice\progetti\demo` e importata così com'è su un altro PC, dove lo stesso progetto si trova
in `C:\Users\bob\work\demo`, finirebbe in una cartella che Claude Code non legge mai.

Per questo il dialogo di import offre l'opzione **"Attach the imported sessions to this folder"**,
pre-compilata con il progetto aperto e pre-selezionata quando la cartella registrata nell'archivio è
diversa da quella del progetto. Quando è attiva:

* il transcript viene scritto nella cartella di `projects/` che corrisponde alla cartella scelta;
* ogni `cwd` viene riscritta: una directory **sotto** la radice di origine conserva il proprio
  percorso relativo, qualunque altra (ad esempio un secondo progetto in cui la sessione era stata
  spostata a metà) viene sostituita dalla cartella scelta, perché indica un percorso che esiste solo
  sulla macchina di origine;
* i percorsi dei file tracciati dalla cronologia (`trackedFileBackups`, `realParentDir`,
  `trackingPath`) vengono rimappati con la stessa regola se si trovano sotto la radice di origine; gli
  altri restano come sono, perché ricondurre un file alla radice del progetto farebbe scrivere a un
  checkpoint un file al posto di una cartella.

La cartella scelta viene **normalizzata** nella forma in cui Claude Code registra i percorsi: IntelliJ
comunica un progetto Windows come `C:/Users/bob/work/demo`, Claude Code scrive
`C:\Users\bob\work\demo`. Il separatore segue lo stile del percorso di destinazione (lettera di unità
o `\` significa Windows), anche per la parte relativa conservata, e il separatore finale viene
rimosso. Il confronto con la radice di origine ignora maiuscole/minuscole e la differenza fra `\` e
`/`.

Disattivando l'opzione la sessione mantiene la cartella originale e sarà elencata solo avviando
Claude Code esattamente da quel percorso.

### Riscrittura del transcript

La riscrittura è **strutturale** (JSON per JSON, chiave per chiave), mai una sostituzione di testo, e
tocca solo i campi che descrivono la macchina, nelle posizioni in cui Claude Code li scrive: il primo
livello della riga, lo `snapshot` delle righe `file-history-snapshot` (con la sua mappa
`trackedFileBackups`), il `backup` delle righe `file-history-delta` e lo `snapshot` di una riga
`attachment` di tipo `environment` (le cartelle di lavoro della sessione, vedi
[Privacy dell'export](#privacy-dellexport) per il resto di quella riga).

| Campo | Trattamento |
|---|---|
| `sessionId`, `session_id` | sostituiti dal nuovo id, solo quando la sessione viene duplicata |
| `cwd`, `environment.snapshot.workingDirectory` | rimappati alla cartella scelta, anche se fuori dalla radice di origine |
| `realParentDir`, `trackingPath`, chiavi di `trackedFileBackups`, `environment.snapshot.additionalWorkingDirectories` | rimappati se sotto la radice di origine, lasciati com'erano altrimenti |
| `timestamp`, `backupTime` (ISO-8601) | traslati, nella stessa forma in cui sono stati letti |
| `startTime` (epoch in millisecondi, riga `cost-state`) | traslato |

Tutto il resto — in particolare `message`, `toolUseResult`, il resto di ogni `attachment` e qualunque
percorso vi compaia — è ciò che si sono detti utente e Claude e **non viene mai toccato**. Le righe in cui nessuno
di quei campi cambia vengono copiate byte per byte; le altre vengono riscritte conservando i campi
`null`, senza trasformare `<`, `>`, `=`, `'`, `&` in sequenze `\u003c`, e mantenendo i fine riga,
incluso l'a-capo finale, di cui Claude Code ha bisogno per accodare righe quando la sessione viene
ripresa. Le righe illeggibili restano identiche.

## Gestione dei conflitti

Una sessione è **già presente** quando un transcript con lo stesso id esiste in una **qualunque**
cartella di `~/.claude/projects/`, non solo in quella di destinazione: due sessioni con lo stesso id
in cartelle diverse sarebbero ambigue per `claude --resume <id>`.

* *Skip* lascia intatta la sessione locale;
* *Replace* rimuove la sessione locale — transcript, cartella ausiliaria e cronologia dei file,
  ovunque si trovi — e la sostituisce con quella dell'archivio, nella cartella scelta;
* *Duplicate* importa la sessione con un **nuovo id**, accanto a quella locale: importare due volte lo
  stesso archivio è quindi sempre sicuro. Il nuovo id viene scritto nel nome del file, nei campi
  `sessionId`/`session_id` e nella cartella della cronologia dei file.

## Verifica automatica dell'import

Al termine dell'import il plugin **rilegge da disco** ciò che ha appena scritto e lo mostra nella
notifica: nome della sessione, cartella a cui è agganciata e numero di messaggi. Subito dopo esegue
la diagnosi di visibilità descritta sotto e riporta nella notifica ogni controllo non superato, con il
suo numero. Un errore su una sessione non interrompe l'import delle altre: viene riportato nella
notifica e, con lo stack trace completo, nel log.

L'export segnala i file che non è riuscito a leggere invece di produrre in silenzio un archivio
incompleto, e solo in quel caso suggerisce di chiudere le sessioni di Claude Code che li usano e di
ripetere l'export.

## Log diagnostico dell'import

Ogni import scrive un file di log dettagliato in
`<cartella dei log dell'IDE>/claude-sessions-import/import-yyyyMMdd-HHmmss.log` (la cartella dei log
dell'IDE è quella aperta da *Help → Show Log in Explorer/Finder*). Il log **viene sempre scritto**,
senza alcuna opzione da abilitare: serve a capire, senza avere accesso alla macchina, perché una
sessione importata non compaia in `claude --resume`.

Contiene, in ordine:

* l'**ambiente**: versione del plugin e dell'IDE, JVM, sistema operativo, locale, lingua
  dell'interfaccia, percorsi della home di Claude Code e come sono stati risolti;
* il **contesto dell'operazione**: archivio, politica sui conflitti, cartella di destinazione richiesta
  e normalizzata, e il contenuto del manifest;
* per ogni sessione, un sotto-log con la decisione sul conflitto, lo scarto applicato ai timestamp, il
  numero di campi riscritti per tipo, i file ausiliari e di cronologia scritti e la verifica riletta da
  disco;
* una **diagnosi di visibilità** con esito esplicito `OK`/`KO` su tredici controlli numerati:

  | # | Controllo |
  |---|---|
  | 1 | il transcript esiste, è leggibile e non è vuoto |
  | 2 | ogni riga del transcript è JSON valido |
  | 3 | ogni id di sessione nel transcript coincide con il nome del file |
  | 4 | il transcript è nella cartella di progetto che Claude Code legge per la sua directory di lavoro |
  | 5 | è registrata una directory di lavoro (`cwd`) |
  | 6 | ogni `cwd` è la cartella agganciata o si trova al suo interno |
  | 7 | ogni `cwd` è in forma canonica (separatori della piattaforma, nessun separatore finale) |
  | 8 | la cartella della `cwd` esiste su questa macchina |
  | 9 | c'è almeno un messaggio utente o assistente fuori dalle sidechain |
  | 10 | il transcript termina con un a-capo |
  | 11 | nessun percorso riferito alla macchina punta ancora alla cartella di origine |
  | 12 | i timestamp cadono al momento dell'import e nessuno è nel futuro |
  | 13 | ogni backup della cronologia dei file richiamato dai checkpoint è presente |

  Il **#4** è quello decisivo per l'elenco: un transcript in un'altra cartella esiste su disco ma
  `claude --resume` non lo vede mai. Il **#13** decide se checkpoint e `/rewind` funzionano;
* un **confronto** con la sessione più recente già presente nella cartella di destinazione: versione
  di Claude Code e chiavi della prima riga di conversazione, utili a individuare un archivio che la CLI
  locale non si aspetta;
* un **riepilogo finale** con i conteggi, l'esito di ogni sessione e ogni controllo `KO`.

Titoli delle sezioni e descrizioni dei controlli seguono la lingua dell'interfaccia; chiavi, valori e
numeri dei controlli restano invariati, così due log restano confrontabili. Il file **non contiene
mai il testo delle conversazioni**, nemmeno il nome della sessione (che senza una riga `summary` è
un'anteprima del primo messaggio): solo id, percorsi, chiavi, conteggi, dimensioni e metadati, con i
valori stringa troncati a 300 caratteri. Ogni scrittura è *best-effort* e non può mai far fallire
l'import, nemmeno quando la cartella dei log non è scrivibile. Vengono conservati solo gli ultimi 20
file. La notifica riporta il percorso del file e offre l'azione **"Show import log"** — anche quando
l'import termina con un avviso o con un errore.

## Lingua dell'interfaccia

Ogni testo del plugin è tradotto: menu, dialoghi di export/import, colonne e stato della tabella
delle sessioni, politiche di conflitto, notifiche e loro azioni, avvisi, messaggi di errore,
avanzamento e sezioni del log. Ogni etichetta è risolta negli stessi due passaggi:

1. la lingua di visualizzazione dell'IDE, quando è una localizzazione esplicita (cioè quando è
   installato un language pack diverso dall'inglese);
2. le impostazioni internazionali del sistema operativo, così l'interfaccia parla la lingua della
   macchina anche su un IDE in inglese.

Se nessuna delle due è tradotta si ricade sull'inglese. Oltre all'inglese sono incluse italiano,
francese, tedesco, spagnolo, portoghese, giapponese, cinese e coreano. Per aggiungerne un'altra basta
creare `src/main/resources/messages/ClaudeSessionsBundle_<lingua>.properties` con le stesse chiavi
del file inglese; un test verifica che ogni traduzione abbia esattamente quelle chiavi e che gli
apostrofi siano raddoppiati solo dove `MessageFormat` lo richiede. Il nome del plugin,
**Session Porter for Claude Code**, non viene tradotto.

## Note operative

* Le sessioni importate vengono datate come appena avvenute su questo PC: tutti i timestamp di una
  sessione sono traslati dello **stesso** scarto, calcolato dal suo ultimo messaggio, così la sessione
  termina nel momento dell'import e compare in cima all'elenco di `claude --resume`, mentre la
  spaziatura fra i messaggi resta invariata. Anche i file ripristinati hanno come data di modifica il
  momento dell'import.
* L'import non richiede che Claude Code sia mai stato avviato sulla macchina di destinazione: crea le
  cartelle che servono.
* Si può esportare anche una sessione in corso: l'archivio la contiene fino all'ultima riga scritta.
* Per correggere un import precedente finito nella cartella sbagliata, ripetere l'import con politica
  *Replace* e l'opzione di aggancio attiva.
* È consigliabile non avere aperte in Claude Code le sessioni che si stanno sostituendo con *Replace*.

## Sviluppo

```bash
./gradlew test            # round-trip export/import, riscrittura, timestamp, diagnosi, traduzioni
./gradlew patchPluginXml  # plugin.xml finale in build/tmp/patchPluginXml (Overview e What's New)
./gradlew buildPlugin     # build/distributions/session-porter-for-claude-code-<versione>.zip
./gradlew runIde          # IDE di prova con il plugin installato
./gradlew verifyPlugin    # IntelliJ Plugin Verifier sulle IDE consigliate
```

`runIde` avvia l'IDE di prova con `-Dclaude.home=build/claude-home-test`: export e import lavorano su
quella cartella e non toccano mai la `~/.claude` reale dello sviluppatore. Per avere qualcosa da
esportare basta copiarvi sotto `projects/` (e `file-history/`) qualche sessione; `./gradlew clean` la
cancella insieme al resto di `build/`.

La versione è in `gradle.properties` (`pluginVersion`). Le note di rilascio si scrivono in
`CHANGELOG.md` nel formato *Keep a Changelog*: la sezione della versione corrente viene convertita in
HTML e inserita nel tag `<change-notes>` di `plugin.xml`, cioè nel riquadro **What's New** della
pagina del plugin. Il riquadro si apre con l'intestazione `[<versione>] - <data>`, ricavata
dall'intestazione `## [<versione>] - <data>` di `CHANGELOG.md` (che per questo deve riportare sempre
una data), seguita da tutte le voci di quella versione; la sezione `## [Unreleased]` resta vuota,
perché senza data non potrebbe alimentare quell'intestazione.

Requisiti: JDK 21, IntelliJ IDEA 2026.1 o successive.
Il plugin si installa da *Settings → Plugins → ⚙ → Install Plugin from Disk...* selezionando lo ZIP
prodotto in `build/distributions`.

## Pubblicazione sul JetBrains Marketplace

```bash
./gradlew verifyPlugin    # struttura del plugin + IntelliJ Plugin Verifier sulle IDE consigliate
./gradlew signPlugin      # firma l'archivio (build/distributions/*-signed.zip)
./gradlew publishPlugin   # carica l'archivio firmato sul Marketplace
```

`signPlugin` e `publishPlugin` **non contengono alcuna credenziale**: leggono esclusivamente
l'ambiente, quindi nessun segreto finisce nel repository.

| Variabile d'ambiente | Contenuto |
|---|---|
| `CERTIFICATE_CHAIN` | catena di certificati in formato PEM |
| `PRIVATE_KEY` | chiave privata in formato PEM |
| `PRIVATE_KEY_PASSWORD` | passphrase della chiave privata |
| `PUBLISH_TOKEN` | token generato da *JetBrains Hub → Marketplace → Personal access token* |

La coppia chiave/certificato si genera come descritto nella
[guida ufficiale alla firma dei plugin](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html);
il token si crea dal profilo sul [Marketplace](https://plugins.jetbrains.com/author/me/tokens).

Il canale di pubblicazione è dedotto da `pluginVersion`: una versione senza suffisso va sul canale
`default`, mentre una pre-release come `0.0.3-beta.1` va sul canale omonimo (`beta`), visibile solo a
chi lo ha aggiunto fra i repository dei plugin.

### Compatibilità

`since-build` vale `261` e `until-build` non è impostato, come raccomanda JetBrains: il plugin si
installa su ogni IDE basato su IntelliJ dalla 2026.1 in poi. `verifyPlugin` lo verifica con IntelliJ
Plugin Verifier sulle build indicate in `build.gradle.kts`:

| IDE | Build | Esito |
|---|---|---|
| IntelliJ IDEA 2026.1.5 | IU-261.27258.48 | Compatible |
| IntelliJ IDEA 2026.2.3 | IU-262.10968.63 | Compatible |
| IntelliJ IDEA 2026.3 EAP | IU-263.5153.40 | Compatible |

`failureLevel` è impostato a `ALL`: **qualunque** segnalazione del Verifier fa fallire la build —
problemi di compatibilità, struttura del plugin, dipendenze mancanti, API interne, sperimentali,
deprecate o pianificate per la rimozione, e un plugin che non si possa caricare senza riavvio. Il
plugin non dipende da altri plugin; la propria versione, per il log di import, la legge dal
`plugin.xml` incluso nel jar invece di interrogare `PluginManager`.

### Requisiti del Marketplace

Come il plugin soddisfa le
[JetBrains Marketplace Approval Guidelines](https://plugins.jetbrains.com/docs/marketplace/jetbrains-marketplace-approval-guidelines.html):

| Requisito | Dove |
|---|---|
| nome in caratteri latini, al massimo 30, senza "Plugin", "IntelliJ", "JetBrains" | `<name>` in `plugin.xml`, `pluginName` in `gradle.properties` |
| descrizione in inglese, corretta e senza link rotti | `<description>` in `plugin.xml` |
| vendor con sito ed email validi | `<vendor>` in `plugin.xml` |
| logo SVG 40×40, diverso dal template e dai loghi JetBrains | `META-INF/pluginIcon.svg`, `pluginIcon_dark.svg` |
| licenza (EULA) e link al sorgente per un plugin open source | [`LICENSE`](LICENSE) (MIT), repository GitHub, link nella descrizione |
| informativa privacy se si raccolgono dati personali | [`PRIVACY.md`](PRIVACY.md): nessun dato raccolto né trasmesso |
| change notes pertinenti | `CHANGELOG.md` → `<change-notes>` |
| versione semantica | `pluginVersion` in `gradle.properties` |
| compatibilità verificata con il Plugin Verifier, nessuna API interna | `verifyPlugin`, vedi *Compatibilità* |
| nessuna violazione di marchi di terzi | nota sui marchi nella descrizione e in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) |

Al primo caricamento, nel modulo del Marketplace vanno indicati: licenza *MIT* con il link a
`LICENSE`, il link al **codice sorgente** e all'**issue tracker** del repository e — una volta per
account — la dichiarazione *trader/non-trader* richiesta dalla normativa europea sui consumatori.

### Rilascio

Checklist prima di un rilascio:

1. aggiornare `pluginVersion` in `gradle.properties`;
2. aggiungere in `CHANGELOG.md` una sezione `## [<versione>] - <data>` **con la data**, lasciando
   vuota `## [Unreleased]`;
3. `./gradlew clean test verifyPlugin` senza errori;
4. fare il merge su `main` e il push: i link della descrizione (licenza, privacy, sorgente) puntano
   al branch `main` del repository e devono rispondere prima della pubblicazione;
5. `./gradlew signPlugin publishPlugin` con le quattro variabili d'ambiente impostate.

Il primo caricamento sul Marketplace passa per una **revisione manuale** di JetBrains e richiede un
`pluginId` univoco (`com.github.fabiopelliccia.claudesessionsimportexport`), una `<description>` e un
`<vendor>` compilati in `plugin.xml`, e una licenza: tutto è già presente in questo repository.

### Nota per Windows

Se la cartella temporanea è scritta in forma breve 8.3 (ad esempio
`C:\Users\ABCDEF~1\AppData\Local\Temp`), Java non riesce ad aprire i propri socket locali e Gradle, i
test e il Plugin Verifier si fermano con `Unable to establish loopback connection`. Basta indicare a
Java una cartella con percorso lungo prima di lanciare Gradle:

```bash
set JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Users\<utente>\AppData\Local\Temp
```

## Licenza, privacy e marchi

* Il plugin è distribuito con licenza [MIT](LICENSE) © Fabio Pelliccia: può essere usato, copiato,
  modificato e ridistribuito liberamente, anche per uso commerciale, mantenendo l'avviso di copyright.
* Lo ZIP del plugin include [Gson](https://github.com/google/gson), con licenza
  [Apache 2.0](licenses/Apache-2.0.txt): vedi [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
  Licenza, avviso e testo Apache sono anche dentro il jar, in `META-INF/licenses/`.
* Il plugin non raccoglie né trasmette alcun dato: vedi [`PRIVACY.md`](PRIVACY.md). Dalla 0.0.3
  l'export rimuove anche, dagli archivi che crea, l'email dell'account e l'identità git
  dell'esportatore: vedi [Privacy dell'export](#privacy-dellexport).
* Claude e Claude Code sono marchi di Anthropic, PBC; IntelliJ IDEA e JetBrains sono marchi di
  JetBrains s.r.o. Questo è un progetto indipendente, non affiliato né approvato da Anthropic o da
  JetBrains.

## Special thanks

Un ringraziamento sentito ad **Antonio Petricca**, che ha seguito il plugin fin dai primi passi: i
suoi suggerimenti ne hanno guidato le scelte e le sue prove sul campo hanno fatto emergere problemi
che nessun test avrebbe intercettato. Grazie per il tempo e per la cura che ci ha messo.
