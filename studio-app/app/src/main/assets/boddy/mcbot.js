// Boddy's Minecraft Java bot. A Mineflayer player that joins a world and takes commands over
// HTTP on 127.0.0.1:7320 (used by the `mcbot` command, the Minecraft Bot app and the AIs), or
// from in-game chat: "boddy follow", "boddy come", "boddy mine oak_log 10", "boddy stop".
const http = require('http')
const mineflayer = require('mineflayer')
const { pathfinder, Movements, goals } = require('mineflayer-pathfinder')
const collectBlock = require('mineflayer-collectblock').plugin

const opts = JSON.parse(process.argv[2] || '{}')
const bot = mineflayer.createBot({
  host: opts.host || '127.0.0.1',
  port: Number(opts.port) || 25565,
  username: opts.username || 'BoddyBot',
  auth: opts.auth || 'offline',
  version: opts.version || false,
})
bot.loadPlugin(pathfinder)
bot.loadPlugin(collectBlock)

const log = []
const note = (msg) => { log.push(`[${new Date().toLocaleTimeString()}] ${msg}`); if (log.length > 50) log.shift(); console.log(msg) }
let task = 'idle'

bot.once('spawn', () => {
  bot.pathfinder.setMovements(new Movements(bot))
  note(`Joined as ${bot.username}`)
  bot.chat('Hi! Boddy bot here. Say "boddy follow", "boddy come", "boddy mine <block> <n>" or "boddy stop".')
})
bot.on('kicked', (reason) => note(`Kicked: ${JSON.stringify(reason)}`))
bot.on('error', (err) => note(`Error: ${err.message}`))
bot.on('end', () => { note('Disconnected'); setTimeout(() => process.exit(0), 500) })
bot.on('death', () => note('Died (respawning)'))

function player(name) {
  const p = name ? bot.players[name] : Object.values(bot.players).find((p) => p.username !== bot.username && p.entity)
  if (!p || !p.entity) throw new Error(name ? `Can't see player ${name} nearby` : "Can't see any player nearby")
  return p
}

function stop() {
  bot.pathfinder.setGoal(null)
  bot.collectBlock.cancelTask?.()
  bot.pvp?.stop?.()
  task = 'idle'
}

const commands = {
  status() {
    const e = bot.entity
    return {
      task,
      position: e && e.position.floored(),
      health: bot.health, food: bot.food,
      inventory: bot.inventory.items().map((i) => `${i.count} ${i.name}`),
      players: Object.keys(bot.players),
      recent: log.slice(-10),
    }
  },
  say(...words) { bot.chat(words.join(' ')); return 'said it' },
  follow(name) {
    const p = player(name)
    bot.pathfinder.setGoal(new goals.GoalFollow(p.entity, 2), true)
    task = `following ${p.username}`
    return task
  },
  come(name) {
    const p = player(name).entity.position
    bot.pathfinder.setGoal(new goals.GoalNear(p.x, p.y, p.z, 1))
    task = 'coming'
    return task
  },
  goto(x, y, z) {
    bot.pathfinder.setGoal(new goals.GoalBlock(Number(x), Number(y), Number(z)))
    task = `going to ${x} ${y} ${z}`
    return task
  },
  async mine(blockName, count = 1) {
    const type = bot.registry.blocksByName[blockName]
    if (!type) throw new Error(`Unknown block "${blockName}" (use names like oak_log, stone, iron_ore)`)
    const blocks = bot.findBlocks({ matching: type.id, maxDistance: 64, count: Number(count) })
    if (!blocks.length) throw new Error(`No ${blockName} within 64 blocks`)
    task = `mining ${blocks.length} ${blockName}`
    bot.collectBlock.collect(blocks.map((p) => bot.blockAt(p)))
      .then(() => { note(`Finished mining ${blockName}`); task = 'idle' })
      .catch((err) => { note(`Mining stopped: ${err.message}`); task = 'idle' })
    return task
  },
  attack() {
    const mob = bot.nearestEntity((e) => e.type === 'hostile' || e.kind === 'Hostile mobs')
    if (!mob) throw new Error('No hostile mob nearby')
    bot.pathfinder.setGoal(new goals.GoalFollow(mob, 1), true)
    const timer = setInterval(() => {
      if (!mob.isValid) { clearInterval(timer); stop(); note('Mob defeated'); return }
      if (bot.entity.position.distanceTo(mob.position) < 3.5) bot.attack(mob)
    }, 400)
    task = `attacking ${mob.name}`
    return task
  },
  stop() { stop(); return 'stopped' },
  quit() { bot.quit(); return 'leaving' },
}

async function run(cmd, args) {
  const fn = commands[cmd]
  if (!fn) throw new Error(`Unknown command "${cmd}". Try: ${Object.keys(commands).join(', ')}`)
  return fn(...args)
}

bot.on('chat', async (username, message) => {
  if (username === bot.username) return
  const [word, cmd, ...args] = message.trim().split(/\s+/)
  if (word.toLowerCase() !== 'boddy' || !cmd) return
  // "follow"/"come" with no name mean the player who asked.
  if ((cmd === 'follow' || cmd === 'come') && !args.length) args.push(username)
  try { bot.chat(String(await run(cmd, args))) } catch (err) { bot.chat(err.message) }
})

http.createServer((req, res) => {
  let body = ''
  req.on('data', (c) => { body += c })
  req.on('end', async () => {
    res.setHeader('Content-Type', 'application/json')
    try {
      const { cmd, args = [] } = JSON.parse(body || '{}')
      res.end(JSON.stringify({ ok: true, result: await run(cmd, args) }))
    } catch (err) {
      res.end(JSON.stringify({ ok: false, error: err.message }))
    }
  })
}).listen(7320, '127.0.0.1')
