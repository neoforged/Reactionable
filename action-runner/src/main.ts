import * as core from '@actions/core'
import * as exec from '@actions/exec'
import * as path from 'path'

import * as fs from 'fs/promises'

import WebSocket from 'ws'

let workspace: string;

export async function run() {
  let githubWorkspacePath = process.env['GITHUB_WORKSPACE']
  if (!githubWorkspacePath) {
    throw new Error('GITHUB_WORKSPACE not defined')
  }
  workspace = path.resolve(githubWorkspacePath)
  const ws = await setupWs(
      core.getInput("endpoint"),
      onMessage
  );
}

export async function onMessage(ws: WebSocket, msg: any) {
  const json = JSON.parse(msg)

  if (json.type == "command") {
    const command = json.command

    console.log(`Executing "${command.join(' ')}"`)

    const cmdLine = command.shift()
    const executed = await exec.getExecOutput(cmdLine, command, {
      cwd: workspace
    })

    ws.send(executed.stdout)

    console.log(`Command returned exit code ${executed.exitCode}`)
  } else if (json.type == "write-file") {
    const pth = path.resolve(workspace, json.path)
    await fs.writeFile(pth, json.content)
    console.log(`Written file to ${pth}`)
  }
}

export async function setupWs(url: string, msg: (ws: WebSocket, message: any) => any): Promise<WebSocket> {
  const ws = new WebSocket(url)

  ws.on('message', data => {
    msg(ws, data)
  })

  ws.on('open', () => {
    console.error(`Connection opened... awaiting command`)
  })
  ws.on('close', () => {
    console.error(`Connection closed.`)
  })

  return ws
}
