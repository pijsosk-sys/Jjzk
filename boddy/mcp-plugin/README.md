# Roblox Studio MCP plugin

The Studio side of Roblox's MCP server, copied unchanged from
[Roblox/studio-rust-mcp-server](https://github.com/Roblox/studio-rust-mcp-server) at commit
`b0b6474f55eb1772254d98533e40e8f5abc2260b` (`plugin/src`, MIT licensed, see `LICENSE`).

It polls `http://localhost:44755`, where `../roblox-studio-mcp` (Boddy's Python version of the
server) hands it Claude Code's requests. `../build-mcp-plugin` turns this folder into
`../MCPStudioPlugin.rbxmx` the way Rojo would build `plugin/default.project.json`; the Studio
launcher copies that file into Studio's Plugins folder.
