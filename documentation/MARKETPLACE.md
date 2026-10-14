# Scheda del plugin sul JetBrains Marketplace

Tutto ciò che serve per compilare il modulo di caricamento e per pubblicare una versione. I testi da
incollare sono in inglese, perché sono quelli che vede chi cerca il plugin.

## Dati del plugin

| Campo | Valore |
|---|---|
| Nome | Session Porter for Claude Code |
| Plugin ID | `com.github.fabiopelliccia.claudesessionsimportexport` (non cambia mai: identifica il plugin) |
| Vendor | Fabio Pelliccia, <fabio.pelliccia@gmail.com>, <https://github.com/fabiopelliccia/claudsessionsmanager> |
| Compatibilità | `since-build` 261 (IntelliJ IDEA 2026.1), `until-build` non impostato; vale per ogni IDE basato su IntelliJ Platform |
| Licenza | MIT, <https://github.com/fabiopelliccia/claudsessionsmanager/blob/main/LICENSE> |
| Codice sorgente | <https://github.com/fabiopelliccia/claudsessionsmanager> |
| Issue tracker | <https://github.com/fabiopelliccia/claudsessionsmanager/issues> |
| Informativa privacy | <https://github.com/fabiopelliccia/claudsessionsmanager/blob/main/PRIVACY.md> (nessun dato raccolto né trasmesso) |
| Prezzo | gratuito |
| Logo | `src/main/resources/META-INF/pluginIcon.svg` e `pluginIcon_dark.svg`, già nel pacchetto: il Marketplace li legge da lì |

Descrizione ("Overview") e "What's New" **non si scrivono nel modulo**: vengono letti dal pacchetto,
rispettivamente da `<description>` in `plugin.xml` e dalla sezione della versione in `CHANGELOG.md`
(`<change-notes>`).

## Testi per il modulo

**Categoria / tag suggeriti:** Tools Integration, Productivity; tag `claude`, `claude-code`, `ai`,
`export`, `import`, `backup`, `sessions`.

**Descrizione breve (una riga):**

> Export and import Claude Code chat sessions to move them to another machine or another user.

**Cosa NON dire:** il nome non contiene "Claude" come marchio proprio ma come riferimento al prodotto
supportato; la descrizione riporta già la nota sui marchi ("independent project, not affiliated with
Anthropic"). Non sostituirla e non usare i loghi di Anthropic come icona.

## Screenshot

Il Marketplace accetta fino a 10 immagini (PNG/JPG, almeno 1280×800 consigliato) e **non** le legge dal
pacchetto: vanno caricate a mano nella scheda. Da fare (richiedono l'IDE, per questo non sono
nel repository):

1. *Tools → Claude Code sessions → Export Sessions...*, con l'elenco delle sessioni.
2. Il dialogo di import, con l'opzione *Attach the imported sessions to this folder*.
3. La notifica di fine import, con il comando `claude --resume`.

Prima di fare lo screenshot togli dall'elenco i titoli delle conversazioni private.

## Primo caricamento (manuale)

Il primo caricamento di un plugin nuovo passa per il sito, non per Gradle, e per una **revisione manuale**
di JetBrains (alcuni giorni lavorativi).

1. Crea lo ZIP: `./gradlew clean test buildPlugin verifyPlugin` (su Windows con il `JAVA_TOOL_OPTIONS` descritto
   nel README, *Nota per Windows*). Lo ZIP è in `build/distributions/`.
2. Accedi con il tuo JetBrains Account su <https://plugins.jetbrains.com/plugin/add>.
3. Carica lo ZIP, indica licenza MIT con il link a `LICENSE`, codice sorgente e issue tracker (tabella sopra)
   e, **una volta per account**, la dichiarazione *trader / non-trader* richiesta dalla normativa europea.
4. Carica gli screenshot e invia in revisione.

Dopo l'approvazione le versioni successive si possono caricare dallo stesso modulo oppure con
`./gradlew signPlugin publishPlugin`.

## Firma e pubblicazione da riga di comando

`signPlugin` e `publishPlugin` leggono solo variabili d'ambiente (nessun segreto nel repository):

| Variabile | Come ottenerla |
|---|---|
| `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD` | `openssl genpkey -aes-256-cbc -algorithm RSA -out private_encrypted.pem -pkeyopt rsa_keygen_bits:4096`, poi `openssl rsa -in private_encrypted.pem -out private.pem`, poi `openssl req -key private.pem -new -x509 -days 365 -out chain.crt`; contenuto dei file in PEM ([guida ufficiale](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html)) |
| `PUBLISH_TOKEN` | <https://plugins.jetbrains.com/author/me/tokens> → *Generate Token* |

Da PowerShell, per la sessione corrente (e non scritte in nessun file del progetto):

```powershell
$env:CERTIFICATE_CHAIN = Get-Content chain.crt -Raw
$env:PRIVATE_KEY = Get-Content private.pem -Raw
$env:PRIVATE_KEY_PASSWORD = '<passphrase>'
$env:PUBLISH_TOKEN = '<token>'
.\gradlew.bat signPlugin publishPlugin
```

Conserva la chiave privata fuori dal repository: se la perdi non puoi firmare gli aggiornamenti con la
stessa identità.

## Dopo la pubblicazione

Controlla sulla pagina del plugin: *Overview*, *What's New* (deve essere la sezione della versione appena
rilasciata), compatibilità e canale (`default` per una versione senza suffisso).
