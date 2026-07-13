import { useMemo, useRef, useState } from 'react'
import {
  ArrowLeft, Brain, Camera, CaretDown, CaretRight, ChatCircleDots, Check, Copy,
  Database, DotsThree, Eye, EyeSlash, File, Gear, Image, List, Microphone,
  PaperPlaneRight, PencilSimple, PlugsConnected, Plus, SlidersHorizontal,
  Sparkle, SpeakerHigh, Stop, Sun, Moon, Monitor, Trash, UserCircle, UsersThree, Waveform, X,
} from '@phosphor-icons/react'
import { beginWavRecording, finishWavRecording, RecordingHandle } from './audio'
import {
  chatCompletion, loadModels, MimoConnection, probeModel, ProbeResult,
  readConnection, saveConnection, speechRecognition, synthesizeSpeech,
} from './mimoClient'

type ModelId = 'mimo-v2.5' | 'mimo-v2.5-pro'
type Screen = 'chat' | 'settings' | 'connection' | 'roles' | 'memory'
type VoiceModel = 'mimo-v2.5-tts' | 'mimo-v2.5-tts-voiceclone'
type Role = {
  id: string
  name: string
  description: string
  prompt: string
  capabilities: string
  voiceModel: VoiceModel
  voiceName: string
  voicePrompt?: string
  voiceSample?: string
  color: string
}
type Attachment = { id: string; name: string; type: 'image' | 'file'; url?: string }
type Message = { id: string; role: 'user' | 'assistant'; text: string; model?: ModelId; attachments?: Attachment[] }
type Conversation = { id: string; title: string; roleId: string; updated: string; messages: Message[] }

const DEFAULT_ROLES: Role[] = [
  { id: 'mimo', name: 'MiMo', description: '日常陪伴与多模态助手', prompt: '你是 MiMo，一个温暖、直接、可靠的私人助手。回答要自然简洁，必要时主动梳理重点。', capabilities: '日常对话、看图、整理信息', voiceModel: 'mimo-v2.5-tts', voiceName: 'Chloe', voicePrompt: '温暖、清晰、自然的中文声音，语速适中，像一位可靠的朋友在面对面聊天。', color: '#f06c3b' },
  { id: 'study', name: '知夏', description: '耐心的学习搭子', prompt: '你是一位耐心的学习搭子知夏。用启发式提问帮助用户理解，不要直接堆砌答案。', capabilities: '学习辅导、知识讲解、复盘', voiceModel: 'mimo-v2.5-tts', voiceName: 'Chloe', voicePrompt: '温柔、耐心、清楚的女声，语气有鼓励感，重点处稍微放慢。', color: '#5f7f73' },
  { id: 'editor', name: '木棉', description: '文字与灵感编辑', prompt: '你是文字编辑木棉。擅长提炼表达、改写文案和激发创意，保持克制、有品位。', capabilities: '写作、改写、创意构思', voiceModel: 'mimo-v2.5-tts', voiceName: 'Chloe', voicePrompt: '干净、克制、略带磁性的中性声音，句尾收得利落，像一位编辑在读稿。', color: '#8d6c58' },
]

const STARTER_CONVERSATIONS: Conversation[] = [
  { id: 'current', title: '新对话', roleId: 'mimo', updated: '刚刚', messages: [] },
  { id: 'weekend', title: '周末旅行计划', roleId: 'mimo', updated: '昨天', messages: [{ id: 'w1', role: 'assistant', text: '杭州两日路线已经整理好了。', model: 'mimo-v2.5' }] },
  { id: 'meeting', title: '会议录音整理', roleId: 'study', updated: '周五', messages: [{ id: 'm1', role: 'assistant', text: '录音重点已经整理为四项待办。', model: 'mimo-v2.5' }] },
]

const RECENT_PHOTOS = [
  '/recent-gallery.jpg#recent-1',
  '/recent-gallery.jpg#recent-2',
  '/recent-gallery.jpg#recent-3',
  '/recent-gallery.jpg#recent-4',
  '/recent-gallery.jpg#recent-5',
]

function readRoles(): Role[] {
  try { return JSON.parse(localStorage.getItem('mimo-roles') ?? 'null') ?? DEFAULT_ROLES } catch { return DEFAULT_ROLES }
}

export default function App() {
  const [screen, setScreen] = useState<Screen>('chat')
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [attachmentOpen, setAttachmentOpen] = useState(false)
  const [modelOpen, setModelOpen] = useState(false)
  const [theme, setTheme] = useState<'light' | 'dark' | 'system'>(() => (localStorage.getItem('mimo-theme') as 'light' | 'dark' | 'system') || 'system')
  const [roles, setRoles] = useState<Role[]>(readRoles)
  const [defaultRoleId, setDefaultRoleId] = useState(() => localStorage.getItem('mimo-default-role') ?? 'mimo')
  const [conversations, setConversations] = useState<Conversation[]>(STARTER_CONVERSATIONS)
  const [conversationId, setConversationId] = useState('current')
  const [model, setModel] = useState<ModelId>('mimo-v2.5')
  const [input, setInput] = useState('')
  const [attachments, setAttachments] = useState<Attachment[]>([])
  const [thinking, setThinking] = useState(false)
  const [recording, setRecording] = useState(false)
  const [voiceStatus, setVoiceStatus] = useState('')
  const [toast, setToast] = useState('')
  const [playingMessageId, setPlayingMessageId] = useState<string | null>(null)
  const recorderRef = useRef<RecordingHandle | null>(null)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const playbackTokenRef = useRef(0)
  const cameraRef = useRef<HTMLInputElement>(null)
  const albumRef = useRef<HTMLInputElement>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  const conversation = conversations.find((item) => item.id === conversationId) ?? conversations[0]
  const activeRole = roles.find((item) => item.id === conversation.roleId) ?? roles[0]

  const updateConversation = (updater: (item: Conversation) => Conversation) => {
    setConversations((current) => current.map((item) => item.id === conversationId ? updater(item) : item))
  }

  const startNewConversation = () => {
    const id = `chat-${Date.now()}`
    setConversations((current) => [{ id, title: '新对话', roleId: defaultRoleId, updated: '刚刚', messages: [] }, ...current])
    setConversationId(id)
    setModel('mimo-v2.5')
    setAttachments([])
    setDrawerOpen(false)
    setScreen('chat')
  }

  const sendPrompt = async (text: string, fromVoice = false) => {
    const clean = text.trim()
    if ((!clean && !attachments.length) || thinking) return
    const userMessage: Message = { id: `user-${Date.now()}`, role: 'user', text: clean || '请分析这些附件', attachments }
    const outgoingAttachments = attachments
    updateConversation((item) => ({ ...item, title: item.messages.length ? item.title : clean.slice(0, 14) || '图片对话', updated: '刚刚', messages: [...item.messages, userMessage] }))
    setInput('')
    setAttachments([])
    setThinking(true)
    if (fromVoice) setVoiceStatus(`${model === 'mimo-v2.5-pro' ? 'Pro' : 'v2.5'} 正在回答`)
    try {
      const config = readConnection()
      const routedModel: ModelId = outgoingAttachments.some((item) => item.type === 'image') ? 'mimo-v2.5' : model
      const imageUrls = outgoingAttachments.filter((item) => item.type === 'image' && item.url).map((item) => item.url!)
      const reply = config.apiKey
        ? await chatCompletion(config, routedModel, `${activeRole.prompt}\n\n用户：${clean || '请分析附件'}`, imageUrls)
        : demoReply(clean, activeRole, routedModel, Boolean(imageUrls.length))
      const assistant: Message = { id: `assistant-${Date.now()}`, role: 'assistant', text: reply, model: routedModel }
      updateConversation((item) => ({ ...item, messages: [...item.messages, assistant] }))
      if (fromVoice) {
        setVoiceStatus('正在生成角色声音')
        await playRoleVoice(reply, activeRole)
      }
    } catch (error) {
      showToast(error instanceof Error ? error.message : '请求失败')
    } finally {
      setThinking(false)
      setVoiceStatus('')
    }
  }

  const stopPlayback = () => {
    playbackTokenRef.current += 1
    audioRef.current?.pause()
    audioRef.current = null
    if ('speechSynthesis' in window) speechSynthesis.cancel()
    setPlayingMessageId(null)
  }

  const playRoleVoice = async (text: string, role = activeRole, messageId?: string) => {
    const playbackId = messageId ?? 'voice-reply'
    if (playingMessageId === playbackId) {
      stopPlayback()
      return
    }
    stopPlayback()
    const token = playbackTokenRef.current
    setPlayingMessageId(playbackId)
    const finish = () => {
      if (playbackTokenRef.current === token) setPlayingMessageId(null)
    }
    try {
      const config = readConnection()
      if (config.apiKey) {
      const url = await synthesizeSpeech(config, role.voiceModel, text, role.voiceName, role.voiceSample, role.voicePrompt)
        if (playbackTokenRef.current !== token) return
        const audio = new Audio(url)
        audioRef.current = audio
        audio.onended = finish
        audio.onerror = finish
        await audio.play()
        return
      }
      if ('speechSynthesis' in window) {
        const utterance = new SpeechSynthesisUtterance(text)
        utterance.lang = 'zh-CN'
        utterance.onend = finish
        utterance.onerror = finish
        speechSynthesis.speak(utterance)
      } else finish()
      showToast('演示音色播放中')
    } catch (error) {
      finish()
      showToast(error instanceof Error ? error.message : '朗读失败')
    }
  }

  const toggleRecording = async () => {
    if (!recording) {
      try {
        recorderRef.current = await beginWavRecording()
        setRecording(true)
        setVoiceStatus('正在听，请说话')
      } catch { showToast('需要麦克风权限才能语音聊天') }
      return
    }
    setRecording(false)
    setVoiceStatus('mimo-v2.5-asr 正在识别')
    try {
      const wav = await finishWavRecording(recorderRef.current!)
      recorderRef.current = null
      const config = readConnection()
      const transcript = config.apiKey ? await speechRecognition(config, wav) : '帮我安排一下今天最重要的三件事'
      setInput(transcript)
      setVoiceStatus('识别完成，正在发送')
      await sendPrompt(transcript, true)
    } catch (error) {
      setVoiceStatus('')
      showToast(error instanceof Error ? error.message : '语音识别失败')
    }
  }

  const showToast = (message: string) => {
    setToast(message.slice(0, 80))
    window.setTimeout(() => setToast(''), 2400)
  }

  const addFiles = async (files: FileList | null, type: 'image' | 'file') => {
    if (!files?.length) return
    const next = await Promise.all([...files].slice(0, 5).map(async (file) => ({
      id: `${file.name}-${Date.now()}`,
      name: file.name,
      type,
      url: type === 'image' ? await fileToDataUrl(file) : undefined,
    } as Attachment)))
    setAttachments((current) => [...current, ...next])
    setAttachmentOpen(false)
  }

  const saveRoles = (next: Role[]) => {
    setRoles(next)
    localStorage.setItem('mimo-roles', JSON.stringify(next))
  }

  const resolvedTheme = theme === 'dark' || (theme === 'system' && window.matchMedia?.('(prefers-color-scheme: dark)').matches) ? 'dark' : 'light'

  return <main className="app-stage">
    <section className={`phone-shell theme-${resolvedTheme}`} aria-label="MiMo 私人聊天助手">
      <StatusBar />
      {screen === 'chat' && <ChatScreen conversation={conversation} role={activeRole} model={model} input={input} attachments={attachments} thinking={thinking} recording={recording} voiceStatus={voiceStatus} playingMessageId={playingMessageId} onMenu={() => setDrawerOpen(true)} onNew={startNewConversation} onModel={() => setModelOpen(true)} onInput={setInput} onSend={() => sendPrompt(input)} onVoice={toggleRecording} onAttachment={() => setAttachmentOpen(true)} onRemoveAttachment={(id) => setAttachments((current) => current.filter((item) => item.id !== id))} onSpeak={(text, id) => playRoleVoice(text, activeRole, id)} />}
      {screen === 'settings' && <SettingsScreen theme={theme} onThemeChange={(value) => { setTheme(value); localStorage.setItem('mimo-theme', value) }} onBack={() => setScreen('chat')} onOpen={setScreen} roleCount={roles.length} />}
      {screen === 'connection' && <ConnectionScreen onBack={() => setScreen('settings')} />}
      {screen === 'roles' && <RolesScreen roles={roles} defaultRoleId={defaultRoleId} onBack={() => setScreen('settings')} onSave={saveRoles} onDefault={(id) => { setDefaultRoleId(id); localStorage.setItem('mimo-default-role', id) }} />}
      {screen === 'memory' && <MemoryScreen onBack={() => setScreen('settings')} />}
      {drawerOpen && <HistoryDrawer conversations={conversations} roles={roles} currentId={conversationId} onClose={() => setDrawerOpen(false)} onNew={startNewConversation} onSelect={(id) => { setConversationId(id); setDrawerOpen(false) }} onSettings={() => { setDrawerOpen(false); setScreen('settings') }} />}
      {attachmentOpen && <AttachmentPanel onClose={() => setAttachmentOpen(false)} onRecent={(url, index) => { setAttachments((current) => [...current, { id: `recent-${Date.now()}`, name: `最近图片 ${index + 1}`, type: 'image', url }]); setAttachmentOpen(false) }} onCamera={() => cameraRef.current?.click()} onAlbum={() => albumRef.current?.click()} onFile={() => fileRef.current?.click()} />}
      {modelOpen && <ModelPanel model={model} onClose={() => setModelOpen(false)} onSelect={(id) => { setModel(id); setModelOpen(false) }} />}
      <input ref={cameraRef} className="hidden-input" type="file" accept="image/*" capture="environment" onChange={(event) => addFiles(event.target.files, 'image')} />
      <input ref={albumRef} className="hidden-input" type="file" accept="image/*" multiple onChange={(event) => addFiles(event.target.files, 'image')} />
      <input ref={fileRef} className="hidden-input" type="file" multiple onChange={(event) => addFiles(event.target.files, 'file')} />
      {toast && <div className="toast">{toast}</div>}
      <div className="home-indicator" />
    </section>
  </main>
}

function StatusBar() { return <div className="statusbar"><span>9:41</span><span>● ◒ ︱</span></div> }

function ChatScreen(props: {
  conversation: Conversation; role: Role; model: ModelId; input: string; attachments: Attachment[]; thinking: boolean; recording: boolean; voiceStatus: string; playingMessageId: string | null
  onMenu: () => void; onNew: () => void; onModel: () => void; onInput: (value: string) => void; onSend: () => void; onVoice: () => void; onAttachment: () => void; onRemoveAttachment: (id: string) => void; onSpeak: (text: string, id: string) => void
}) {
  const { conversation, role, model, input, attachments, thinking, recording, voiceStatus } = props
  return <section className="chat-view">
    <header className="chat-topbar">
      <button className="round-button" onClick={props.onMenu} aria-label="打开历史记录"><List /></button>
      <button className="role-title" onClick={props.onModel}><span className="role-avatar" style={{ background: role.color }}>{role.name.slice(0, 1)}</span><span><strong>{role.name}</strong><small>{model === 'mimo-v2.5' ? 'MiMo v2.5 · 多模态' : 'MiMo v2.5 Pro · 深度推理'}</small></span><CaretDown /></button>
      <button className="round-button" onClick={props.onNew} aria-label="新建对话"><ChatCircleDots /></button>
    </header>
    <div className="chat-messages">
      {!conversation.messages.length && <div className="role-welcome"><span className="welcome-avatar" style={{ background: role.color }}><Sparkle weight="fill" /></span><h1>和 {role.name} 聊聊</h1><p>{role.description}</p><div className="ability-line"><span>{role.capabilities}</span><span>{role.voiceModel.includes('voiceclone') ? '克隆音色' : '自然语音'}</span></div></div>}
      {conversation.messages.map((message) => <article key={message.id} className={`chat-message ${message.role}`}>
        {message.attachments?.length ? <div className="message-attachments">{message.attachments.map((item) => item.url ? <img key={item.id} src={item.url} alt={item.name} /> : <span key={item.id}><File />{item.name}</span>)}</div> : null}
        <div className="message-bubble">{message.text}</div>
        {message.role === 'assistant' && <div className="message-tools"><button onClick={() => props.onSpeak(message.text, message.id)} aria-label={props.playingMessageId === message.id ? '停止朗读' : '朗读'}>{props.playingMessageId === message.id ? <Stop weight="fill" /> : <SpeakerHigh />}</button><button onClick={() => navigator.clipboard?.writeText(message.text)} aria-label="复制"><Copy /></button></div>}
      </article>)}
      {thinking && <div className="thinking-row"><i /><i /><i /><span>{model === 'mimo-v2.5-pro' ? 'Pro 正在思考' : 'MiMo 正在回应'}</span></div>}
    </div>
    <div className="composer-area">
      {voiceStatus && <div className="voice-status"><Waveform />{voiceStatus}</div>}
      {attachments.length > 0 && <div className="attachment-chips">{attachments.map((item) => <span key={item.id}>{item.url ? <img src={item.url} alt={item.name} /> : <File />}<em>{item.name}</em><button onClick={() => props.onRemoveAttachment(item.id)}><X /></button></span>)}</div>}
      <div className={`composer ${recording ? 'recording' : ''}`}>
        {recording ? <><button className="stop-record" onClick={props.onVoice}><Stop weight="fill" /></button><div className="record-wave">{Array.from({ length: 20 }).map((_, index) => <i key={index} />)}</div><span>再次点击发送</span></> : <><button onClick={props.onAttachment} aria-label="添加附件"><Plus /></button><textarea rows={1} value={input} onChange={(event) => props.onInput(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); props.onSend() } }} placeholder="发消息或按住说话" />{input.trim() || attachments.length ? <button className="send-button" onClick={props.onSend} aria-label="发送"><PaperPlaneRight weight="fill" /></button> : <button onClick={props.onVoice} aria-label="语音聊天"><Microphone /></button>}</>}
      </div>
      <div className="composer-hint">语音链路：ASR → {model === 'mimo-v2.5-pro' ? 'v2.5 Pro' : 'v2.5'} → {role.voiceModel.includes('voiceclone') ? '克隆音色' : 'TTS'}</div>
    </div>
  </section>
}

function HistoryDrawer({ conversations, roles, currentId, onClose, onNew, onSelect, onSettings }: { conversations: Conversation[]; roles: Role[]; currentId: string; onClose: () => void; onNew: () => void; onSelect: (id: string) => void; onSettings: () => void }) {
  return <div className="drawer-layer" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><aside className="history-drawer"><header><div className="mini-brand"><Sparkle weight="fill" /></div><strong>MiMo Chat</strong><button onClick={onClose}><X /></button></header><button className="drawer-new" onClick={onNew}><Plus />新对话</button><div className="history-section"><span>最近对话</span>{conversations.map((item) => { const role = roles.find((entry) => entry.id === item.roleId) ?? roles[0]; return <button key={item.id} className={item.id === currentId ? 'active' : ''} onClick={() => onSelect(item.id)}><i style={{ background: role.color }}>{role.name.slice(0, 1)}</i><span><strong>{item.title}</strong><small>{role.name} · {item.updated}</small></span></button> })}</div><footer><button onClick={onSettings}><Gear />设置<CaretRight /></button></footer></aside></div>
}

function AttachmentPanel({ onClose, onRecent, onCamera, onAlbum, onFile }: { onClose: () => void; onRecent: (url: string, index: number) => void; onCamera: () => void; onAlbum: () => void; onFile: () => void }) {
  return <div className="sheet-layer" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className="attachment-panel"><div className="grabber" /><header><div><h2>添加内容</h2><small>最近项目</small></div><button onClick={onClose}><X /></button></header><div className="recent-photos">{RECENT_PHOTOS.map((url, index) => <button key={url} onClick={() => onRecent(url, index)}><img src={url} alt={`最近图片 ${index + 1}`} /><span /></button>)}</div><div className="attachment-actions"><button onClick={onCamera}><span><Camera /></span><strong>拍照</strong></button><button onClick={onAlbum}><span><Image /></span><strong>相册</strong></button><button onClick={onFile}><span><File /></span><strong>文件</strong></button></div><p>图片将交给 mimo-v2.5 进行多模态理解</p></section></div>
}

function ModelPanel({ model, onClose, onSelect }: { model: ModelId; onClose: () => void; onSelect: (id: ModelId) => void }) {
  return <div className="sheet-layer" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className="model-panel"><div className="grabber" /><header><h2>选择对话模型</h2><button onClick={onClose}><X /></button></header><button className={model === 'mimo-v2.5' ? 'selected' : ''} onClick={() => onSelect('mimo-v2.5')}><span className="model-symbol"><Sparkle /></span><span><strong>MiMo v2.5</strong><small>默认 · 文字、图片、音频多模态理解</small></span>{model === 'mimo-v2.5' && <Check />}</button><button className={model === 'mimo-v2.5-pro' ? 'selected' : ''} onClick={() => onSelect('mimo-v2.5-pro')}><span className="model-symbol"><Brain /></span><span><strong>MiMo v2.5 Pro</strong><small>复杂推理与长任务；语音先经 ASR 转写</small></span>{model === 'mimo-v2.5-pro' && <Check />}</button></section></div>
}

function SettingsScreen({ onBack, onOpen, roleCount, theme, onThemeChange }: { onBack: () => void; onOpen: (screen: Screen) => void; roleCount: number; theme: 'light' | 'dark' | 'system'; onThemeChange: (theme: 'light' | 'dark' | 'system') => void }) {
  const count = Number(localStorage.getItem('mimo-model-count') ?? 0)
  const [sound, setSound] = useState(() => localStorage.getItem('mimo-sound') !== 'off')
  const [haptics, setHaptics] = useState(() => localStorage.getItem('mimo-haptics') !== 'off')
  const [sendOnEnter, setSendOnEnter] = useState(() => localStorage.getItem('mimo-send-enter') !== 'off')
  const setPreference = (key: string, value: boolean, setter: (value: boolean) => void) => { setter(value); localStorage.setItem(key, value ? 'on' : 'off') }
  return <section className="settings-view"><PageHeader title="设置" onBack={onBack} /><div className="settings-scroll"><div className="private-note"><UserCircle weight="duotone" /><div><strong>私人模式</strong><small>无需登录，数据仅保存在本机</small></div></div><SettingsGroup title="聊天"><SettingsRow icon={<UsersThree />} title="聊天角色" detail={`${roleCount} 个角色`} onClick={() => onOpen('roles')} /><SettingsRow icon={<PlugsConnected />} title="模型服务" detail={count ? `${count} 个模型` : '未连接'} onClick={() => onOpen('connection')} /></SettingsGroup><SettingsGroup title="数据与偏好"><SettingsRow icon={<Database />} title="记忆管理" detail="3 条" onClick={() => onOpen('memory')} /></SettingsGroup><SettingsGroup title="交互"><ToggleRow title="语音回复" detail="语音聊天时自动播放角色声音" value={sound} onChange={(value) => setPreference('mimo-sound', value, setSound)} /><ToggleRow title="触感反馈" detail="重要操作使用轻触反馈" value={haptics} onChange={(value) => setPreference('mimo-haptics', value, setHaptics)} /><ToggleRow title="回车发送" detail="键盘回车直接发送消息" value={sendOnEnter} onChange={(value) => setPreference('mimo-send-enter', value, setSendOnEnter)} /></SettingsGroup><SettingsGroup title="主题"><ThemeRow value={theme} onChange={onThemeChange} /></SettingsGroup><SettingsGroup title="关于"><div className="about-row"><span>MiMo Chat</span><small>本地私人版本 · 0.2.0</small></div></SettingsGroup></div></section>
}

function SettingsGroup({ title, children }: { title: string; children: React.ReactNode }) { return <section className="settings-block"><h3>{title}</h3><div>{children}</div></section> }
function SettingsRow({ icon, title, detail, onClick }: { icon: React.ReactNode; title: string; detail?: string; onClick: () => void }) { return <button className="settings-row" onClick={onClick}><span>{icon}</span><strong>{title}</strong>{detail && <small>{detail}</small>}<CaretRight /></button> }
function ThemeRow({ value, onChange }: { value: 'light' | 'dark' | 'system'; onChange: (value: 'light' | 'dark' | 'system') => void }) { return <div className="theme-row"><button className={value === 'light' ? 'active' : ''} onClick={() => onChange('light')}><Sun /><small>浅色</small></button><button className={value === 'dark' ? 'active' : ''} onClick={() => onChange('dark')}><Moon /><small>深色</small></button><button className={value === 'system' ? 'active' : ''} onClick={() => onChange('system')}><Monitor /><small>跟随系统</small></button></div> }
function PageHeader({ title, onBack, action }: { title: string; onBack: () => void; action?: React.ReactNode }) { return <header className="page-header"><button onClick={onBack} aria-label="返回"><ArrowLeft /></button><h2>{title}</h2>{action ?? <span />}</header> }

function RolesScreen({ roles, defaultRoleId, onBack, onSave, onDefault }: { roles: Role[]; defaultRoleId: string; onBack: () => void; onSave: (roles: Role[]) => void; onDefault: (id: string) => void }) {
  const [editingId, setEditingId] = useState(roles[0].id)
  const [previewing, setPreviewing] = useState(false)
  const role = roles.find((item) => item.id === editingId) ?? roles[0]
  const sampleRef = useRef<HTMLInputElement>(null)
  const previewAudioRef = useRef<HTMLAudioElement | null>(null)
  const update = (patch: Partial<Role>) => onSave(roles.map((item) => item.id === role.id ? { ...item, ...patch } : item))
  const previewVoice = async () => {
    if (previewing) {
      previewAudioRef.current?.pause()
      previewAudioRef.current = null
      if ('speechSynthesis' in window) speechSynthesis.cancel()
      setPreviewing(false)
      return
    }
    setPreviewing(true)
    const previewText = '你好，我是这个角色。这样的声音会用于语音聊天。'
    try {
      const config = readConnection()
      if (config.apiKey) {
        const url = await synthesizeSpeech(config, role.voiceModel, previewText, role.voiceName, role.voiceSample, role.voicePrompt)
        const audio = new Audio(url)
        previewAudioRef.current = audio
        audio.onended = () => setPreviewing(false)
        audio.onerror = () => setPreviewing(false)
        await audio.play()
      } else if ('speechSynthesis' in window) {
        const utterance = new SpeechSynthesisUtterance(previewText)
        utterance.lang = 'zh-CN'
        utterance.onend = () => setPreviewing(false)
        utterance.onerror = () => setPreviewing(false)
        speechSynthesis.cancel()
        speechSynthesis.speak(utterance)
      } else setPreviewing(false)
    } catch {
      setPreviewing(false)
    }
  }
  return <section className="roles-view"><PageHeader title="聊天角色" onBack={onBack} action={<button className="header-text-button" onClick={() => { const newRole: Role = { ...DEFAULT_ROLES[0], id: `role-${Date.now()}`, name: '新角色', description: '自定义聊天角色', color: '#6f766e' }; onSave([...roles, newRole]); setEditingId(newRole.id) }}>新增</button>} /><div className="roles-scroll"><div className="role-tabs">{roles.map((item) => <button className={item.id === role.id ? 'active' : ''} key={item.id} onClick={() => setEditingId(item.id)}><i style={{ background: item.color }}>{item.name.slice(0, 1)}</i><span>{item.name}</span>{item.id === defaultRoleId && <em>默认</em>}</button>)}</div><div className="role-editor"><label><span>角色名称</span><input value={role.name} onChange={(event) => update({ name: event.target.value })} /></label><label><span>角色介绍</span><input value={role.description} onChange={(event) => update({ description: event.target.value })} /></label><label><span>能力设定</span><textarea rows={2} value={role.capabilities} onChange={(event) => update({ capabilities: event.target.value })} /></label><label><span>角色提示词</span><textarea rows={4} value={role.prompt} onChange={(event) => update({ prompt: event.target.value })} /></label><label className="voice-prompt-field"><span>音色提示词</span><textarea rows={3} value={role.voicePrompt ?? ''} onChange={(event) => update({ voicePrompt: event.target.value })} placeholder="例如：温柔、清晰、语速偏慢，重点处稍微停顿。" /></label><div className="voice-choice"><span>聊天音色</span><button className={role.voiceModel === 'mimo-v2.5-tts' ? 'active' : ''} onClick={() => update({ voiceModel: 'mimo-v2.5-tts' })}>内置 TTS<small>自然音色</small></button><button className={role.voiceModel === 'mimo-v2.5-tts-voiceclone' ? 'active' : ''} onClick={() => update({ voiceModel: 'mimo-v2.5-tts-voiceclone' })}>声音克隆<small>{role.voiceSample ? '已录入样本' : '需要音频样本'}</small></button></div><button className="voice-preview" onClick={previewVoice}>{previewing ? <Stop weight="fill" /> : <SpeakerHigh />}{previewing ? '停止试听' : '试听这个音色'}</button>{role.voiceModel === 'mimo-v2.5-tts-voiceclone' && <button className="upload-voice" onClick={() => sampleRef.current?.click()}><Waveform />{role.voiceSample ? '更换声音样本' : '上传声音样本'}</button>}<button className="set-default" disabled={role.id === defaultRoleId} onClick={() => onDefault(role.id)}>{role.id === defaultRoleId ? <><Check />当前默认角色</> : '设为新对话默认角色'}</button>{roles.length > 1 && <button className="delete-role" onClick={() => { const next = roles.filter((item) => item.id !== role.id); onSave(next); setEditingId(next[0].id); if (defaultRoleId === role.id) onDefault(next[0].id) }}><Trash />删除角色</button>}</div></div><input ref={sampleRef} className="hidden-input" type="file" accept="audio/mp3,audio/wav,audio/mpeg" onChange={async (event) => { const file = event.target.files?.[0]; if (file) update({ voiceSample: await fileToDataUrl(file) }) }} /></section>
}

function MemoryScreen({ onBack }: { onBack: () => void }) {
  const [items, setItems] = useState(['我偏好简洁直接的回答', '我的常用城市是杭州', '工作日早上优先处理重要任务'])
  return <section className="subpage-view"><PageHeader title="记忆管理" onBack={onBack} /><div className="subpage-scroll"><p className="page-intro">MiMo 会在合适的时候使用这些信息。你可以随时删除。</p>{items.length ? <div className="memory-list">{items.map((item) => <article key={item}><span>{item}</span><button onClick={() => setItems((current) => current.filter((entry) => entry !== item))}><Trash /></button></article>)}</div> : <div className="empty-state"><Database /><strong>暂无记忆</strong><small>后续对话中确认的偏好会出现在这里</small></div>}</div></section>
}

function ToggleRow({ title, detail, value, onChange }: { title: string; detail: string; value: boolean; onChange: (value: boolean) => void }) { return <button className="toggle-row" onClick={() => onChange(!value)}><span><strong>{title}</strong><small>{detail}</small></span><i className={value ? 'on' : ''}><em /></i></button> }

function ConnectionScreen({ onBack }: { onBack: () => void }) {
  const [config, setConfig] = useState<MimoConnection>(readConnection)
  const [showKey, setShowKey] = useState(false)
  const [results, setResults] = useState<ProbeResult[]>([])
  const [phase, setPhase] = useState<'idle' | 'loading' | 'testing' | 'done'>('idle')
  const [error, setError] = useState('')
  const connect = async () => {
    if (!config.baseUrl.trim() || !config.apiKey.trim()) { setError('请填写模型地址和 API Key'); return }
    setError(''); setResults([]); setPhase('loading'); saveConnection(config)
    try {
      const models = await loadModels(config)
      if (!models.length) throw new Error('没有加载到模型')
      localStorage.setItem('mimo-model-count', String(models.length)); localStorage.setItem('mimo-model-list', JSON.stringify(models))
      setPhase('testing'); setResults(models.map((item) => ({ model: item, capability: '识别中', status: 'testing', detail: '正在验证能力' })))
      for (const item of models) { const result = await probeModel(config, item); setResults((current) => current.map((entry) => entry.model === item ? result : entry)) }
      setPhase('done')
    } catch (reason) { setError(reason instanceof Error ? reason.message : '连接失败'); setPhase('idle') }
  }
  const passed = results.filter((item) => item.status === 'passed' || item.status === 'reachable').length
  return <section className="connection-view"><PageHeader title="模型服务" onBack={onBack} /><div className="connection-scroll"><div className="connection-intro"><PlugsConnected /><div><h1>连接模型服务</h1><p>保存后自动加载模型列表并逐一验证能力。</p></div></div><div className="config-form"><label><span>模型地址</span><input value={config.baseUrl} onChange={(event) => setConfig({ ...config, baseUrl: event.target.value })} /></label><label><span>API Key</span><div><input type={showKey ? 'text' : 'password'} value={config.apiKey} onChange={(event) => setConfig({ ...config, apiKey: event.target.value })} placeholder="输入密钥" /><button onClick={() => setShowKey(!showKey)}>{showKey ? <EyeSlash /> : <Eye />}</button></div></label><label><span>认证方式</span><div className="auth-tabs"><button className={config.authMode === 'api-key' ? 'active' : ''} onClick={() => setConfig({ ...config, authMode: 'api-key' })}>api-key</button><button className={config.authMode === 'bearer' ? 'active' : ''} onClick={() => setConfig({ ...config, authMode: 'bearer' })}>Bearer</button></div></label></div><button className="primary-action" disabled={phase === 'loading' || phase === 'testing'} onClick={connect}>{phase === 'loading' ? '正在加载模型' : phase === 'testing' ? `正在检测 ${passed}/${results.length}` : '连接并自动检测'}</button>{error && <div className="inline-error">{error}</div>}{results.length > 0 && <div className="probe-list"><header><strong>能力体检</strong><small>{passed}/{results.length} 通过</small></header>{results.map((result) => <article key={result.model} className={result.status}><span>{result.status === 'testing' ? <i className="spinner" /> : result.status === 'failed' ? <X /> : <Check />}</span><div><strong>{result.model}</strong><small>{result.capability} · {result.detail}</small></div>{result.latency && <time>{result.latency}ms</time>}</article>)}</div>}</div></section>
}

function demoReply(text: string, role: Role, model: ModelId, hasImage: boolean) {
  if (hasImage) return `我已经看到了这张图片。作为${role.name}，我会先从画面主体、细节和你的目标三个方面来分析。你可以再告诉我最想关注哪一部分。`
  if (model === 'mimo-v2.5-pro') return `我会先把“${text || '这个问题'}”拆成目标、约束和行动三部分，再给你一个可以直接执行的方案。`
  return `明白了。关于“${text || '这件事'}”，我建议先抓住最重要的一步开始。如果你愿意，我可以继续帮你整理成清单。`
}

function fileToDataUrl(file: File): Promise<string> { return new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(String(reader.result)); reader.onerror = reject; reader.readAsDataURL(file) }) }
