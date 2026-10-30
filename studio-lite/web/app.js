import * as THREE from 'three'
import { OrbitControls } from './vendor/OrbitControls.js'
import { TransformControls } from './vendor/TransformControls.js'

const token = location.hash.slice(1)
const $ = (id) => document.getElementById(id)
const PARTS = ['Part', 'SpawnLocation']
const SCRIPTS = ['Script', 'LocalScript', 'ModuleScript']
const ICONS = { Workspace: '🌍', Lighting: '💡', ReplicatedStorage: '📦', ServerScriptService: '⚙', ServerStorage: '🗄',
  StarterGui: '🖼', StarterPack: '🎒', StarterPlayer: '🧍', StarterPlayerScripts: '📜', Part: '⬛', SpawnLocation: '🟩',
  Model: '🧩', Folder: '📁', Script: '📄', LocalScript: '📃', ModuleScript: '📘' }

let meta = {}
let project = null
let projectName = ''
let selected = null
let openTabs = []          // script ids open in the editor
let activeTab = 'scene'
let dirty = false
let bad = new Set()        // ids of scripts with errors
const collapsed = new Set()

// ---- Server ------------------------------------------------------------------------------------

async function api(path, body = {}) {
  const r = await fetch('/api/' + path, { method: 'POST', headers: { 'X-Token': token, 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
  const data = await r.json().catch(() => ({ error: 'no answer from Studio Lite' }))
  if (!r.ok) throw new Error(data.error || r.statusText)
  return data
}

function log(text, kind = '') {
  const line = document.createElement('div')
  line.className = kind
  line.textContent = new Date().toLocaleTimeString() + '  ' + text
  $('log').appendChild(line)
  $('log').scrollTop = $('log').scrollHeight
}

function status(text) { $('status').textContent = text }

// ---- Tree helpers ------------------------------------------------------------------------------

function* walk(nodes, parent = null) {
  for (const n of nodes) {
    yield [n, parent]
    yield* walk(n.children || [], n)
  }
}
const byId = (id) => { for (const [n] of walk(project.tree)) if (n.id === id) return n; return null }
const parentOf = (id) => { for (const [n, p] of walk(project.tree)) if (n.id === id) return p; return null }
const newId = () => Math.random().toString(16).slice(2, 14)
const service = (name) => project.tree.find((n) => n.className === name)

function uniqueName(parent, base) {
  const names = new Set((parent ? parent.children : project.tree).map((c) => c.name))
  if (!names.has(base)) return base
  for (let i = 2; ; i++) if (!names.has(base + i)) return base + i
}

// ---- Saving ------------------------------------------------------------------------------------

let saveTimer = null
function changed() {
  dirty = true
  status('Unsaved changes…')
  clearTimeout(saveTimer)
  saveTimer = setTimeout(save, 1200)
}

async function save() {
  clearTimeout(saveTimer)
  if (!project || !dirty) return
  try {
    await api('save', { name: projectName, project })
    dirty = false
    status('Saved')
  } catch (e) {
    status('Not saved')
    log('Save failed: ' + e.message, 'err')
  }
}
window.addEventListener('beforeunload', (e) => { if (dirty) { save(); e.preventDefault() } })

// ---- Explorer ----------------------------------------------------------------------------------

function renderTree() {
  const box = $('tree')
  box.textContent = ''
  const add = (n, depth) => {
    const row = document.createElement('div')
    row.className = 'row' + (selected === n.id ? ' sel' : '') + (bad.has(n.id) ? ' bad' : '')
    row.style.paddingLeft = 4 + depth * 12 + 'px'
    const kids = n.children && n.children.length
    row.innerHTML = '<span class="tw"></span><span class="ic"></span><span class="nm"></span>'
    row.children[0].textContent = kids ? (collapsed.has(n.id) ? '▸' : '▾') : ''
    row.children[1].textContent = ICONS[n.className] || '•'
    row.children[2].textContent = n.name
    row.children[0].onclick = (e) => { e.stopPropagation(); collapsed.has(n.id) ? collapsed.delete(n.id) : collapsed.add(n.id); renderTree() }
    row.onclick = () => select(n.id)
    row.ondblclick = () => { if (SCRIPTS.includes(n.className)) openScript(n.id) }
    box.appendChild(row)
    if (kids && !collapsed.has(n.id)) for (const c of n.children) add(c, depth + 1)
  }
  for (const n of project.tree) add(n, 0)
}

function select(id) {
  selected = id
  renderTree()
  renderProps()
  const mesh = meshes.get(id)
  if (mesh && gizmo) gizmo.attach(mesh)
  else if (gizmo) gizmo.detach()
  const n = id && byId(id)
  if (n && SCRIPTS.includes(n.className)) openScript(id)
}

// ---- Properties --------------------------------------------------------------------------------

const hex = (c) => '#' + c.map((v) => Math.round(v).toString(16).padStart(2, '0')).join('')
const unhex = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16))

function renderProps() {
  const list = $('propList')
  list.textContent = ''
  const n = selected && byId(selected)
  if (!n) { list.innerHTML = '<span style="grid-column: span 2; color: var(--dim)">Select something in Explorer or the 3D view.</span>'; return }
  const row = (label, el) => { const l = document.createElement('label'); l.textContent = label; list.append(l, el) }
  const isService = !parentOf(n.id) || n.className === 'StarterPlayerScripts'
  const name = document.createElement('input')
  name.value = n.name
  name.disabled = isService
  name.onchange = () => { if (name.value.trim()) { n.name = name.value.trim().slice(0, 100); renderTree(); renderTabs(); changed() } }
  row('Name', name)
  const cls = document.createElement('input'); cls.value = n.className; cls.disabled = true; row('Class', cls)
  if (!PARTS.includes(n.className)) return
  const p = n.properties
  const vec = (key, step) => {
    const box = document.createElement('div'); box.className = 'vec'
    p[key].forEach((v, i) => {
      const inp = document.createElement('input'); inp.type = 'number'; inp.step = step; inp.value = +(+v).toFixed(3)
      inp.onchange = () => { const x = parseFloat(inp.value); if (Number.isFinite(x)) { p[key][i] = key === 'Size' ? Math.max(0.05, x) : x; updateMesh(n); changed() } }
      box.appendChild(inp)
    })
    row(key, box)
  }
  vec('Position', 0.5); vec('Orientation', 15); vec('Size', 0.5)
  const color = document.createElement('input'); color.type = 'color'; color.value = hex(p.Color)
  color.oninput = () => { p.Color = unhex(color.value); updateMesh(n); changed() }
  row('Color', color)
  const pick = (key, options) => {
    const s = document.createElement('select')
    for (const o of options) s.add(new Option(o, o, false, o === p[key]))
    s.onchange = () => { p[key] = s.value; updateMesh(n); changed() }
    row(key, s)
  }
  pick('Material', meta.materials)
  if (n.className === 'Part') pick('Shape', meta.shapes)
  const tr = document.createElement('input'); tr.type = 'number'; tr.min = 0; tr.max = 1; tr.step = 0.1; tr.value = p.Transparency
  tr.onchange = () => { p.Transparency = Math.min(1, Math.max(0, parseFloat(tr.value) || 0)); updateMesh(n); changed() }
  row('Transparency', tr)
  for (const key of ['Anchored', 'CanCollide']) {
    const c = document.createElement('input'); c.type = 'checkbox'; c.checked = !!p[key]
    c.onchange = () => { p[key] = c.checked; changed() }
    row(key, c)
  }
}

// ---- Adding, copying, deleting -----------------------------------------------------------------

const ADD = [
  ['Part', 'Part', { Shape: 'Block' }], ['Ball', 'Part', { Shape: 'Ball', Size: [4, 4, 4] }], ['Cylinder', 'Part', { Shape: 'Cylinder', Size: [4, 4, 4] }],
  ['SpawnLocation', 'SpawnLocation', { Size: [12, 1, 12] }], ['Model', 'Model'], ['Folder', 'Folder'],
  ['Script', 'Script'], ['LocalScript', 'LocalScript'], ['ModuleScript', 'ModuleScript'],
]
const STARTER = {
  Script: '-- Runs on the server.\nprint("Hello from the server")\n',
  LocalScript: '-- Runs on each player\'s device.\nlocal player = game.Players.LocalPlayer\nprint("Hi " .. player.Name)\n',
  ModuleScript: 'local module = {}\n\nfunction module.hello()\n\treturn "hello"\nend\n\nreturn module\n',
}

function buildAddMenu() {
  const menu = $('addMenu')
  for (const [label, cls, extra] of ADD) {
    const b = document.createElement('button')
    b.textContent = (ICONS[cls] || '') + ' ' + label
    b.onclick = () => { menu.hidden = true; addObject(label, cls, extra || {}) }
    menu.appendChild(b)
  }
  $('add').onclick = (e) => { e.stopPropagation(); menu.hidden = !menu.hidden }
  document.addEventListener('click', () => { menu.hidden = true })
}

function addObject(label, cls, extra) {
  const sel = selected && byId(selected)
  let parent
  if (PARTS.includes(cls)) {
    // Parts go in the selected model or folder inside Workspace, otherwise Workspace.
    parent = sel && ['Model', 'Folder', 'Workspace'].includes(sel.className) && inWorkspace(sel) ? sel : service('Workspace')
  } else if (SCRIPTS.includes(cls)) {
    parent = sel && !PARTS.includes(sel.className) && !SCRIPTS.includes(sel.className) ? sel
      : sel && PARTS.includes(sel.className) ? sel
      : service(cls === 'LocalScript' ? 'StarterPlayer' : cls === 'ModuleScript' ? 'ReplicatedStorage' : 'ServerScriptService')
    if (parent.className === 'StarterPlayer') parent = parent.children.find((c) => c.className === 'StarterPlayerScripts') || parent
  } else {
    parent = sel && !SCRIPTS.includes(sel.className) && !PARTS.includes(sel.className) ? sel : service('Workspace')
  }
  const n = { id: newId(), name: uniqueName(parent, label), className: cls, properties: {}, children: [] }
  if (PARTS.includes(cls)) {
    const at = focusPoint()
    n.properties = { Position: at, Orientation: [0, 0, 0], Size: [4, 1, 2], Color: cls === 'SpawnLocation' ? [163, 162, 165] : [163, 162, 165],
      Material: cls === 'SpawnLocation' ? 'SmoothPlastic' : 'Plastic', Transparency: 0, Anchored: true, CanCollide: true, Shape: 'Block', ...extra }
    n.properties.Position[1] = Math.max(n.properties.Position[1], n.properties.Size[1] / 2)
  }
  if (SCRIPTS.includes(cls)) n.source = STARTER[cls]
  parent.children.push(n)
  collapsed.delete(parent.id)
  rebuildScene()
  select(n.id)
  changed()
}

function inWorkspace(n) {
  for (let p = n; p; p = parentOf(p.id)) if (p.className === 'Workspace') return true
  return false
}

function copyNode(n) {
  const c = JSON.parse(JSON.stringify(n))
  for (const [x] of walk([c])) x.id = newId()
  return c
}

$('dup').onclick = () => {
  const n = selected && byId(selected); const p = n && parentOf(n.id)
  if (!p) return log('Select an object to copy.')
  const c = copyNode(n)
  c.name = uniqueName(p, n.name)
  if (c.properties && c.properties.Position) c.properties.Position[0] += (c.properties.Size || [4])[0]
  p.children.splice(p.children.indexOf(n) + 1, 0, c)
  rebuildScene(); select(c.id); changed()
}

$('del').onclick = () => {
  const n = selected && byId(selected); const p = n && parentOf(n.id)
  if (!p || n.className === 'StarterPlayerScripts') return log('Services can\'t be deleted.')
  if (!confirm('Delete ' + n.name + (n.children.length ? ' and everything in it' : '') + '?')) return
  p.children.splice(p.children.indexOf(n), 1)
  for (const [x] of walk([n])) closeTab(x.id)
  selected = null
  rebuildScene(); renderTree(); renderProps(); changed()
}

// ---- Script editor -----------------------------------------------------------------------------

function openScript(id) {
  if (!openTabs.includes(id)) openTabs.push(id)
  showTab(id)
}

function closeTab(id) {
  openTabs = openTabs.filter((t) => t !== id)
  if (activeTab === id) showTab('scene')
  else renderTabs()
}

function renderTabs() {
  const tabs = $('tabs')
  tabs.textContent = ''
  const tab = (id, label) => {
    const t = document.createElement('div')
    t.className = 'tab' + (activeTab === id ? ' on' : '')
    t.textContent = label
    t.onclick = () => showTab(id)
    if (id !== 'scene') {
      const x = document.createElement('span'); x.className = 'x'; x.textContent = '✕'
      x.onclick = (e) => { e.stopPropagation(); closeTab(id) }
      t.appendChild(x)
    }
    tabs.appendChild(t)
  }
  tab('scene', '🌍 Scene')
  for (const id of openTabs) { const n = byId(id); if (n) tab(id, (ICONS[n.className] || '') + ' ' + n.name) }
}

function showTab(id) {
  activeTab = id
  const n = id !== 'scene' && byId(id)
  $('viewport').hidden = !!n
  $('editor').hidden = !n
  if (n) {
    $('code').value = n.source || ''
    gutter()
    $('code').focus()
  } else {
    resize()
  }
  renderTabs()
}

function gutter() {
  const lines = $('code').value.split('\n').length
  $('gutter').textContent = Array.from({ length: lines }, (_, i) => i + 1).join('\n')
  $('gutter').scrollTop = $('code').scrollTop
}

$('code').addEventListener('input', () => {
  const n = byId(activeTab)
  if (n) { n.source = $('code').value; bad.delete(n.id); changed() }
  gutter()
})
$('code').addEventListener('scroll', () => { $('gutter').scrollTop = $('code').scrollTop })
$('code').addEventListener('keydown', (e) => {
  const ta = $('code')
  if (e.key === 'Tab') {
    e.preventDefault()
    ta.setRangeText('\t', ta.selectionStart, ta.selectionEnd, 'end')
    ta.dispatchEvent(new Event('input'))
  } else if (e.key === 'Enter') {
    // Keep the indent of the line above, and indent one more after "do", "then", "function(...)" or "{".
    e.preventDefault()
    const before = ta.value.slice(0, ta.selectionStart)
    const line = before.slice(before.lastIndexOf('\n') + 1)
    let indent = line.match(/^\s*/)[0]
    if (/(\bdo|\bthen|\belse|\brepeat|function\s*[\w.:]*\s*\([^)]*\)|[{(])\s*$/.test(line)) indent += '\t'
    ta.setRangeText('\n' + indent, ta.selectionStart, ta.selectionEnd, 'end')
    ta.dispatchEvent(new Event('input'))
  } else if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault(); save()
  }
})

// ---- 3D scene ----------------------------------------------------------------------------------

let renderer = null, scene, camera, orbit, gizmo
const meshes = new Map()     // instance id -> mesh

function initScene() {
  const canvas = $('scene')
  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: false, powerPreference: 'low-power' })
  } catch (e) {
    $('noGl').hidden = false
    log('No 3D view: ' + e.message, 'err')
    return
  }
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5))
  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x9fc9f0)
  camera = new THREE.PerspectiveCamera(60, 1, 0.5, 4000)
  camera.position.set(30, 25, 30)
  scene.add(new THREE.HemisphereLight(0xffffff, 0x5a6a4a, 1.6))
  const sun = new THREE.DirectionalLight(0xffffff, 1.4); sun.position.set(40, 80, 20); scene.add(sun)
  const grid = new THREE.GridHelper(512, 128, 0x335533, 0x447744); grid.position.y = 0.01; grid.material.opacity = 0.25; grid.material.transparent = true
  scene.add(grid)
  orbit = new OrbitControls(camera, canvas)
  orbit.target.set(0, 0, 0)
  orbit.enableDamping = false
  orbit.addEventListener('change', render)
  gizmo = new TransformControls(camera, canvas)
  gizmo.setTranslationSnap(0.5); gizmo.setRotationSnap(THREE.MathUtils.degToRad(15)); gizmo.setScaleSnap(0.25)
  gizmo.addEventListener('dragging-changed', (e) => { orbit.enabled = !e.value; if (!e.value) { renderProps(); changed() } })
  gizmo.addEventListener('objectChange', () => fromMesh(gizmo.object))
  gizmo.addEventListener('change', render)
  scene.add(gizmo.getHelper ? gizmo.getHelper() : gizmo)
  // Tap to select: a tap that didn't move the view or the gizmo.
  let down = null
  canvas.addEventListener('pointerdown', (e) => { down = [e.clientX, e.clientY] })
  canvas.addEventListener('pointerup', (e) => {
    if (!down || Math.hypot(e.clientX - down[0], e.clientY - down[1]) > 6 || gizmo.dragging) return
    const r = canvas.getBoundingClientRect()
    const ray = new THREE.Raycaster()
    ray.setFromCamera(new THREE.Vector2((e.clientX - r.left) / r.width * 2 - 1, -(e.clientY - r.top) / r.height * 2 + 1), camera)
    const hit = ray.intersectObjects([...meshes.values()].filter((m) => m.userData.pickable))[0]
    select(hit ? hit.object.userData.id : null)
  })
  window.addEventListener('resize', resize)
  resize()
}

function resize() {
  if (!renderer) return
  const box = $('viewport')
  const w = box.clientWidth, h = box.clientHeight
  if (!w || !h) return
  renderer.setSize(w, h, false)
  camera.aspect = w / h
  camera.updateProjectionMatrix()
  render()
}

let frame = 0
function render() {
  if (!renderer || frame) return
  frame = requestAnimationFrame(() => { frame = 0; renderer.render(scene, camera) })
}

const geometries = {
  Block: new THREE.BoxGeometry(1, 1, 1),
  Ball: new THREE.SphereGeometry(0.5, 24, 16),
  Cylinder: new THREE.CylinderGeometry(0.5, 0.5, 1, 24).rotateZ(Math.PI / 2), // Roblox cylinders lie along X
}

function updateMesh(n) {
  const m = meshes.get(n.id)
  if (!m) return
  const p = n.properties
  const shape = n.className === 'Part' ? p.Shape : 'Block'
  m.geometry = geometries[shape] || geometries.Block
  m.position.set(...p.Position)
  m.rotation.set(...p.Orientation.map(THREE.MathUtils.degToRad), 'YXZ')
  let [x, y, z] = p.Size
  // A ball is as wide as its smallest side; a cylinder as round as its smaller cross side.
  if (shape === 'Ball') x = y = z = Math.min(x, y, z)
  if (shape === 'Cylinder') y = z = Math.min(y, z)
  m.scale.set(x, y, z)
  m.material.color.setRGB(...p.Color.map((c) => c / 255), THREE.SRGBColorSpace)
  m.material.transparent = p.Transparency > 0
  m.material.opacity = 1 - p.Transparency
  m.material.emissive.setRGB(...(p.Material === 'Neon' ? p.Color.map((c) => c / 400) : [0, 0, 0]), THREE.SRGBColorSpace)
  render()
}

function fromMesh(m) {
  const n = byId(m.userData.id)
  if (!n) return
  const p = n.properties
  p.Position = m.position.toArray().map((v) => +v.toFixed(3))
  const e = new THREE.Euler().setFromQuaternion(m.quaternion, 'YXZ')
  p.Orientation = [e.x, e.y, e.z].map((r) => +THREE.MathUtils.radToDeg(r).toFixed(2))
  p.Size = m.scale.toArray().map((v) => +Math.max(0.05, v).toFixed(3))
  updateMesh(n)
}

function rebuildScene() {
  if (!renderer) return
  for (const m of meshes.values()) { scene.remove(m); m.material.dispose() }
  meshes.clear()
  const ws = service('Workspace')
  if (ws) for (const [n] of walk(ws.children)) {
    if (!PARTS.includes(n.className)) continue
    const m = new THREE.Mesh(geometries.Block, new THREE.MeshLambertMaterial())
    // The baseplate is too big to grab by accident: it can be selected in Explorer only.
    m.userData = { id: n.id, pickable: Math.max(...n.properties.Size) < 200 }
    meshes.set(n.id, m)
    scene.add(m)
    updateMesh(n)
  }
  if (gizmo) { const m = meshes.get(selected); m ? gizmo.attach(m) : gizmo.detach() }
  render()
}

function focusPoint() {
  if (!renderer) return [0, 2, 0]
  return orbit.target.toArray().map((v) => Math.round(v * 2) / 2)
}

for (const b of $('modes').querySelectorAll('button')) {
  b.onclick = () => {
    for (const o of $('modes').querySelectorAll('button')) o.classList.toggle('on', o === b)
    if (gizmo) gizmo.setMode(b.dataset.mode)
  }
}

// ---- Projects ----------------------------------------------------------------------------------

async function openProject(name) {
  await save()
  project = await api('load', { name })
  projectName = name
  localStorage.setItem('studio-lite-last', name)
  selected = null; openTabs = []; bad = new Set()
  showTab('scene')
  renderTree(); renderProps(); rebuildScene()
  status('Opened ' + name)
}

async function refreshList(pick) {
  meta = await api('list')
  const sel = $('project')
  sel.textContent = ''
  for (const p of meta.projects) sel.add(new Option(p, p))
  if (!meta.projects.length) {
    const p = await api('create', { name: 'My Game' })
    return refreshList(p.name)
  }
  const want = pick || localStorage.getItem('studio-lite-last')
  sel.value = meta.projects.includes(want) ? want : meta.projects[0]
  await openProject(sel.value)
}

$('project').onchange = () => openProject($('project').value)
$('new').onclick = async () => {
  const name = prompt('Name for the new game (letters, numbers, spaces):', 'My Game 2')
  if (!name) return
  try { await api('create', { name: name.trim() }); await refreshList(name.trim()) } catch (e) { log(e.message, 'err') }
}

// ---- Check, export, publish, play --------------------------------------------------------------

async function check(quiet = false) {
  const r = await api('check', { project })
  if (!r.available) {
    log('Script checking needs Lune. Installing it (one time)…')
    const i = await api('install-lune')
    if (!i.lune) { log('Could not install Lune:\n' + i.log, 'err'); return true }
    return check(quiet)
  }
  bad = new Set(r.results.filter((x) => !x.ok).map((x) => x.id))
  for (const x of r.results) if (!x.ok) log(x.path + ': ' + x.error, 'err')
  if (!bad.size && !quiet) log('All ' + r.results.length + ' scripts look fine.', 'ok')
  renderTree()
  return bad.size === 0
}

$('check').onclick = () => check().catch((e) => log(e.message, 'err'))
$('export').onclick = async () => {
  try { await save(); const r = await api('export', { project }); log('Saved ' + r.path + ' (open it in Roblox Studio on a computer).', 'ok') } catch (e) { log(e.message, 'err') }
}
$('publish').onclick = async () => {
  try {
    await save()
    if (!(await check(true))) { log('Fix the script errors first (in red in Explorer).', 'err'); return }
    if (!meta.hasKey || !project.placeId) { openSettings(); return }
    status('Publishing…')
    const r = await api('publish', { project })
    log('Published version ' + (r.versionNumber ?? '?') + '. Press Play to try it in the Roblox app.', 'ok')
    status('Published')
  } catch (e) { status('Publish failed'); log(e.message, 'err') }
}
$('play').onclick = async () => {
  try { await api('play', { placeId: project.placeId }); log('Opening the game on your phone…') } catch (e) { log(e.message, 'err') }
}

function openSettings() {
  $('universe').value = project.universeId || ''
  $('place').value = project.placeId || ''
  $('apikey').value = ''
  $('apikey').placeholder = meta.hasKey ? '(a key is saved; type to replace it)' : '(saved only on this device)'
  $('settingsDlg').showModal()
}
$('settings').onclick = openSettings
$('saveSettings').onclick = async () => {
  project.universeId = $('universe').value.replace(/\D/g, '')
  project.placeId = $('place').value.replace(/\D/g, '')
  changed()
  if ($('apikey').value.trim()) {
    try { meta.hasKey = (await api('key', { key: $('apikey').value })).hasKey; log('API key saved.', 'ok') } catch (e) { log(e.message, 'err') }
  }
}
$('clearOut').onclick = () => { $('log').textContent = '' }

// ---- Start -------------------------------------------------------------------------------------

document.documentElement.style.setProperty('--bar', $('bar').offsetHeight + 'px')
new ResizeObserver(() => { document.documentElement.style.setProperty('--bar', $('bar').offsetHeight + 'px'); resize() }).observe($('bar'))
buildAddMenu()
initScene()
refreshList().then(() => log('Ready. Tap a part to select it; drag to turn the view, pinch or scroll to zoom.')).catch((e) => log(e.message, 'err'))
