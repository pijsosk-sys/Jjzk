-- Reads a place file with Lune's Roblox library and prints every instance as one JSON line.
local fs = require("@lune/fs")
local serde = require("@lune/serde")
local process = require("@lune/process")
local roblox = require("@lune/roblox")

local game = roblox.deserializePlace(fs.readFile(process.args[1]))
for _, inst in game:GetDescendants() do
	local row = { class = inst.ClassName, name = inst.Name, path = inst:GetFullName() }
	if inst:IsA("BasePart") then
		-- Position and Orientation aren't stored in the file; Studio works them out from CFrame.
		local cf = inst.CFrame
		row.position = { cf.Position.X, cf.Position.Y, cf.Position.Z }
		local rx, ry, rz = cf:ToOrientation()
		row.orientation = { math.deg(rx), math.deg(ry), math.deg(rz) }
		row.size = { inst.Size.X, inst.Size.Y, inst.Size.Z }
		row.color = { math.round(inst.Color.R * 255), math.round(inst.Color.G * 255), math.round(inst.Color.B * 255) }
		row.material = inst.Material.Name
		row.transparency = inst.Transparency
		row.anchored = inst.Anchored
		row.canCollide = inst.CanCollide
		if inst:IsA("Part") then
			row.shape = inst.Shape.Name
		end
	end
	if inst:IsA("LuaSourceContainer") then
		row.source = inst.Source
	end
	print(serde.encode("json", row))
end
