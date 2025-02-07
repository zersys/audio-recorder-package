import type { TurboModule } from 'react-native';
import type { EventEmitter } from 'react-native/Libraries/Types/CodegenTypes';
import { TurboModuleRegistry } from 'react-native';

export interface RecordingResponse {
  started?: boolean;
  filePath?: string;
  duration?: number;
}
// export interface EventRecordingStatus {
//   status?:
//     | 'Paused'
//     | 'Started'
//     | 'Stopped'
//     | 'StoppedDueToTimeLimit'
//     | 'PausedDueToExternalAction'
//     | 'Resumed';

// }

export interface EventRecordingStatus {
  status?:
    | 'Paused'
    | 'Started'
    | 'Stopped'
    | 'StoppedDueToTimeLimit'
    | 'PausedDueToExternalAction'
    | 'Resumed'
    | 'Interrupted';
  // reason?: 'userStop' | 'autoStop' | 'timeLimit' | 'externalAction';
  timeRemaining?: number;
}

export interface Spec extends TurboModule {
  /**
   * Supported events.
   */

  startRecording(
    recordingTimeLimit: number,
    notifyTimeLimitReached: boolean | undefined,
    notifyTimeLimit: number | undefined
  ): Promise<RecordingResponse>;
  stopRecording(): Promise<RecordingResponse>;
  pauseRecording(): Promise<RecordingResponse>;
  resumeRecording(): Promise<RecordingResponse>;

  readonly onRecordingStatusChanged: EventEmitter<EventRecordingStatus>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('AudioRecorderPackage');
