# Privacy policy

*Applies to the plugin "Session Porter for Claude Code", from version 0.0.0. The export sanitizing
described below applies from version 0.0.3.*

**The plugin does not collect, transmit or share any data.** It has no telemetry, no statistics, no
crash reporting and no network access of any kind: it never contacts a server, neither the author's
nor a third party's.

## What the plugin reads and writes, only on your machine

* **Export** reads the Claude Code sessions you select — their transcripts, auxiliary folders and file
  history under `~/.claude` (or `CLAUDE_CONFIG_DIR`) — and writes them to the ZIP archive **you**
  choose. The archive contains your conversations: keep it as private as the conversations themselves,
  and share it only with people who may read them.
* **Import** reads the archive you choose and writes the selected sessions under `~/.claude`.
* **Every import writes a diagnostic log** in the IDE log folder (`claude-sessions-import/`). The log
  never contains conversation text; it does contain local metadata such as file paths, the name of
  your home folder, the operating system, the IDE and Java versions, and the session ids. It stays on
  your machine, only the last 20 logs are kept, and it leaves the machine only if you send it to
  someone yourself.

The plugin processes nothing else: it does not read other files, other IDE settings or other
projects.

## What export removes before writing the archive

Claude Code writes two kinds of record into a session on your behalf, never because you typed them:
your account email and your local `git status` output (which includes your configured git user name),
and its own per-session temporary folder path (always under the OS temp directory, so always naming
your account). **Export removes both, from every `.jsonl` file it writes, before the archive is
created** — the archive itself never carries them, whether or not it is ever imported, and by whom.

This does **not** touch anything you or Claude actually wrote to each other: if your email or name
appears in the conversation itself — because you typed it, or because a file Claude read or edited
contains it — that text is conversation content, kept exactly as it was, for the same reason the
conversation itself is never rewritten (see the README's *Riscrittura del transcript* section). The
plugin does not scan your conversations for anything that looks personal.

## Contact

Questions about this policy can be opened as an issue on the
[project repository](https://github.com/fabiopelliccia/claudsessionsmanager/issues).
