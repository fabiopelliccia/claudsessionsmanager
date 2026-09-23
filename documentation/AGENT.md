# Brief originale — Claude Code sessions

Documento di origine del progetto: raccoglie, nella forma in cui sono stati espressi, i requisiti che
hanno portato alla **0.0.0**, la versione di sviluppo attuale. Non è una specifica tecnica: la
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
* **Versione di sviluppo.** Il progetto è ancora in sviluppo: la versione è `0.0.0`.

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
