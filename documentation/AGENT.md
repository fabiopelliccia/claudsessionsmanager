# Brief originale — Session Porter for Claude Code

Documento di origine del progetto: raccoglie, nella forma in cui sono stati espressi, i requisiti che
hanno portato alla **0.0.0**, la prima versione di sviluppo, e le richieste successive fino alla
**0.0.3**, quella attuale. Non è una specifica tecnica: la
specifica operativa, con vincoli, architettura e checklist, è `documentation/TASK.md`.

Il progetto è il gemello di *Github Copilot sessions*, da cui questi due documenti sono stati
ricavati: stessa idea e stessa struttura, adattate a come Claude Code conserva le proprie sessioni.

## Perché il plugin esiste

Le sessioni di chat di Claude Code restano legate alla macchina e alla cartella su cui sono nate. Non
esiste un modo ufficiale per portarle altrove — su un secondo PC, a un collega, o sulla stessa
postazione dopo un reset — né per sceglierne solo alcune. Il plugin serve a esportarle in un archivio
portabile e a ripristinarle dove servono.

## Requisiti richiesti

* **Export selettivo.** Elencare tutte le conversazioni presenti in locale e permettere di scegliere
  puntualmente quali salvare in un archivio ZIP.
* **Import con gestione dei conflitti.** Rileggere l'archivio, segnalare le sessioni già presenti e
  poter decidere se saltarle, sostituirle o importarle come copia.
* **Le sessioni importate devono comparire davvero.** È il requisito che ha guidato tutto il resto:
  un import che scrive i file corretti ma lascia la conversazione invisibile a `claude --resume` non
  vale nulla. Da qui l'aggancio alla cartella di progetto della macchina di destinazione e la
  normalizzazione dei percorsi. Una sessione ripristinata deve anche restare *riprendibile*: con la
  cronologia dei file, perché checkpoint e `/rewind` continuino a funzionare.
* **Fedeltà del transcript.** Oltre ai campi che descrivono la macchina, nulla del transcript deve
  cambiare: il testo della conversazione, ma anche i campi `null`, i caratteri e i fine riga.
* **Datazione locale.** Una sessione importata va datata come se fosse appena avvenuta sulla macchina
  di destinazione, non con la data di quella di origine, così compare in cima all'elenco.
* **Log diagnostico dell'import.** Ogni import deve produrre un file di log che spieghi con
  precisione cosa ha fatto e, soprattutto, perché una sessione ripristinata comparirebbe o non
  comparirebbe in `claude --resume` — senza bisogno di accedere alla macchina su cui è avvenuto.
* **Localizzazione.** Ogni testo mostrato all'utente — menu, dialoghi, messaggi, notifiche — deve
  seguire la lingua impostata, con le lingue più diffuse.
* **Icona propria.** Un'icona coerente con la funzione del tool: anello, freccia verde di import e
  freccia blu di export contrapposte, senza nuvole di chat, con variante per i temi scuri.
* **Overview della pagina plugin.** Deve contenere i ringraziamenti ad Antonio Petricca.
* **What's New della pagina plugin.** Deve aprirsi con `[VERSIONE] - [DATA]` e riportare di seguito
  ogni aggiunta o modifica di quella versione.
* **Versione di sviluppo.** Il progetto è ancora in sviluppo: la prima versione è `0.0.0`.

### Richieste della 0.0.1

* **Icona nel menu Tools.** La voce del plugin nel menu *Tools* deve mostrare la sua icona.
* **Logo del plugin al posto dell'icona generica.** Nella pagina dei plugin deve comparire il logo del
  progetto, non l'icona predefinita.
* **Riavvio dopo l'installazione e dopo l'import.** Se l'IDE va riavviato, il plugin deve chiederlo;
  se non serve, non deve chiederlo. Verificato: non serve in nessuno dei due casi.
* **Versione `0.0.1`**, con le novità aggiunte al changelog senza togliere quelle della `0.0.0`.

### Richieste della 0.0.2

* **Nome della sessione comprensibile.** Nel pannello di export la colonna *Sessione* mostrava markup
  tecnico (comandi slash, avvisi, promemoria di sistema) al posto di un testo leggibile: deve mostrare
  un nome comprensibile, lo stesso in export e in import.
* **Versione `0.0.2`**, con le novità aggiunte al changelog senza togliere quelle delle versioni
  precedenti.

### Richieste della 0.0.3

* **Niente riferimenti alla mia utenza né ai miei percorsi nell'export.** L'archivio esportato non deve
  contenere l'email dell'account né l'identità git di chi esporta.
* **Import senza conflitti fra macchina e utente diversi.** Il contenuto esportato dal PC 1 deve poter
  essere importato sul PC 2 (macchina e utente diversi) senza conflitti e in modo corretto.
* **Datazione locale al PC 2, non al PC 1.** Le sessioni importate dal PC 1 vanno gestite come sessioni
  create localmente sul PC 2, con il timestamp di importazione del PC 2, non con quello di creazione
  del PC 1. Verificato: era già così dalla 0.0.0 (vedi *Datazione locale* sopra); riconfermato con un
  test end-to-end dedicato al trasferimento fra due macchine.
* **Versione `0.0.3`**, con le novità aggiunte al changelog senza togliere quelle delle versioni
  precedenti.

## Dove è finito ciascun requisito

| Requisito | Documentazione |
|---|---|
| Export/import, policy, aggancio alla cartella | `README.md`, §2 di `TASK.md` |
| Visibilità in `claude --resume`, cronologia dei file | `README.md` → *Una sessione, un posto solo* e *Cartella di lavoro e visibilità*, §3 e §4 di `TASK.md` |
| Fedeltà del transcript | `README.md` → *Riscrittura del transcript*, §4.3 di `TASK.md` |
| Datazione locale | `README.md` → *Note operative*, §4.5 di `TASK.md` |
| Log diagnostico | `README.md` → *Log diagnostico dell'import*, §5 di `TASK.md` |
| Localizzazione | `README.md` → *Lingua dell'interfaccia*, §6 di `TASK.md` |
| Icona, Overview, What's New, versione | `plugin.xml`, `build.gradle.kts`, `gradle.properties`, §7 di `TASK.md` |
| Icona nel menu, logo, riavvii (0.0.1) | `README.md` → *Installazione e riavvii*, §3 e §7.1 di `TASK.md` |
| Nome della sessione (0.0.2) | `README.md` → *Il nome della sessione*, §2 di `TASK.md` |
| Privacy dell'export, import fra macchine e utenti diversi (0.0.3) | `README.md` → *Privacy dell'export*, `PRIVACY.md`, §4.3 e §4.6 di `TASK.md` |
