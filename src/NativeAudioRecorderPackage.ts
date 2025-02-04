import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface RecordingResponse {
  started?: boolean;
  filePath?: string;
  duration?: number;
}

export interface Spec extends TurboModule {
  startRecording(): Promise<RecordingResponse>;
  stopRecording(): Promise<RecordingResponse>;
  pauseRecording(): Promise<RecordingResponse>;
  resumeRecording(): Promise<RecordingResponse>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('AudioRecorderPackage');