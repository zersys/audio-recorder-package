import type { TurboModule } from 'react-native';
import type { EventEmitter } from 'react-native/Libraries/Types/CodegenTypes';
import { TurboModuleRegistry } from 'react-native';

export interface RecordingResponse {
  started?: boolean;
  filePath?: string;
  duration?: number;
}
export interface EventRecordingStatus {
  status?:
    | 'Paused'
    | 'Started'
    | 'Stopped'
    | 'StoppedDueToTimeLimit'
    | 'PausedDueToExternalAction'
    | 'Resumed';
}
export interface MicrophoneMap {
  id: string;
  name: string;
  type: string;
}


export interface Spec extends TurboModule {
  /**
   * Supported events.
   */
  readonly onRecordingStatusChanged: EventEmitter<EventRecordingStatus>;

  startRecording(
    recordingTimeLimit: number,
    notifyTimeLimitReached: boolean | undefined,
    notifyTimeLimit: number | undefined
  ): Promise<RecordingResponse>;
  stopRecording(): Promise<RecordingResponse>;
  pauseRecording(): Promise<RecordingResponse>;
  resumeRecording(): Promise<RecordingResponse>;
  getAvailableMicrophones():Promise<[MicrophoneMap]>;
  switchMicrophone(
    microphoneId: string,
  ):Promise<boolean>;
  getCurrentMicrophone():Promise<MicrophoneMap>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('AudioRecorderPackage');
