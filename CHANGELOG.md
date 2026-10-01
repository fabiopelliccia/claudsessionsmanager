# Changelog

All notable changes to the Session Porter for Claude Code plugin are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.0.4] - 2026-10-01

First release published on the JetBrains Marketplace.

### Added

- Export and import of Claude Code sessions in a portable ZIP archive, to move them to another machine
  and another user: transcript, auxiliary folder (subagent runs, tool results) and the file history
  behind checkpoints and `/rewind` travel together. On import the sessions are attached to a local
  project folder, conflicts are resolved by skipping, replacing or importing a new copy, and the
  timestamps are shifted so the sessions show up first in `claude --resume`, with no restart.
- The archive never carries the exporting account's email, git identity or Claude Code scratch folder.
- Every import writes a diagnostic log with thirteen numbered visibility checks.
- The interface is available in English, Italian, French, German, Spanish, Portuguese, Japanese, Chinese
  and Korean.
- `CrossMachineTransferTest`, a battery of 14 end-to-end tests simulating the export on one machine
  (PC_1, user `alice`) and the import on another (PC_2, user `bob`) with different account names and
  paths: the source home is deleted and the archive moved before the import, and the sessions are
  checked the way `claude --resume` lists them - folder, working directories, name, message count,
  dates, checkpoints, subagents, no trace of the source user, every visibility check passing - across
  operating systems, accented and spaced names, repeated imports, a destination that never ran Claude
  Code and a round trip back to PC_1.

### Fixed

- Subagent transcripts (`<sessionId>/subagents/agent-*.jsonl`) are now rewritten on import exactly like
  the main transcript: their `cwd` is pointed at the attached folder, their `sessionId` follows a
  duplicated session's new id and their timestamps move by the same delta. They used to be copied as
  they were, leaving the source user's project path, the old id and the original dates on the
  destination machine. Every other auxiliary file is still copied byte for byte.

## [0.0.3] - 2026-09-25

Development version.

### Added

- Export removes the exporting user's own identity from the archive before writing it: the account
  email and the local `git status` output (which includes the configured git user name), embedded by
  Claude Code itself in a `session_context` attachment and mirrored in its pre-rendered
  `<system-reminder>` cache. Applied to every `.jsonl` file the export writes - the main transcript and
  any subagent transcript under the auxiliary folder - so the archive itself never carries them,
  whether or not it is ever imported, and by whom. The export notification reports how many lines were
  cleaned.
- Export also removes Claude Code's own per-session scratch folder path (an `environment` attachment's
  `scratchpadDirectory`), always under the OS temp directory and so always naming the account that ran
  it, and of no use on another machine, where a fresh one is created regardless.
- The archive manifest no longer records the source machine's Claude Code home path: it always embedded
  the exporting user's OS account name and had no functional use.
- Import now also remaps an `environment` attachment's own working directory and additional working
  directories when the session is attached to a folder, the same way `cwd` and the file history paths
  already were - closing the last gap where a project path could survive an attach-to-folder import
  unmapped. The visibility diagnosis (check #11) now also catches a regression here.
- What none of the above touches, on purpose: if the account email or git user name appears inside the
  conversation itself - because it was typed, or because a file Claude read or edited contains it -
  that text is conversation content and is kept exactly as it was, for the same reason the conversation
  itself is never rewritten. Verified against every real transcript on the development machine: all
  structural occurrences were removed, and the only text that survived was inside `message`,
  `toolUseResult` or a file-editing attachment.
- A dedicated end-to-end test exports a session from one simulated machine and account and imports it
  into a different one, on a different drive, confirming: no conflict (a fresh id imports as "new"),
  no identity or scratch path left anywhere, every project folder correctly pointed at the destination,
  and every timestamp landing at import time on the destination machine, never at the source machine's
  original time - reconfirming a guarantee already in place since 0.0.0.

## [0.0.2] - 2026-09-23

Development version.

### Changed

- The *Session* column shows a readable name, the same in the export and in the import table. Current
  Claude Code versions write no `summary` line, and the first user line of a session is usually the
  markup of a slash command (`<command-name>/model</command-name>`), a caveat or a system reminder, so
  the name is now picked, in order, from: a custom title, a `summary` line, the first prompt the user
  actually typed (with that markup and any IDE context removed), the slash command the session started
  with, and the last recorded prompt. The import table picks it again from the archived transcript, so
  archives written by 0.0.0 and 0.0.1 read the same too. A session with none of them is shown as
  *(untitled session)*, translated like every other label.

## [0.0.1] - 2026-09-23

Development version.

### Added

- The *Tools* submenu and its *Export Sessions...* and *Import Sessions...* entries show the plugin
  icon in the main menu on every platform, including the macOS system menu, where IntelliJ hides menu
  icons unless an entry asks for them.

### Changed

- The documentation states what happens after installing and after importing, as verified on
  IntelliJ IDEA 2026.2.3: the plugin is loaded dynamically, without restarting the IDE, and imported
  sessions are listed by `claude --resume` right away, so neither step asks for a restart. It also
  explains why *Install Plugin from Disk* shows the generic plugin icon while the plugin is read from
  its ZIP, and where the plugin logo is shown instead.

## [0.0.0] - 2026-09-23

Development version.

### Added

- Export and import of Claude Code chat sessions as a portable ZIP archive: the transcript
  (`~/.claude/projects/<project>/<sessionId>.jsonl`), its auxiliary folder and its file history
  (`~/.claude/file-history/<sessionId>/`, the backups behind checkpoints and `/rewind`).
- Session tables with a quick filter, multiple selection and the name, folder, branch, last update,
  number of messages, size and id of every session; the import table marks the sessions that are
  already present.
- Conflict policies on import - *Skip*, *Replace* and *Duplicate* (the default, which imports under a
  new id) - applied to a session whose id exists in any project folder, not only in the target one.
- *Attach the imported sessions to this folder*: the transcript is written to the project folder
  Claude Code reads for that directory, and every `cwd` is rewritten in the form Claude Code records
  it (`C:\Users\...`, not IntelliJ's `C:/Users/...`), sub-folders included. File paths tracked by the
  file history are remapped with it when they lie below the source root.
- Timestamps of an imported session are shifted by one delta, so it ends at import time and keeps the
  spacing between its messages; every ISO timestamp keeps the shape it was read in, and the epoch of
  the `cost-state` line moves with them.
- Structural rewrite of the transcript: only the machine facing fields (`sessionId`, `session_id`,
  `cwd`, file history paths and timestamps) change. Lines none of them touches are copied byte for
  byte, `null` fields and characters such as `<`, `=` or `&` are written back as they were, and line
  endings are preserved, including the trailing newline Claude Code relies on to append to a resumed
  session.
- The outcome of an import is re-read from disk and shown in the notification, together with every
  failed check of the visibility diagnosis and the actions *Copy resume command* and *Show import
  log*. No IDE restart is needed: `claude --resume` lists an imported session right away. A session
  that fails does not stop the others.
- Diagnostic import log in `<IDE log folder>/claude-sessions-import/`, always written: environment,
  operation context, per-session steps, thirteen numbered `OK`/`KO` visibility checks, a comparison
  with the most recent native session of the target folder and a summary. It never contains
  conversation text, values are truncated to 300 characters and only the last 20 logs are kept.
- The export reports every file it could not read, and only then suggests closing the Claude Code
  sessions using it and exporting again; the manifest lists exactly the sessions written to the
  archive.
- Localized interface - menu, dialogs, tables, notifications, warnings, errors, progress and import
  log sections - in English, Italian, French, German, Spanish, Portuguese, Japanese, Chinese and
  Korean, following the IDE language when a language pack is installed and the regional settings of
  the operating system otherwise.
- Plugin logo and menu icon with dark variants: a ring with the green import arrow and the blue
  export arrow facing each other.
- The `runIde` sandbox works on `build/claude-home-test` instead of the real `~/.claude`.
- Compatible with IntelliJ-based IDEs from build 261 on, with no upper bound; verified with the
  IntelliJ Plugin Verifier on 2026.1.5, 2026.2.3 and the 2026.3 EAP (263.5153.40), where any finding -
  deprecated, experimental or internal API included - fails the build.
- Menu texts come from the plugin resource bundle, as JetBrains recommends, and the plugin can be
  installed, enabled and disabled without restarting the IDE.
- Licensing and privacy: the plugin jar carries its MIT license, the notice for the bundled Gson
  library and the Apache License 2.0 it is distributed under; the repository adds a privacy policy
  (the plugin collects and transmits no data) and a trademark notice.
