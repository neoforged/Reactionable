import * as core from '@actions/core'
import * as exec from '@actions/exec'
import { ExecOutput } from '@actions/exec'
import * as cache from '@actions/cache'
import * as path from 'path'
import * as semver from 'semver'

import * as fs from 'fs/promises'
import * as streamfs from 'node:fs'
import * as os from 'os'

import * as uuid from 'uuid'

import process from 'process'

import WebSocket, {OPEN} from 'ws'
import nodeEval from "eval";

let workspace: string;
let currentCommand: Promise<ExecOutput> | null = null;

const backgroundCommands: Map<string, Promise<number>> = new Map<string, Promise<number>>()

export async function run() {
  const githubWorkspacePath = process.env['GITHUB_WORKSPACE']
  if (!githubWorkspacePath) {
    throw new Error('GITHUB_WORKSPACE not defined')
  }
  workspace = path.resolve(githubWorkspacePath)

  let endpoint = core.getInput("endpoint")
  if (!endpoint) {
    const json = JSON.parse(await fs.readFile(process.env['GITHUB_EVENT_PATH']!!, {encoding: 'utf8'}))
    endpoint = json.inputs[core.getInput('endpoint-input')]
  }

  core.setSecret(endpoint)
  await setupWs(endpoint, onMessage)
}

export async function onMessage(ws: WebSocket, msg: any) {
  const json = JSON.parse(msg)

  if (json.type == "details") {
    ws.send(JSON.stringify({
      repository: process.env['GITHUB_REPOSITORY'],
      id: parseInt(process.env['GITHUB_RUN_ID']!),
      token: process.env['GITHUB_TOKEN'],
      userHome: await determineUserHome()
    }))
  } else if (json.type == "command") {
    const command: string[] = json.command

    core.startGroup(`Executing "${command.join(' ')}"`)

    let cmdLine = command.shift()!
    const optional = cmdLine.startsWith("?")
    if (optional) {
      cmdLine = cmdLine.substring(1)
    }

    currentCommand = exec.getExecOutput(cmdLine, command, {
      cwd: workspace,
      ignoreReturnCode: true
    })
    .then(executed => {
      core.endGroup()

      let log = core.info
      if (executed.exitCode != 0) {
        log = core.error
      }
      log(`Command returned exit code ${executed.exitCode}`)

      if (!optional && executed.exitCode != 0) {
        ws.send(JSON.stringify({
          stderr: executed.stderr
        }))
      } else {
        ws.send(JSON.stringify({
          stdout: executed.stdout
        }))
      }
      return executed
    }).finally(() => {
      currentCommand = null
    })

  } else if (json.type == "background-command") {
    const id: string = json.id
    const command: string[] = json.command

    core.info(`Executing "${command.join(' ')}" in the background`)

    const cmdLine = command.shift()!

    await fs.mkdir('/bg-process')
    const file = `/bg-process/${id}-${uuid.v4()}`
    const stream = streamfs.createWriteStream(file)

    const promise = exec.exec(cmdLine, command, {
      cwd: workspace,
      ignoreReturnCode: true,
      outStream: stream,
      errStream: stream
    }).finally(() => backgroundCommands.delete(id))
    backgroundCommands.set(id, promise)

    ws.send(JSON.stringify({output: file}))
  } else if (json.type == "set-env") {
    const name: string = json.name
    const value: string = json.value
    core.exportVariable(name, value)
    ws.send("{}")
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
    const type: string = json.logType
    const message: string = json.message

    if (type == 'error') {
      core.error(message)
    } else if (type == 'warning' || !type) {
      core.warning(message)
    } else if (type == 'info') {
      core.info(message)
    } else if (type == 'debug') {
      core.debug(message)
    }

    ws.send("{}")
  } else if (json.type == 'eval') {
    const expression = json.expression
    console.log(`Evaluating '${expression}'`)

    let toEval = `exports.result = ${expression}`
    if (expression.includes('return ')) {
      toEval = `exports.result = (()=>{${expression})()`
    }

    let vars: Record<string, unknown> = {
      'semver': semver
    }
    Object.keys(json.variables).forEach(key => vars[key] = json.variables[key])

    ws.send(JSON.stringify({result: (nodeEval(toEval, 'expreval', vars, true) as any).result}))
  } else if (json.type == 'save-cache') {
    const ch = await cache.saveCache(json.paths, json.key).catch(_ => undefined)
    if (ch == undefined) {
      console.error(`Cache could not be saved`)
      ws.send(JSON.stringify({id: -1}))
    } else {
      console.log(`Saved cache from ` + json.paths + ` as ` + json.key)
      ws.send(JSON.stringify({id: ch}))
    }
  } else if (json.type == 'restore-cache') {
    const ch = await cache.restoreCache(json.paths, json.key)
    if (ch) console.log(`Restored cache to ` + json.paths)
    else console.log(`No cache hit`)
    ws.send("{}")
  } else if (json.type == 'mask') {
    core.setSecret(json.value)
    ws.send("{}")
  } else if (json.type == 'group') {
    const title = json.title
    if (title) {
      core.startGroup(title)
    } else {
      core.endGroup()
    }
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

async function determineUserHome(): Promise<string> {
  const output = await exec.getExecOutput('java', ['-XshowSettings:properties', '-version'], {silent: true})
  const regex = /user\.home = (\S*)/i
  const found = output.stderr.match(regex)
  if (found == null || found.length <= 1) {
    core.info('Could not determine user.home from java -version output. Using os.homedir().')
    return os.homedir()
  }
  const userHome = found[1]
  core.debug(`Determined user.home from java -version output: '${userHome}'`)
  return userHome
}
