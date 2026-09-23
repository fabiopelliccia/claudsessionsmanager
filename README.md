# Claude Code sessions

IntelliJ plugin to export and import [Claude Code](https://claude.com/product/claude-code) chat
sessions as a portable ZIP archive, designed to move them from one machine (and one user) to
another.

Claude Code stores every session as a plain JSONL transcript under
`~/.claude/projects/<encoded-project-path>/<sessionId>.jsonl`, plus an optional sibling folder with
the same name holding auxiliary data (subagent runs, tool results, ...). This plugin copies those
files as-is - it never parses or rewrites the conversation itself.

## Usage

- **Tools | Claude Code sessions | Export Sessions...** — pick one or more local sessions and save
  them into a ZIP archive.
- **Tools | Claude Code sessions | Import Sessions...** — pick an archive, choose which local
  project folder to attach the sessions to (pre-filled with the currently open project), and pick
  which sessions to restore. A session that already exists locally is never overwritten: it is
  imported under a freshly generated id instead, so importing the same archive twice is always safe.

On import, three fields of each transcript line are rewritten - nothing else is ever touched:

- **`cwd`** is pointed at the local folder you picked. A session records more than one working
  directory in practice - its project root, directories below it, and sometimes an unrelated
  project it was moved to mid-way - so all of them are remapped: a path below the session's root
  keeps its relative remainder, anything else is replaced outright, because it names a folder that
  only exists on the machine the archive came from.
- **`timestamp`** (including the one nested under a `file-history-snapshot` line's `snapshot`) is
  shifted by a single delta per session, so the whole conversation lands around "now" - the last
  message ends at import time - while every message keeps its original spacing relative to the
  others. The imported session then shows up as one that just happened, not an old one.
- **`sessionId`** is only touched when the session already exists locally, in which case the import
  is duplicated under a fresh id instead of overwriting it.

## Archive format

```
manifest.json                       # formatVersion, exportedAt, producer, sessions metadata
sessions/<id>/transcript.jsonl      # verbatim copy of <sessionId>.jsonl
sessions/<id>/aux/...                # verbatim copy of the sibling <sessionId>/ folder, if present
```

## Scope

This is intentionally a "raw" export/import tool: it moves the on-disk transcript files as-is, with
minimal conflict handling (skip / overwrite / duplicate, duplicate being the default and the only
one exposed in the UI). The auxiliary `aux/` folder (subagent runs, tool results, ...) is copied
verbatim and not rewritten, so any absolute path it happens to reference internally still points at
the source machine.

## Building

```
./gradlew buildPlugin
```

Requires JDK 21. See `gradle.properties` for the target IntelliJ platform version.
