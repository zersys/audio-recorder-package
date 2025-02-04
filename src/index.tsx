import AudioRecorderPackage, {
  type RecordingResponse,
} from './NativeAudioRecorderPackage';

export function startRecording(): Promise<RecordingResponse> {
  return AudioRecorderPackage.startRecording();
}

export function stopRecording(): Promise<RecordingResponse> {
  return AudioRecorderPackage.stopRecording();
}

export function pauseRecording(): Promise<RecordingResponse> {
  return AudioRecorderPackage.pauseRecording();
}

export function resumeRecording(): Promise<RecordingResponse> {
  return AudioRecorderPackage.resumeRecording();
}
