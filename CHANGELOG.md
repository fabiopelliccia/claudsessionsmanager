# Changelog

All notable changes to the Claude Code sessions plugin are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- Import no longer drops the transcript's trailing newline, which could corrupt the file once
  Claude Code appended to a resumed session.
- Import keeps `null` fields (such as `"parentUuid":null`) and no longer escapes `<`, `>`, `=`, `'`
  and `&` as `\u003c`-style sequences: apart from `sessionId`, `cwd` and `timestamp`, every line is
  written back exactly as recorded, and lines where none of those fields change are kept verbatim.
- The rewritten `cwd` uses the target platform's separator: a folder picked in IntelliJ as
  `C:/Users/...` is now recorded as `C:\Users\...`, as Claude Code does, including sub-folders.

## [1.0.0] - 2026-09-21

### Added

- Initial release. Export and import Claude Code chat sessions (`~/.claude/projects/<project>/<sessionId>.jsonl`
  plus the auxiliary per-session folder) as a portable ZIP archive, with a simple checkbox-list picker
  and safe-by-default duplicate handling on import.
- Cross-machine import: the import dialog asks which local project folder to attach the sessions
  to and rewrites each transcript's `cwd` field accordingly, since the folder recorded in the
  archive belongs to the source machine and is otherwise meaningless.
- Imported sessions have their timestamps shifted by a single per-session delta so they land around
  "now" instead of when they were originally recorded, while keeping the original spacing between
  their own messages.
