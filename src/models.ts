export type ModelId =
  | 'mimo-v2.5-pro'
  | 'mimo-v2.5'
  | 'mimo-v2.5-asr'
  | 'mimo-v2.5-tts'
  | 'mimo-v2.5-tts-voicedesign'
  | 'mimo-v2.5-tts-voiceclone'

export type ModelInfo = {
  id: ModelId
  shortName: string
  role: string
  detail: string
  tone: 'orange' | 'blue' | 'green' | 'violet'
}

export const MODELS: ModelInfo[] = [
  { id: 'mimo-v2.5-pro', shortName: 'Pro', role: '深度思考', detail: '复杂推理、长任务与 Agent 执行', tone: 'violet' },
  { id: 'mimo-v2.5', shortName: 'Omni', role: '全模态理解', detail: '理解文字、图片、音频与视频', tone: 'blue' },
  { id: 'mimo-v2.5-asr', shortName: 'ASR', role: '实时转写', detail: '中英、方言与复杂声学环境', tone: 'green' },
  { id: 'mimo-v2.5-tts', shortName: 'TTS', role: '自然语音', detail: '内置高品质音色与细粒度控制', tone: 'orange' },
  { id: 'mimo-v2.5-tts-voicedesign', shortName: 'Design', role: '声音设计', detail: '用一句描述创造全新声音', tone: 'violet' },
  { id: 'mimo-v2.5-tts-voiceclone', shortName: 'Clone', role: '声音克隆', detail: '用少量样本复刻目标音色', tone: 'green' },
]

export const modelById = (id: ModelId) => MODELS.find((model) => model.id === id)!
