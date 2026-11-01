-- Check for table that is shared between executions.
if not shared then
	return warn("No shared, no script.")
end

-- Initialize Luraph globals if they do not exist.
loadstring("getfenv().LPH_NO_VIRTUALIZE = function(...) return ... end")()

getfenv().PP_SCRAMBLE_NUM = function(...) return ... end
getfenv().PP_SCRAMBLE_STR = function(...) return ... end
getfenv().PP_SCRAMBLE_RE_NUM = function(...) return ... end

local Fly = { enabled = false, speed = 60, boost = 2.5, smoothing = 10 }

-- Constants.
local TOGGLE_KEY = Enum.KeyCode.F
local BOOST_KEY = Enum.KeyCode.LeftShift

-- Services.
local playersService = cloneref(game:GetService("Players"))
local runService = cloneref(game:GetService("RunService"))
local userInputService = cloneref(game:GetService("UserInputService"))

-- State.
local localPlayer = playersService.LocalPlayer
local connections = {}
local flyCFrame = nil
local currentVelocity = Vector3.zero
local oldIndex = nil

---Return the local character's root part and humanoid.
---@return BasePart?, Humanoid?
local function getCharacter()
	local character = localPlayer.Character
	if not character then
		return nil, nil
	end

	return character:FindFirstChild("HumanoidRootPart"), character:FindFirstChildOfClass("Humanoid")
end

---Build a camera-relative direction from held keys.
---@return Vector3
local function getInputDirection()
	local camera = workspace.CurrentCamera
	local look, right = camera.CFrame.LookVector, camera.CFrame.RightVector
	local direction = Vector3.zero

	if userInputService:IsKeyDown(Enum.KeyCode.W) then direction += look end
	if userInputService:IsKeyDown(Enum.KeyCode.S) then direction -= look end
	if userInputService:IsKeyDown(Enum.KeyCode.D) then direction += right end
	if userInputService:IsKeyDown(Enum.KeyCode.A) then direction -= right end
	if userInputService:IsKeyDown(Enum.KeyCode.Space) then direction += Vector3.yAxis end
	if userInputService:IsKeyDown(Enum.KeyCode.LeftControl) then direction -= Vector3.yAxis end

	return direction.Magnitude > 0 and direction.Unit or direction
end

---Step the fly: we own the position ourselves, so physics never accumulates velocity.
---@param deltaTime number
local function onHeartbeat(deltaTime)
	if not Fly.enabled then
		return
	end

	local rootPart, humanoid = getCharacter()
	if not (rootPart and humanoid and humanoid.Health > 0) then
		return
	end

	flyCFrame = flyCFrame or rootPart.CFrame

	-- Ease toward the target velocity for a smooth glide instead of snapping.
	local targetSpeed = Fly.speed * (userInputService:IsKeyDown(BOOST_KEY) and Fly.boost or 1)
	local targetVelocity = getInputDirection() * targetSpeed
	currentVelocity = currentVelocity:Lerp(targetVelocity, math.clamp(deltaTime * Fly.smoothing, 0, 1))

	local camLook = workspace.CurrentCamera.CFrame.LookVector
	local flatLook = Vector3.new(camLook.X, 0, camLook.Z)
	local position = flyCFrame.Position + currentVelocity * deltaTime

	flyCFrame = flatLook.Magnitude > 0 and CFrame.lookAt(position, position + flatLook) or CFrame.new(position)

	-- Zero physics velocity so gravity and server velocity checks see a resting part.
	rootPart.AssemblyLinearVelocity = Vector3.zero
	rootPart.AssemblyAngularVelocity = Vector3.zero
	rootPart.CFrame = flyCFrame
end

---Toggle flight on or off.
---@param state boolean?
function Fly.toggle(state)
	Fly.enabled = state == nil and not Fly.enabled or state
	flyCFrame = nil
	currentVelocity = Vector3.zero

	local _, humanoid = getCharacter()
	if humanoid then
		humanoid:ChangeState(Fly.enabled and Enum.HumanoidStateType.Physics or Enum.HumanoidStateType.GettingUp)
	end
end

---Disconnect everything and restore hooks.
function Fly.detach()
	Fly.toggle(false)

	for _, connection in ipairs(connections) do
		connection:Disconnect()
	end

	table.clear(connections)
end

---Install connections and the velocity read spoof.
function Fly.init()
	table.insert(connections, runService.Heartbeat:Connect(onHeartbeat))

	table.insert(connections, userInputService.InputBegan:Connect(function(input, gameProcessed)
		if gameProcessed or input.KeyCode ~= TOGGLE_KEY then
			return
		end

		Fly.toggle()
	end))

	-- Reset tracked position on respawn so we don't yank the new character back.
	table.insert(connections, localPlayer.CharacterAdded:Connect(function()
		flyCFrame = nil
		currentVelocity = Vector3.zero
	end))

	-- Game scripts reading root velocity while flying see a grounded value.
	if not shared.FlyIndexHooked then
		shared.FlyIndexHooked = true
		oldIndex = hookmetamethod(game, "__index", newcclosure(function(self, key)
			if not checkcaller() and shared.Fly and shared.Fly.enabled and (key == "AssemblyLinearVelocity" or key == "Velocity") then
				local rootPart = getCharacter()
				if self == rootPart then
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
	warn("Failed to initialize fly.")
	warn(error)
	warn(debug.traceback())
	Fly.detach()
end

-- Detach the previous instance before re-initializing.
if shared.Fly then
	shared.Fly.detach()
end

shared.Fly = Fly

xpcall(Fly.init, onInitializeError)
