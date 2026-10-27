-- Check for table that is shared between executions.
if not shared then
	return warn("No shared, no script.")
end

-- Initialize Luraph globals if they do not exist.
loadstring("getfenv().LPH_NO_VIRTUALIZE = function(...) return ... end")()

getfenv().PP_SCRAMBLE_NUM = function(...) return ... end
getfenv().PP_SCRAMBLE_STR = function(...) return ... end
getfenv().PP_SCRAMBLE_RE_NUM = function(...) return ... end

local RustESP = { enabled = true }

-- Constants.
local CONFIG = {
	maxDistance = 1500,
	boxColor = Color3.fromRGB(255, 70, 70),
	textColor = Color3.fromRGB(255, 255, 255),
	textSize = 13,
	showHealth = true,
	showDistance = true,
}

-- Services.
local playersService = cloneref(game:GetService("Players"))
local runService = cloneref(game:GetService("RunService"))
local workspaceService = cloneref(game:GetService("Workspace"))

-- State.
local localPlayer = playersService.LocalPlayer
local espObjects = {}
local renderConnection = nil

---Create a drawing with the given properties.
---@param class string
---@param properties table
---@return table
local function createDrawing(class, properties)
	local drawing = Drawing.new(class)

	for property, value in next, properties do
		drawing[property] = value
	end

	return drawing
end

---Create the drawings used for one player.
---@return table
local function createEsp()
	return {
		box = createDrawing("Square", { Thickness = 1, Filled = false, Color = CONFIG.boxColor, Visible = false }),
		name = createDrawing("Text", { Size = CONFIG.textSize, Center = true, Outline = true, Color = CONFIG.textColor, Visible = false }),
		info = createDrawing("Text", { Size = CONFIG.textSize, Center = true, Outline = true, Color = CONFIG.textColor, Visible = false }),
		healthBar = createDrawing("Line", { Thickness = 2, Visible = false }),
	}
end

---Remove drawings for a player.
---@param player Player
local function removeEsp(player)
	local esp = espObjects[player]
	if not esp then
		return
	end

	for _, drawing in next, esp do
		drawing:Remove()
	end

	espObjects[player] = nil
end

---Hide drawings for a player.
---@param esp table
local function hideEsp(esp)
	for _, drawing in next, esp do
		drawing.Visible = false
	end
end

---Resolve root part and health info from a character, supporting custom rigs.
---@param character Model
---@return BasePart?, number?, number?
local function resolveCharacter(character)
	local rootPart = character:FindFirstChild("HumanoidRootPart") or character.PrimaryPart or character:FindFirstChild("Torso")
	if not rootPart then
		return nil
	end

	local humanoid = character:FindFirstChildOfClass("Humanoid")
	if humanoid then
		return rootPart, humanoid.Health, humanoid.MaxHealth
	end

	-- Many Rust-style games store health as an attribute or value object instead.
	local health = character:GetAttribute("Health")
	local healthValue = character:FindFirstChild("Health")
	if not health and healthValue and healthValue:IsA("ValueBase") then
		health = healthValue.Value
	end

	return rootPart, health, character:GetAttribute("MaxHealth") or 100
end

---Update a single player's drawings.
---@param player Player
---@param esp table
---@param camera Camera
local function updateEsp(player, esp, camera)
	local character = player.Character
	if not character then
		return hideEsp(esp)
	end

	local rootPart, health, maxHealth = resolveCharacter(character)
	if not rootPart or (health and health <= 0) then
		return hideEsp(esp)
	end

	local distance = (camera.CFrame.Position - rootPart.Position).Magnitude
	if distance > CONFIG.maxDistance then
		return hideEsp(esp)
	end

	local rootPos, onScreen = camera:WorldToViewportPoint(rootPart.Position)
	if not onScreen then
		return hideEsp(esp)
	end

	-- Project top and bottom of the character to size the box by distance.
	local topPos = camera:WorldToViewportPoint(rootPart.Position + Vector3.new(0, 3, 0))
	local bottomPos = camera:WorldToViewportPoint(rootPart.Position - Vector3.new(0, 3.5, 0))
	local height = math.abs(bottomPos.Y - topPos.Y)
	local width = height * 0.6
	local boxPosition = Vector2.new(rootPos.X - width / 2, topPos.Y)

	esp.box.Size = Vector2.new(width, height)
	esp.box.Position = boxPosition
	esp.box.Visible = true

	esp.name.Text = player.DisplayName
	esp.name.Position = Vector2.new(rootPos.X, topPos.Y - CONFIG.textSize - 2)
	esp.name.Visible = true

	local infoText = CONFIG.showDistance and string.format("[%dm]", distance) or ""
	if CONFIG.showHealth and health then
		infoText = string.format("%s [%d HP]", infoText, health)
	end

	esp.info.Text = infoText
	esp.info.Position = Vector2.new(rootPos.X, bottomPos.Y + 2)
	esp.info.Visible = infoText ~= ""

	if not (CONFIG.showHealth and health) then
		esp.healthBar.Visible = false
		return
	end

	-- Draw a vertical health bar on the left side of the box.
	local healthRatio = math.clamp(health / maxHealth, 0, 1)
	local barX = boxPosition.X - 4

	esp.healthBar.From = Vector2.new(barX, boxPosition.Y + height)
	esp.healthBar.To = Vector2.new(barX, boxPosition.Y + height - height * healthRatio)
	esp.healthBar.Color = Color3.fromRGB(255, 0, 0):Lerp(Color3.fromRGB(0, 255, 0), healthRatio)
	esp.healthBar.Visible = true
end

---Render step for every tracked player.
local function onRenderStep()
	local camera = workspaceService.CurrentCamera
	if not camera then
		return
	end

	for _, player in next, playersService:GetPlayers() do
		if player == localPlayer then
			continue
		end

		local esp = espObjects[player]
		if not esp then
			esp = createEsp()
			espObjects[player] = esp
		end

		if not RustESP.enabled then
			hideEsp(esp)
			continue
		end

		updateEsp(player, esp, camera)
	end
end

---Detach the ESP and clean up drawings.
function RustESP.detach()
	if renderConnection then
		renderConnection:Disconnect()
		renderConnection = nil
	end

	for player in next, espObjects do
		removeEsp(player)
	end
end

---Initialize the ESP.
function RustESP.init()
	renderConnection = runService.RenderStepped:Connect(onRenderStep)
	playersService.PlayerRemoving:Connect(removeEsp)
end

---This is called when initialization errors.
---@param error string
local function onInitializeError(error)
	warn("Failed to initialize ESP.")
	warn(error)
	warn(debug.traceback())
	RustESP.detach()
end

-- Detach any previous instance before re-executing.
if shared.RustESP then
	shared.RustESP.detach()
end

shared.RustESP = RustESP

xpcall(RustESP.init, onInitializeError)
