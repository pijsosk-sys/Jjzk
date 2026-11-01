-- Check for table that is shared between executions.
if not shared then
	return warn("No shared, no script.")
end

-- Initialize Luraph globals if they do not exist.
loadstring("getfenv().LPH_NO_VIRTUALIZE = function(...) return ... end")()

getfenv().PP_SCRAMBLE_NUM = function(...) return ... end
getfenv().PP_SCRAMBLE_STR = function(...) return ... end
getfenv().PP_SCRAMBLE_RE_NUM = function(...) return ... end

local EggHub = {
	fly = { enabled = false, speed = 60, boost = 2.5, smoothing = 10, target = nil },
	farm = { enabled = false, minValue = 0, home = nil },
	esp = { enabled = false },
	eggs = {},
}

-- Constants.
local TOGGLE_FLY_KEY = Enum.KeyCode.F
local TOGGLE_UI_KEY = Enum.KeyCode.RightShift
local BOOST_KEY = Enum.KeyCode.LeftShift
local ARRIVE_DISTANCE = 4
local MOVE_TIMEOUT = 25
local DEPOSIT_DELAY = 0.6
local RETRY_COOLDOWN = 15
local ESP_COUNT = 10
local EGG_NAME_PATTERN = "egg"
local VALUE_KEYS = { "Value", "Price", "Cost", "Income", "Money", "Cash", "Worth", "Generation", "PerSecond" }
local RARITY_KEYS = { "Rarity", "Tier", "Grade" }
local OWNER_KEYS = { "Owner", "OwnerName", "Player", "PlotOwner" }
local RARITY_ORDER = { "common", "uncommon", "rare", "epic", "legendary", "mythic", "mythical", "godly", "secret", "divine", "celestial", "og" }
local MONEY_SUFFIXES = { k = 1e3, m = 1e6, b = 1e9, t = 1e12, q = 1e15 }

-- Services.
local playersService = cloneref(game:GetService("Players"))
local runService = cloneref(game:GetService("RunService"))
local userInputService = cloneref(game:GetService("UserInputService"))

-- State.
local localPlayer = playersService.LocalPlayer
local connections = {}
local espObjects = {}
local attempted = setmetatable({}, { __mode = "k" })
local flyCFrame = nil
local currentVelocity = Vector3.zero
local screenGui = nil
local statusLabel = nil
local eggList = nil

---Return the local character's root part and humanoid.
---@return BasePart?, Humanoid?
local function getCharacter()
	local character = localPlayer.Character
	if not character then
		return nil, nil
	end

	return character:FindFirstChild("HumanoidRootPart"), character:FindFirstChildOfClass("Humanoid")
end

---Update the status text in the UI.
---@param text string
local function setStatus(text)
	if statusLabel then
		statusLabel.Text = "Status: " .. text
	end
end

---Parse "$1.5K/s" style strings into numbers.
---@param text string
---@return number?
local function parseMoney(text)
	local number, suffix = tostring(text):gsub(",", ""):match("([%d%.]+)%s*([KkMmBbTtQq]?)")
	number = tonumber(number)
	if not number then
		return nil
	end

	return number * (MONEY_SUFFIXES[suffix:lower()] or 1)
end

---Return the rank of a rarity name, higher is better.
---@param text string?
---@return number
local function getRarityRank(text)
	if not text then
		return 0
	end

	local lowered = tostring(text):lower()
	local best = 0
	for rank, name in ipairs(RARITY_ORDER) do
		if lowered:find(name, 1, true) then
			best = rank
		end
	end

	return best
end

---Read a keyed value from attributes or ValueBase children.
---@param instance Instance
---@param keys table
---@return any
local function readKeyed(instance, keys)
	for _, key in ipairs(keys) do
		local attribute = instance:GetAttribute(key)
		if attribute ~= nil then
			return attribute
		end

		local child = instance:FindFirstChild(key)
		if child and child:IsA("ValueBase") then
			return child.Value
		end
	end

	return nil
end

---Work out an egg's value and rarity from attributes, values and billboard text.
---@param egg Instance
---@return number, string?
local function getEggInfo(egg)
	local value = readKeyed(egg, VALUE_KEYS)
	local rarity = readKeyed(egg, RARITY_KEYS)
	value = typeof(value) == "number" and value or (value and parseMoney(value))

	-- Fall back to whatever the egg's billboard shows.
	if not value or not rarity then
		for _, label in ipairs(egg:GetDescendants()) do
			if not label:IsA("TextLabel") then
				continue
			end

			local text = label.ContentText
			if not value and text:find("%$") then
				value = parseMoney(text)
			end

			if not rarity and getRarityRank(text) > 0 then
				rarity = text
			end
		end
	end

	return value or 0, rarity and tostring(rarity) or nil
end

---Check if an instance is owned by us via owner keys.
---@param instance Instance
---@return boolean
local function isOwnedByUs(instance)
	local owner = readKeyed(instance, OWNER_KEYS)
	return owner == localPlayer or owner == localPlayer.Name or owner == localPlayer.UserId or owner == localPlayer.DisplayName
end

---Find our plot by walking workspace for an owned container.
---@return Instance?
local function findOurPlot()
	for _, instance in ipairs(workspace:GetDescendants()) do
		if (instance:IsA("Model") or instance:IsA("Folder")) and isOwnedByUs(instance) then
			return instance
		end
	end

	return nil
end

---Return a world position for an egg model or part.
---@param egg Instance
---@return Vector3?
local function getEggPosition(egg)
	if egg:IsA("BasePart") then
		return egg.Position
	end

	if egg:IsA("Model") then
		return egg:GetPivot().Position
	end

	return nil
end

---Scan the map for eggs and rank them best first.
---@return table
function EggHub.scan()
	local plot = findOurPlot()
	local results = {}

	for _, instance in ipairs(workspace:GetDescendants()) do
		if not (instance:IsA("Model") or instance:IsA("BasePart")) then
			continue
		end

		if not instance.Name:lower():find(EGG_NAME_PATTERN) then
			continue
		end

		-- Only keep the outermost egg container so parts inside don't duplicate.
		local parent = instance.Parent
		if parent and parent.Name:lower():find(EGG_NAME_PATTERN) then
			continue
		end

		if localPlayer.Character and instance:IsDescendantOf(localPlayer.Character) then
			continue
		end

		local position = getEggPosition(instance)
		if not position then
			continue
		end

		local value, rarity = getEggInfo(instance)
		table.insert(results, {
			instance = instance,
			position = position,
			value = value,
			rarity = rarity,
			rank = getRarityRank(rarity),
			owned = plot ~= nil and instance:IsDescendantOf(plot),
			prompt = instance:FindFirstChildWhichIsA("ProximityPrompt", true),
		})
	end

	table.sort(results, function(a, b)
		if a.value ~= b.value then
			return a.value > b.value
		end

		return a.rank > b.rank
	end)

	EggHub.eggs = results
	return results
end

---Build a camera-relative direction from held keys.
---@return Vector3
local function getInputDirection()
	local camera = workspace.CurrentCamera
	local look, right = camera.CFrame.LookVector, camera.CFrame.RightVector
	local direction = Vector3.zero

	if userInputService:GetFocusedTextBox() then
		return direction
	end

	if userInputService:IsKeyDown(Enum.KeyCode.W) then direction += look end
	if userInputService:IsKeyDown(Enum.KeyCode.S) then direction -= look end
	if userInputService:IsKeyDown(Enum.KeyCode.D) then direction += right end
	if userInputService:IsKeyDown(Enum.KeyCode.A) then direction -= right end
	if userInputService:IsKeyDown(Enum.KeyCode.Space) then direction += Vector3.yAxis end
	if userInputService:IsKeyDown(Enum.KeyCode.LeftControl) then direction -= Vector3.yAxis end

	return direction.Magnitude > 0 and direction.Unit or direction
end

---Step the fly: we own the position, so physics never builds velocity.
---@param deltaTime number
local function onHeartbeat(deltaTime)
	local fly = EggHub.fly
	if not fly.enabled then
		return
	end

	local rootPart, humanoid = getCharacter()
	if not (rootPart and humanoid and humanoid.Health > 0) then
		return
	end

	flyCFrame = flyCFrame or rootPart.CFrame

	local speed = fly.speed * (userInputService:IsKeyDown(BOOST_KEY) and fly.boost or 1)
	local position = flyCFrame.Position
	local facing = workspace.CurrentCamera.CFrame.LookVector

	if fly.target then
		-- Autopilot walks toward the target at fly speed instead of teleporting.
		local offset = fly.target - position
		local step = math.min(speed * deltaTime, offset.Magnitude)
		currentVelocity = Vector3.zero
		if offset.Magnitude > 0 then
			position += offset.Unit * step
			facing = offset.Unit
		end
	else
		local targetVelocity = getInputDirection() * speed
		currentVelocity = currentVelocity:Lerp(targetVelocity, math.clamp(deltaTime * fly.smoothing, 0, 1))
		position += currentVelocity * deltaTime
	end

	local flatLook = Vector3.new(facing.X, 0, facing.Z)
	flyCFrame = flatLook.Magnitude > 0 and CFrame.lookAt(position, position + flatLook) or CFrame.new(position)

	-- Zero physics velocity so gravity and velocity checks see a resting part.
	rootPart.AssemblyLinearVelocity = Vector3.zero
	rootPart.AssemblyAngularVelocity = Vector3.zero
	rootPart.CFrame = flyCFrame
end

---Toggle flight on or off.
---@param state boolean?
function EggHub.toggleFly(state)
	local fly = EggHub.fly
	if state == nil then
		state = not fly.enabled
	end

	fly.enabled = state
	fly.target = nil
	flyCFrame = nil
	currentVelocity = Vector3.zero

	local _, humanoid = getCharacter()
	if humanoid then
		humanoid:ChangeState(state and Enum.HumanoidStateType.Physics or Enum.HumanoidStateType.GettingUp)
	end
end

---Fly to a position and yield until arrival or timeout.
---@param position Vector3
---@return boolean
function EggHub.moveTo(position)
	if not EggHub.fly.enabled then
		EggHub.toggleFly(true)
	end

	EggHub.fly.target = position
	local started = os.clock()

	while os.clock() - started < MOVE_TIMEOUT do
		local rootPart = getCharacter()
		if not rootPart or not EggHub.fly.enabled then
			break
		end

		if (rootPart.Position - position).Magnitude <= ARRIVE_DISTANCE then
			EggHub.fly.target = nil
			return true
		end

		task.wait()
	end

	EggHub.fly.target = nil
	return false
end

---Fly to an egg and fire its prompt.
---@param egg table
---@return boolean
function EggHub.grab(egg)
	if not egg.instance.Parent then
		return false
	end

	local position = getEggPosition(egg.instance) or egg.position
	setStatus("flying to " .. egg.instance.Name)
	if not EggHub.moveTo(position + Vector3.new(0, 3, 0)) then
		return false
	end

	local prompt = egg.prompt or egg.instance:FindFirstChildWhichIsA("ProximityPrompt", true)
	if prompt then
		fireproximityprompt(prompt)
	else
		-- No prompt, so try touching it instead.
		local rootPart = getCharacter()
		local part = egg.instance:IsA("BasePart") and egg.instance or egg.instance:FindFirstChildWhichIsA("BasePart", true)
		if rootPart and part then
			firetouchinterest(rootPart, part, 0)
			task.wait()
			firetouchinterest(rootPart, part, 1)
		end
	end

	task.wait(0.3)
	return true
end

---Return to the home position to deposit the egg.
function EggHub.goHome()
	local home = EggHub.farm.home
	if not home then
		local plot = findOurPlot()
		home = plot and (plot:IsA("Model") and plot:GetPivot().Position or nil)
	end

	if not home then
		return setStatus("no home set")
	end

	setStatus("returning home")
	EggHub.moveTo(home + Vector3.new(0, 3, 0))
	task.wait(DEPOSIT_DELAY)
end

---Pick the best egg we haven't stolen or recently tried.
---@return table?
local function pickBestEgg()
	for _, egg in ipairs(EggHub.scan()) do
		if egg.owned or egg.value < EggHub.farm.minValue then
			continue
		end

		local lastTry = attempted[egg.instance]
		if lastTry and os.clock() - lastTry < RETRY_COOLDOWN then
			continue
		end

		return egg
	end

	return nil
end

---Main farm loop: grab best egg, bring it home, repeat.
local function farmLoop()
	while EggHub.farm.enabled do
		local egg = pickBestEgg()
		if not egg then
			setStatus("no eggs found, rescanning")
			task.wait(1)
			continue
		end

		attempted[egg.instance] = os.clock()
		if EggHub.grab(egg) then
			EggHub.goHome()
		end

		task.wait(0.2)
	end

	EggHub.toggleFly(false)
	setStatus("idle")
end

---Toggle the auto farm.
---@param state boolean
function EggHub.toggleFarm(state)
	EggHub.farm.enabled = state
	if not state then
		return
	end

	-- Default home to where we stand when farming starts.
	local rootPart = getCharacter()
	if not EggHub.farm.home and rootPart then
		EggHub.farm.home = rootPart.Position
	end

	task.spawn(farmLoop)
end

---Clear every ESP object.
local function clearEsp()
	for _, object in ipairs(espObjects) do
		object:Destroy()
	end

	table.clear(espObjects)
end

---Redraw ESP on the top eggs.
local function refreshEsp()
	clearEsp()
	if not EggHub.esp.enabled then
		return
	end

	for index = 1, math.min(ESP_COUNT, #EggHub.eggs) do
		local egg = EggHub.eggs[index]
		local color = egg.owned and Color3.fromRGB(80, 200, 255) or Color3.fromHSV(math.clamp(egg.rank / #RARITY_ORDER, 0, 1) * 0.8, 0.8, 1)

		local highlight = Instance.new("Highlight")
		highlight.Adornee = egg.instance
		highlight.FillColor = color
		highlight.FillTransparency = 0.6
		highlight.OutlineColor = color
		highlight.Parent = screenGui
		table.insert(espObjects, highlight)

		local billboard = Instance.new("BillboardGui")
		billboard.Adornee = egg.instance:IsA("Model") and (egg.instance.PrimaryPart or egg.instance:FindFirstChildWhichIsA("BasePart", true)) or egg.instance
		billboard.Size = UDim2.fromOffset(160, 36)
		billboard.StudsOffset = Vector3.new(0, 3, 0)
		billboard.AlwaysOnTop = true
		billboard.Parent = screenGui
		table.insert(espObjects, billboard)

		local label = Instance.new("TextLabel")
		label.Size = UDim2.fromScale(1, 1)
		label.BackgroundTransparency = 1
		label.Font = Enum.Font.GothamBold
		label.TextSize = 13
		label.TextColor3 = color
		label.TextStrokeTransparency = 0
		label.Text = string.format("#%d %s\n%s | $%s", index, egg.instance.Name, egg.rarity or "?", tostring(egg.value))
		label.Parent = billboard
	end
end

---Create an instance with properties.
---@param className string
---@param properties table
---@return Instance
local function create(className, properties)
	local instance = Instance.new(className)
	for key, value in pairs(properties) do
		if key ~= "Parent" then
			instance[key] = value
		end
	end

	instance.Parent = properties.Parent
	return instance
end

---Add rounded corners to a UI element.
---@param parent Instance
local function round(parent)
	create("UICorner", { CornerRadius = UDim.new(0, 6), Parent = parent })
end

---Create a toggle button bound to a callback.
---@param parent Instance
---@param text string
---@param callback function
---@return TextButton
local function createToggle(parent, text, callback)
	local state = false
	local button = create("TextButton", {
		Size = UDim2.new(1, 0, 0, 28),
		BackgroundColor3 = Color3.fromRGB(40, 40, 48),
		TextColor3 = Color3.fromRGB(230, 230, 230),
		Font = Enum.Font.GothamMedium,
		TextSize = 13,
		Text = text .. ": OFF",
		Parent = parent,
	})
	round(button)

	button.MouseButton1Click:Connect(function()
		state = not state
		button.Text = text .. (state and ": ON" or ": OFF")
		button.BackgroundColor3 = state and Color3.fromRGB(70, 140, 90) or Color3.fromRGB(40, 40, 48)
		callback(state)
	end)

	return button
end

---Create a plain action button.
---@param parent Instance
---@param text string
---@param callback function
---@return TextButton
local function createButton(parent, text, callback)
	local button = create("TextButton", {
		Size = UDim2.new(1, 0, 0, 28),
		BackgroundColor3 = Color3.fromRGB(55, 75, 130),
		TextColor3 = Color3.fromRGB(240, 240, 240),
		Font = Enum.Font.GothamMedium,
		TextSize = 13,
		Text = text,
		Parent = parent,
	})
	round(button)
	button.MouseButton1Click:Connect(callback)
	return button
end

---Create a labelled number input.
---@param parent Instance
---@param text string
---@param default number
---@param callback function
local function createNumberBox(parent, text, default, callback)
	local box = create("TextBox", {
		Size = UDim2.new(1, 0, 0, 28),
		BackgroundColor3 = Color3.fromRGB(30, 30, 36),
		TextColor3 = Color3.fromRGB(230, 230, 230),
		PlaceholderText = text,
		Font = Enum.Font.Gotham,
		TextSize = 13,
		Text = text .. ": " .. default,
		ClearTextOnFocus = true,
		Parent = parent,
	})
	round(box)

	box.FocusLost:Connect(function()
		local number = parseMoney(box.Text) or default
		default = number
		box.Text = text .. ": " .. number
		callback(number)
	end)
end

---Rebuild the egg list in the UI.
local function refreshEggList()
	if not eggList then
		return
	end

	for _, child in ipairs(eggList:GetChildren()) do
		if child:IsA("TextButton") then
			child:Destroy()
		end
	end

	for index, egg in ipairs(EggHub.eggs) do
		if index > 25 then
			break
		end

		local button = create("TextButton", {
			Size = UDim2.new(1, -6, 0, 24),
			BackgroundColor3 = egg.owned and Color3.fromRGB(35, 55, 70) or Color3.fromRGB(38, 38, 44),
			TextColor3 = Color3.fromRGB(220, 220, 220),
			Font = Enum.Font.Gotham,
			TextSize = 12,
			TextXAlignment = Enum.TextXAlignment.Left,
			Text = string.format("  %d. %s [%s] $%s%s", index, egg.instance.Name, egg.rarity or "?", tostring(egg.value), egg.owned and " (yours)" or ""),
			Parent = eggList,
		})
		round(button)

		-- Click an egg to steal that one directly.
		button.MouseButton1Click:Connect(function()
			task.spawn(function()
				if EggHub.grab(egg) then
					EggHub.goHome()
				end

				if not EggHub.farm.enabled then
					EggHub.toggleFly(false)
				end

				setStatus("idle")
			end)
		end)
	end

	eggList.CanvasSize = UDim2.fromOffset(0, math.min(#EggHub.eggs, 25) * 28)
end

---Scan and refresh every view that depends on the egg list.
local function scanAndRefresh()
	local eggs = EggHub.scan()
	refreshEggList()
	refreshEsp()
	setStatus(string.format("found %d eggs", #eggs))
end

---Build the hub UI.
local function buildUi()
	screenGui = create("ScreenGui", { Name = "EggHub", ResetOnSpawn = false, ZIndexBehavior = Enum.ZIndexBehavior.Sibling, Parent = gethui() })

	local main = create("Frame", {
		Size = UDim2.fromOffset(460, 340),
		Position = UDim2.new(0.5, -230, 0.5, -170),
		BackgroundColor3 = Color3.fromRGB(22, 22, 27),
		Active = true,
		Draggable = true,
		Parent = screenGui,
	})
	round(main)

	create("TextLabel", {
		Size = UDim2.new(1, -20, 0, 30),
		Position = UDim2.fromOffset(10, 4),
		BackgroundTransparency = 1,
		Font = Enum.Font.GothamBold,
		TextSize = 16,
		TextXAlignment = Enum.TextXAlignment.Left,
		TextColor3 = Color3.fromRGB(255, 210, 90),
		Text = "Egg Hub  |  RightShift to hide",
		Parent = main,
	})

	statusLabel = create("TextLabel", {
		Size = UDim2.new(1, -20, 0, 18),
		Position = UDim2.new(0, 10, 1, -22),
		BackgroundTransparency = 1,
		Font = Enum.Font.Gotham,
		TextSize = 12,
		TextXAlignment = Enum.TextXAlignment.Left,
		TextColor3 = Color3.fromRGB(170, 170, 170),
		Text = "Status: idle",
		Parent = main,
	})

	local controls = create("Frame", {
		Size = UDim2.fromOffset(170, 270),
		Position = UDim2.fromOffset(10, 40),
		BackgroundTransparency = 1,
		Parent = main,
	})
	create("UIListLayout", { Padding = UDim.new(0, 6), Parent = controls })

	local flyToggle = createToggle(controls, "Fly (F)", function(state)
		EggHub.toggleFly(state)
	end)

	createToggle(controls, "Auto Farm", function(state)
		EggHub.toggleFarm(state)
	end)

	createToggle(controls, "Egg ESP", function(state)
		EggHub.esp.enabled = state
		refreshEsp()
	end)

	createButton(controls, "Scan Eggs", scanAndRefresh)

	createButton(controls, "Set Home Here", function()
		local rootPart = getCharacter()
		if rootPart then
			EggHub.farm.home = rootPart.Position
			setStatus("home set")
		end
	end)

	createNumberBox(controls, "Fly Speed", EggHub.fly.speed, function(number)
		EggHub.fly.speed = math.clamp(number, 1, 500)
	end)

	createNumberBox(controls, "Min Value", EggHub.farm.minValue, function(number)
		EggHub.farm.minValue = number
	end)

	eggList = create("ScrollingFrame", {
		Size = UDim2.fromOffset(265, 270),
		Position = UDim2.fromOffset(188, 40),
		BackgroundColor3 = Color3.fromRGB(28, 28, 34),
		BorderSizePixel = 0,
		ScrollBarThickness = 4,
		CanvasSize = UDim2.new(),
		Parent = main,
	})
	round(eggList)
	create("UIListLayout", { Padding = UDim.new(0, 4), HorizontalAlignment = Enum.HorizontalAlignment.Center, Parent = eggList })
	create("UIPadding", { PaddingTop = UDim.new(0, 4), Parent = eggList })

	-- Keybinds for fly and hiding the UI.
	table.insert(connections, userInputService.InputBegan:Connect(function(input, gameProcessed)
		if gameProcessed then
			return
		end

		if input.KeyCode == TOGGLE_UI_KEY then
			main.Visible = not main.Visible
		elseif input.KeyCode == TOGGLE_FLY_KEY then
			EggHub.toggleFly()
			flyToggle.Text = "Fly (F)" .. (EggHub.fly.enabled and ": ON" or ": OFF")
			flyToggle.BackgroundColor3 = EggHub.fly.enabled and Color3.fromRGB(70, 140, 90) or Color3.fromRGB(40, 40, 48)
		end
	end))
end

---Disconnect everything and remove the UI.
function EggHub.detach()
	EggHub.farm.enabled = false
	EggHub.toggleFly(false)
	clearEsp()

	for _, connection in ipairs(connections) do
		connection:Disconnect()
	end

	table.clear(connections)

	if screenGui then
		screenGui:Destroy()
	end
end

---Install the fly loop, UI and velocity read spoof.
function EggHub.init()
	table.insert(connections, runService.Heartbeat:Connect(onHeartbeat))

	-- Reset tracked position on respawn so we don't yank the new character back.
	table.insert(connections, localPlayer.CharacterAdded:Connect(function()
		flyCFrame = nil
		currentVelocity = Vector3.zero
	end))

	buildUi()
	scanAndRefresh()

	-- Game scripts reading root velocity while flying see a resting value.
	if not shared.EggHubIndexHooked then
		shared.EggHubIndexHooked = true

		local oldIndex
		oldIndex = hookmetamethod(game, "__index", newcclosure(function(self, key)
			if not checkcaller() and (key == "AssemblyLinearVelocity" or key == "Velocity") then
				local hub = shared.EggHub
				local character = localPlayer.Character
				if hub and hub.fly.enabled and character and self == character:FindFirstChild("HumanoidRootPart") then
					return Vector3.zero
				end
			end

			return oldIndex(self, key)
		end))
	end
end

---This is called when initialization errors.
---@param error string
local function onInitializeError(error)
	warn("Failed to initialize Egg Hub.")
	warn(error)
	warn(debug.traceback())
	EggHub.detach()
end

-- Detach the previous instance before re-initializing.
if shared.EggHub then
	shared.EggHub.detach()
end

shared.EggHub = EggHub

xpcall(EggHub.init, onInitializeError)
