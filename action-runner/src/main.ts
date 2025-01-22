import * as core from '@actions/core'
import * as exec from '@actions/exec'
import { ExecOutput } from '@actions/exec'
import * as path from 'path'

import * as fs from 'fs/promises'

import process from 'process'

import WebSocket, {OPEN} from 'ws'

let workspace: string;
let currentCommand: Promise<ExecOutput> | null = null;

export async function run() {
  let githubWorkspacePath = process.env['GITHUB_WORKSPACE']
  if (!githubWorkspacePath) {
    throw new Error('GITHUB_WORKSPACE not defined')
  }
  workspace = path.resolve(githubWorkspacePath)
  await setupWs(
      core.getInput("endpoint"),
      onMessage
  );
}

export async function onMessage(ws: WebSocket, msg: any) {
  const json = JSON.parse(msg)

  if (json.type == "details") {
    ws.send(JSON.stringify({
      repository: process.env['GITHUB_REPOSITORY'],
      id: parseInt(process.env['GITHUB_RUN_ID']!)
    }))
  } else if (json.type == "command") {
    const command = json.command

    console.error(`Executing "${command.join(' ')}"\n`)

    const cmdLine = command.shift()
    currentCommand = exec.getExecOutput(cmdLine, command, {
      cwd: workspace
    })
    .then(executed => {
      if (executed.exitCode != 0) {
        ws.send(JSON.stringify({
          stderr: executed.stderr
        }))
      } else {
        ws.send(JSON.stringify({
          stdout: executed.stdout
        }))
      }

      console.log(`\nCommand returned exit code ${executed.exitCode}`)
      return executed
    }).finally(() => {
      currentCommand = null
    })

  } else if (json.type == "write-file") {
    const pth = path.resolve(workspace, json.path)
    await fs.mkdir(path.dirname(pth), {
      recursive: true
    })
    await fs.writeFile(pth, json.content)
    console.log(`Written file to ${pth}`)
    ws.send("{}")
  } else if (json.type == "read-file") {
    const pth = path.resolve(workspace, json.path)
    try {
      const file = await fs.readFile(pth)
      ws.send(JSON.stringify({file: file.toString('base64')}))
    } catch (error) {
      ws.send(JSON.stringify({error: error}))
    }
    console.log(`Read file from ${pth}`)
  } else if (json.type == "log") {
    console.log(json.message)
    ws.send("{}")
  }
}

export async function setupWs(url: string, msg: (ws: WebSocket, message: any) => any): Promise<WebSocket> {
  const ws = new WebSocket(url)

  ws.on('message', data => {
    msg(ws, data)
  })

  ws.on('open', () => {
    console.error(`Connection opened... awaiting command`)
    heartbeat(ws)
  })
  ws.on('close', (code, reason) => {
    console.error(`Connection closed with code ${code}: ${reason.toString()}`)
  })

  return ws
}

function heartbeat(ws: WebSocket) {
  if (ws.readyState == OPEN) {
    ws.ping()
    setTimeout(() => heartbeat(ws), 10 * 1000)
  }
}

export function getRunURL(): string {
  return `${process.env['GITHUB_SERVER_URL']}/${process.env['GITHUB_REPOSITORY']}/actions/runs/${process.env['GITHUB_RUN_ID']}`
}
