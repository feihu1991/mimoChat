export type RecordingHandle = {
  stream: MediaStream
  context: AudioContext
  source: MediaStreamAudioSourceNode
  processor: ScriptProcessorNode
  chunks: Float32Array[]
  sampleRate: number
}

export async function beginWavRecording(): Promise<RecordingHandle> {
  const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
  const context = new AudioContext()
  const source = context.createMediaStreamSource(stream)
  const processor = context.createScriptProcessor(4096, 1, 1)
  const chunks: Float32Array[] = []
  processor.onaudioprocess = (event) => chunks.push(new Float32Array(event.inputBuffer.getChannelData(0)))
  source.connect(processor)
  processor.connect(context.destination)
  return { stream, context, source, processor, chunks, sampleRate: context.sampleRate }
}

export async function finishWavRecording(handle: RecordingHandle): Promise<string> {
  handle.processor.disconnect()
  handle.source.disconnect()
  handle.stream.getTracks().forEach((track) => track.stop())
  await handle.context.close()
  const length = handle.chunks.reduce((sum, chunk) => sum + chunk.length, 0)
  const merged = new Float32Array(length)
  let offset = 0
  handle.chunks.forEach((chunk) => { merged.set(chunk, offset); offset += chunk.length })
  const targetRate = 16000
  const ratio = handle.sampleRate / targetRate
  const samples = new Float32Array(Math.max(1, Math.floor(merged.length / ratio)))
  for (let i = 0; i < samples.length; i++) samples[i] = merged[Math.floor(i * ratio)] ?? 0
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)
  const write = (at: number, value: string) => [...value].forEach((char, index) => view.setUint8(at + index, char.charCodeAt(0)))
  write(0, 'RIFF'); view.setUint32(4, 36 + samples.length * 2, true); write(8, 'WAVE')
  write(12, 'fmt '); view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true)
  view.setUint32(24, targetRate, true); view.setUint32(28, targetRate * 2, true); view.setUint16(32, 2, true); view.setUint16(34, 16, true)
  write(36, 'data'); view.setUint32(40, samples.length * 2, true)
  samples.forEach((sample, index) => view.setInt16(44 + index * 2, Math.max(-1, Math.min(1, sample)) * 0x7fff, true))
  const bytes = new Uint8Array(buffer)
  let binary = ''
  bytes.forEach((byte) => { binary += String.fromCharCode(byte) })
  return `data:audio/wav;base64,${btoa(binary)}`
}
