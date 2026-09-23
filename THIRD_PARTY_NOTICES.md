# Third-party notices

The plugin is released under the [MIT License](LICENSE). Its distribution archive
(`session-porter-for-claude-code-<version>.zip`) also bundles the following third-party component, in
`session-porter-for-claude-code/lib/`, under its own license:

| Component | Version | License | Source |
|---|---|---|---|
| [Gson](https://github.com/google/gson) | 2.11.0 | [Apache License 2.0](licenses/Apache-2.0.txt) | `com.google.code.gson:gson` on Maven Central |

Gson is Copyright (C) 2008 Google Inc. It is redistributed unmodified; the full text of its license is in
[`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt) and is also packaged inside the plugin jar, under
`META-INF/licenses/`, together with this notice and the plugin's own license.

The Kotlin standard library and the IntelliJ Platform are **not** bundled: the plugin uses the copies
that ship with the IDE it runs in.

## Trademarks

Claude and Claude Code are trademarks of Anthropic, PBC. IntelliJ IDEA and JetBrains are trademarks of
JetBrains s.r.o. This plugin is an independent project: it is not affiliated with, endorsed by or
sponsored by Anthropic or JetBrains. Those names are used only to identify the products the plugin
works with.
