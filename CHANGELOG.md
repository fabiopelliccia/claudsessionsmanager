# Changelog

All notable changes to the Claude Code sessions plugin are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
