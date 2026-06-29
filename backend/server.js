const http = require('http')
const WebSocket = require('ws')
const https = require('https')
const crypto = require('crypto')

const PORT = process.env.PORT || 8080
const AUTH_TIMEOUT_MS = 15_000

// name.toLowerCase() -> { ws, displayName }
const clients = new Map()

// HTTP server handles health checks; the WS server rides the same port via upgrade
const httpServer = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'text/plain' })
    res.end(`ok — ${clients.size} online`)
  } else {
    res.writeHead(404)
    res.end()
  }
})

const wss = new WebSocket.Server({ server: httpServer })

function broadcast(payload, excludeWs = null) {
  const text = JSON.stringify(payload)
  for (const { ws, displayName } of clients.values()) {
    if (ws !== excludeWs && ws.readyState === WebSocket.OPEN) {
      ws.send(text)
    }
  }
}

function mojangVerify(username, serverId) {
  return new Promise((resolve, reject) => {
    const url = `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${encodeURIComponent(username)}&serverId=${encodeURIComponent(serverId)}`
    https.get(url, res => {
      if (res.statusCode === 204 || res.statusCode === 403) {
        return reject(new Error(`Mojang rejected auth (${res.statusCode})`))
      }
      if (res.statusCode !== 200) {
        return reject(new Error(`Unexpected status ${res.statusCode}`))
      }
      let body = ''
      res.on('data', chunk => { body += chunk })
      res.on('end', () => {
        try {
          const profile = JSON.parse(body)
          if (!profile.name) return reject(new Error('No name in profile'))
          resolve(profile.name)
        } catch (e) {
          reject(e)
        }
      })
    }).on('error', reject)
  })
}

wss.on('connection', ws => {
  let key = null // lowercase verified name, set after auth

  const serverId = crypto.randomBytes(20).toString('hex')
  ws.send(JSON.stringify({ action: 'auth_request', serverId }))

  const authTimer = setTimeout(() => {
    if (!key) ws.close(4001, 'Auth timeout')
  }, AUTH_TIMEOUT_MS)

  ws.on('message', async raw => {
    let msg
    try { msg = JSON.parse(raw.toString()) } catch { return }

    if (!key) {
      if (msg.action !== 'auth_response') return
      const { name } = msg
      if (typeof name !== 'string' || !name) return

      try {
        const verifiedName = await mojangVerify(name, serverId)
        clearTimeout(authTimer)
        key = verifiedName.toLowerCase()

        // Kick existing connection with same name
        const existing = clients.get(key)
        if (existing) existing.ws.close(4002, 'Replaced by newer connection')

        clients.set(key, { ws, displayName: verifiedName })

        // Tell the new client who else is here
        ws.send(JSON.stringify({ action: 'sync', names: [...clients.values()].map(c => c.displayName) }))

        // Tell everyone else
        broadcast({ action: 'add', name: verifiedName }, ws)

        console.log(`[+] ${verifiedName} connected — ${clients.size} online`)
      } catch (err) {
        console.warn(`Auth failed for "${name}": ${err.message}`)
        ws.close(4003, 'Mojang auth failed')
      }
      return
    }

    if (msg.action === 'share_item') {
      const { seq, data, text } = msg
      if (typeof data !== 'string') return
      const displayName = clients.get(key)?.displayName ?? key
      // Broadcast to everyone except sender (sender caches locally)
      broadcast({ action: 'item_data', sender: displayName, seq: (seq | 0), data, text: text ?? null }, ws)
    }
    // location_update: ignore for now, available for future use
  })

  ws.on('close', () => {
    clearTimeout(authTimer)
    if (key) {
      const entry = clients.get(key)
      if (entry?.ws === ws) {
        clients.delete(key)
        broadcast({ action: 'remove', name: entry.displayName })
        console.log(`[-] ${entry.displayName} disconnected — ${clients.size} online`)
      }
    }
  })

  ws.on('error', err => {
    console.error(`WS error (${key ?? 'unauthenticated'}): ${err.message}`)
  })
})

httpServer.listen(PORT, () => {
  console.log(`Telosmancy relay listening on :${PORT}`)
})
