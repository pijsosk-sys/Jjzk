-- Unpacks scripts from Roblox files and puts edited ones back. Run by roblox-tool via Lune.
local fs = require("@lune/fs")
local process = require("@lune/process")
local roblox = require("@lune/roblox")

local SCRIPT_EXT = {
	Script = ".server.luau",
	LocalScript = ".client.luau",
	ModuleScript = ".luau",
}

local function safe(name: string): string
	local cleaned = string.gsub(name, "[^%w%-_ ]", "_")
	return if cleaned == "" then "_" else cleaned
end

local function isModel(path: string): boolean
	return string.match(path, "%.rbxmx?$") ~= nil
end

local function load(path: string)
	local data = fs.readFile(path)
	if isModel(path) then
		return roblox.deserializeModel(data), true
	end
	return roblox.deserializePlace(data), false
end

-- Visits every instance with a unique, stable folder path. Duplicate sibling names get " (2)", " (3)".
local function walk(instances, prefix: string, depth: number, visit)
	local seen = {}
	for _, inst in instances do
		local base = safe(inst.Name)
		seen[base] = (seen[base] or 0) + 1
		local name = if seen[base] > 1 then `{base} ({seen[base]})` else base
		local path = if prefix == "" then name else `{prefix}/{name}`
		visit(inst, path, depth)
		walk(inst:GetChildren(), path, depth + 1, visit)
	end
end

local function roots(root, isModelFile: boolean)
	return if isModelFile then root else root:GetChildren()
end

local mode, file, dir, out = process.args[1], process.args[2], process.args[3], process.args[4]
local root, isModelFile = load(file)

if mode == "extract" then
	fs.writeDir(dir)
	local tree, count = {}, 0
	walk(roots(root, isModelFile), "", 0, function(inst, path, depth)
		local ext = SCRIPT_EXT[inst.ClassName]
		table.insert(tree, `{string.rep("  ", depth)}{inst.Name} [{inst.ClassName}]{if ext then "  -> " .. path .. ext else ""}`)
		if ext then
			local parent = string.match(path, "^(.*)/[^/]*$")
			if parent then
				fs.writeDir(`{dir}/{parent}`)
			end
			fs.writeFile(`{dir}/{path}{ext}`, inst.Source)
			count += 1
		end
	end)
	fs.writeFile(`{dir}/tree.txt`, table.concat(tree, "\n") .. "\n")
	print(`Unpacked {count} scripts to {dir}`)
	print(`The game's layout is in {dir}/tree.txt`)
elseif mode == "apply" then
	local changed = 0
	walk(roots(root, isModelFile), "", 0, function(inst, path)
		local ext = SCRIPT_EXT[inst.ClassName]
		local scriptFile = if ext then `{dir}/{path}{ext}` else nil
		if scriptFile and fs.isFile(scriptFile) then
			local source = fs.readFile(scriptFile)
			if source ~= inst.Source then
				inst.Source = source
				changed += 1
			end
		end
	end)
	local xml = string.match(out, "x$") ~= nil
	local data = if isModelFile then roblox.serializeModel(root, xml) else roblox.serializePlace(root, xml)
	fs.writeFile(out, data)
	print(`Updated {changed} scripts and saved {out}`)
else
	error("usage: roblox.luau extract FILE DIR | apply FILE DIR OUT")
end
