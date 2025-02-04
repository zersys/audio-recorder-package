import AudioRecorderPackage, {
  type RecordingResponse,
} from './NativeAudioRecorderPackage';

export function startRecording(
  recordingTimeLimit: number,
  notifyTimeLimitReached: boolean | undefined,
  notifyTimeLimit: number | undefined
): Promise<RecordingResponse> {
  return AudioRecorderPackage.startRecording(
    recordingTimeLimit,
    notifyTimeLimitReached,
    notifyTimeLimit
  );
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

export default AudioRecorderPackage;
