-- Compiles each file given and prints one line per file: "<index>\tok" or "<index>\terror\t<message>".
local fs = require("@lune/fs")
local luau = require("@lune/luau")
local process = require("@lune/process")

for i, path in process.args do
	local ok, err = pcall(luau.compile, fs.readFile(path))
	if ok then
		print(`{i - 1}\tok`)
	else
		-- "syntax error: 2: Expected ..." and a stack trace; keep "line 2: Expected ...".
		local first = string.split(tostring(err), "\n")[1]
		local line, message = string.match(first, ":%s*(%d+):%s*(.*)$")
		print(`{i - 1}\terror\t{if line then `line {line}: {message}` else first}`)
	end
end
