export type AuthMode = 'api-key' | 'bearer'

export type MimoConnection = {
  baseUrl: string
  apiKey: string
  authMode: AuthMode
}

export type ProbeStatus = 'testing' | 'passed' | 'reachable' | 'failed'

export type ProbeResult = {
  model: string
  capability: string
  status: ProbeStatus
  latency?: number
  detail: string
}

const CHAT_PATH = '/chat/completions'
const ONE_PIXEL_PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='

function normalizeBaseUrl(value: string) {
  return value.trim().replace(/\/+$/, '')
}

function headers(config: MimoConnection): Record<string, string> {
  const result: Record<string, string> = { 'Content-Type': 'application/json' }
  if (config.authMode === 'bearer') result.Authorization = `Bearer ${config.apiKey}`
  else result['api-key'] = config.apiKey
  return result
}

async function apiFetch(config: MimoConnection, path: string, init?: RequestInit) {
  const response = await fetch(`${normalizeBaseUrl(config.baseUrl)}${path}`, {
    ...init,
    headers: headers(config),
  })
  const text = await response.text()
  let data: unknown = text
  try { data = text ? JSON.parse(text) : {} } catch { /* keep diagnostic text */ }
  if (!response.ok) {
    const message = typeof data === 'object' && data && 'error' in data
      ? JSON.stringify((data as { error: unknown }).error)
      : text.slice(0, 240)
    throw new Error(`${response.status} ${message || response.statusText}`)
  }
  return data
}

export async function loadModels(config: MimoConnection): Promise<string[]> {
  const data = await apiFetch(config, '/models', { method: 'GET' }) as
    | { data?: Array<{ id?: string } | string>; models?: Array<{ id?: string } | string> }
    | Array<{ id?: string } | string>
  const list = Array.isArray(data) ? data : (data.data ?? data.models ?? [])
  return list
    .map((item) => typeof item === 'string' ? item : item.id)
    .filter((id): id is string => Boolean(id))
    .sort((a, b) => a.localeCompare(b))
}

function testWavDataUrl() {
  const sampleRate = 8000
  const seconds = 0.35
  const samples = Math.floor(sampleRate * seconds)
  const buffer = new ArrayBuffer(44 + samples * 2)
  const view = new DataView(buffer)
  const write = (offset: number, value: string) => [...value].forEach((char, index) => view.setUint8(offset + index, char.charCodeAt(0)))
  write(0, 'RIFF'); view.setUint32(4, 36 + samples * 2, true); write(8, 'WAVE')
  write(12, 'fmt '); view.setUint32(16, 16, true); view.setUint16(20, 1, true)
  view.setUint16(22, 1, true); view.setUint32(24, sampleRate, true); view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true); view.setUint16(34, 16, true); write(36, 'data'); view.setUint32(40, samples * 2, true)
  for (let i = 0; i < samples; i++) {
    const envelope = Math.sin(Math.PI * i / samples)
    view.setInt16(44 + i * 2, Math.sin(2 * Math.PI * 220 * i / sampleRate) * 5000 * envelope, true)
  }
  const bytes = new Uint8Array(buffer)
  let binary = ''
  bytes.forEach((byte) => { binary += String.fromCharCode(byte) })
  return `data:audio/wav;base64,${btoa(binary)}`
}

function capabilityFor(model: string) {
  const id = model.toLowerCase()
  if (id.includes('voiceclone')) return '声音克隆'
  if (id.includes('voicedesign')) return '声音设计'
  if (id.includes('tts')) return '语音合成'
  if (id.includes('asr') || id.includes('whisper')) return '语音识别'
  if (id.includes('omni') || id === 'mimo-v2.5' || id.includes('vision')) return '多模态理解'
  if (id.includes('pro') || id.includes('reason')) return '深度推理'
  return '文本对话'
}

function probeBody(model: string) {
  const capability = capabilityFor(model)
  if (capability === '声音克隆') return {
    model,
    messages: [{ role: 'user', content: '自然、清晰地说话。' }, { role: 'assistant', content: '你好，这是声音克隆能力检测。' }],
    audio: { format: 'wav', voice: testWavDataUrl() },
  }
  if (capability === '声音设计') return {
    model,
    messages: [{ role: 'user', content: '温暖、沉稳、自然的青年中文声音。' }, { role: 'assistant', content: '你好，这是声音设计能力检测。' }],
    audio: { format: 'wav', optimize_text_preview: true },
  }
  if (capability === '语音合成') return {
    model,
    messages: [{ role: 'user', content: '用轻快自然的语气。' }, { role: 'assistant', content: '你好，这是语音合成能力检测。' }],
    audio: { format: 'wav', voice: 'Chloe' },
  }
  if (capability === '语音识别') return {
    model,
    messages: [{ role: 'user', content: [{ type: 'input_audio', input_audio: { data: testWavDataUrl() } }] }],
    asr_options: { language: 'auto' },
  }
  if (capability === '多模态理解') return {
    model,
    messages: [{ role: 'user', content: [{ type: 'text', text: '只回答图像是否成功读取。' }, { type: 'image_url', image_url: { url: ONE_PIXEL_PNG } }] }],
    max_tokens: 12,
  }
  return {
    model,
    messages: [{ role: 'user', content: capability === '深度推理' ? '计算 17×19，只回答结果。' : '只回答 OK。' }],
    max_tokens: 16,
  }
}

export async function probeModel(config: MimoConnection, model: string): Promise<ProbeResult> {
  const capability = capabilityFor(model)
  const started = performance.now()
  try {
    const data = await apiFetch(config, CHAT_PATH, { method: 'POST', body: JSON.stringify(probeBody(model)) }) as Record<string, unknown>
    const latency = Math.round(performance.now() - started)
    const hasOutput = Boolean(data.choices || data.output || data.id)
    return { model, capability, status: hasOutput ? 'passed' : 'reachable', latency, detail: hasOutput ? '功能响应正常' : '接口可达，响应结构非标准' }
  } catch (error) {
    const message = error instanceof Error ? error.message : '未知错误'
    return { model, capability, status: 'failed', latency: Math.round(performance.now() - started), detail: message }
  }
}

export async function chatCompletion(config: MimoConnection, model: string, prompt: string, images: string[] = []): Promise<string> {
  const content = images.length
    ? [{ type: 'text', text: prompt }, ...images.map((url) => ({ type: 'image_url', image_url: { url } }))]
    : prompt
  const data = await apiFetch(config, CHAT_PATH, {
    method: 'POST',
    body: JSON.stringify({ model, messages: [{ role: 'user', content }], stream: false }),
  }) as { choices?: Array<{ message?: { content?: string } }>; output_text?: string }
  const responseContent = data.choices?.[0]?.message?.content ?? data.output_text
  if (!responseContent) throw new Error('模型没有返回文本内容')
  return responseContent
}

export async function speechRecognition(config: MimoConnection, audioDataUrl: string): Promise<string> {
  const data = await apiFetch(config, CHAT_PATH, {
    method: 'POST',
    body: JSON.stringify({
      model: 'mimo-v2.5-asr',
      messages: [{ role: 'user', content: [{ type: 'input_audio', input_audio: { data: audioDataUrl } }] }],
      asr_options: { language: 'auto' },
    }),
  }) as { choices?: Array<{ message?: { content?: string } }> }
  const text = data.choices?.[0]?.message?.content
  if (!text) throw new Error('没有识别到语音内容')
  return text
}

export async function synthesizeSpeech(
  config: MimoConnection,
  model: 'mimo-v2.5-tts' | 'mimo-v2.5-tts-voiceclone',
  text: string,
  voiceName = 'Chloe',
  voiceSample?: string,
  voicePrompt = '自然、清晰、像面对面聊天一样回应。',
): Promise<string> {
  const audio = model === 'mimo-v2.5-tts-voiceclone'
    ? { format: 'wav', voice: voiceSample }
    : { format: 'wav', voice: voiceName }
  if (model.includes('voiceclone') && !voiceSample) throw new Error('当前角色还没有录入克隆音色')
  const data = await apiFetch(config, CHAT_PATH, {
    method: 'POST',
    body: JSON.stringify({
      model,
      messages: [{ role: 'user', content: voicePrompt }, { role: 'assistant', content: text }],
      audio,
    }),
  }) as { choices?: Array<{ message?: { audio?: { data?: string } } }> }
  const audioBase64 = data.choices?.[0]?.message?.audio?.data
  if (!audioBase64) throw new Error('语音合成没有返回音频')
  return `data:audio/wav;base64,${audioBase64}`
}

export function saveConnection(config: MimoConnection) {
  localStorage.setItem('mimo-connection', JSON.stringify(config))
}

export function readConnection(): MimoConnection {
  try {
    const saved = JSON.parse(localStorage.getItem('mimo-connection') ?? '{}')
    return {
      baseUrl: saved.baseUrl || 'https://api.xiaomimimo.com/v1',
      apiKey: saved.apiKey || '',
      authMode: saved.authMode === 'bearer' ? 'bearer' : 'api-key',
    }
  } catch {
    return { baseUrl: 'https://api.xiaomimimo.com/v1', apiKey: '', authMode: 'api-key' }
  }
}
