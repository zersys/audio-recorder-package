import {
  Text,
  View,
  StyleSheet,
  TouchableOpacity,
  Platform,
  NativeEventEmitter,
  TurboModuleRegistry,
} from 'react-native';
import {
  startRecording,
  stopRecording,
  pauseRecording,
  resumeRecording,
} from 'audio-recorder-package';
import { useState, useEffect, useRef } from 'react';
import { PermissionsAndroid } from 'react-native';
import type { EventSubscription } from 'react-native';
import type {
  RecordingResponse,
  Spec,
} from '../../src/NativeAudioRecorderPackage';
import Share from 'react-native-share';

// ios
const AudioRecorderPackage = TurboModuleRegistry.getEnforcing<Spec>(
  'AudioRecorderPackage'
);
const audioRecorderEvents = new NativeEventEmitter(AudioRecorderPackage);
//

export default function App() {
  const listenerSubscription = useRef<null | EventSubscription>(null);
  const [recordingStatus, setRecordingStatus] = useState('idle'); // idle, recording, paused
  const [recordingData, setRecordingData] = useState<null | RecordingResponse>(
    null
  );
  const [error, setError] = useState(null);

  useEffect(() => {
    if (Platform.OS === 'android') {
      PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.RECORD_AUDIO
      ).then((result) => {
        console.log('Microphone permission result:', result);
      });
    }
  }, []);

  // useEffect(() => {
  //   listenerSubscription.current =
  //     AudioRecorderPackage.onRecordingStatusChanged((data) => {
  //       console.log(data, 'eiuhfiurefiurifueiu');
  //     });

  //   return () => {
  //     listenerSubscription.current?.remove();
  //     listenerSubscription.current = null;
  //   };
  // }, []); // android

  useEffect(() => {
    const subscription = audioRecorderEvents.addListener(
      'onRecordingStatusChanged',
      (data) => {
        if (data?.reason == 'autoStop') {
          setError(null);
          setRecordingStatus('idle');
          shareFile(recordingData?.filePath);
        }
      }
    );

    return () => subscription.remove();
  }, []); // ios

  const handleStartRecording = async () => {
    try {
      setError(null);
      const data = await startRecording(10, true, 5);
      setRecordingData(data);
      setRecordingStatus('recording');
    } catch (err) {
      setError(err.message);
      console.log(err);
    }
  };

  const handleStopRecording = async () => {
    try {
      setError(null);
      await stopRecording();
      setRecordingStatus('idle');
      shareFile(recordingData?.filePath);
    } catch (err) {
      setError(err.message);
      console.log(err);
    }
  };

  const handlePauseRecording = async () => {
    try {
      setError(null);
      await pauseRecording();
      setRecordingStatus('paused');
    } catch (err) {
      setError(err.message);
      console.log(err);
    }
  };

  const handleResumeRecording = async () => {
    try {
      setError(null);
      await resumeRecording();
      setRecordingStatus('recording');
    } catch (err) {
      setError(err.message);
      console.log(err);
    }
  };

  const shareFile = async (filePath) => {
    try {
      const shareOptions = {
        title: 'Share your recording',
        url: `file://${filePath}`,
        type: 'audio/m4a',
      };

      // Open the native share dialog
      await Share.open(shareOptions);
    } catch (error) {
      console.log('Error while sharing file:', error);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.status}>Status: {recordingStatus}</Text>
      {recordingData && (
        <Text style={styles.data}>
          Recording Data: {JSON.stringify(recordingData)}
        </Text>
      )}
      {error && <Text style={styles.error}>Error: {error}</Text>}

      <View style={styles.buttonContainer}>
        {recordingStatus === 'idle' && (
          <TouchableOpacity
            style={styles.button}
            onPress={handleStartRecording}
          >
            <Text style={styles.buttonText}>Start Recording</Text>
          </TouchableOpacity>
        )}

        {recordingStatus === 'recording' && (
          <>
            <TouchableOpacity
              style={styles.button}
              onPress={handlePauseRecording}
            >
              <Text style={styles.buttonText}>Pause</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.button, styles.stopButton]}
              onPress={handleStopRecording}
            >
              <Text style={styles.buttonText}>Stop</Text>
            </TouchableOpacity>
          </>
        )}

        {recordingStatus === 'paused' && (
          <>
            <TouchableOpacity
              style={styles.button}
              onPress={handleResumeRecording}
            >
              <Text style={styles.buttonText}>Resume</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.button, styles.stopButton]}
              onPress={handleStopRecording}
            >
              <Text style={styles.buttonText}>Stop</Text>
            </TouchableOpacity>
          </>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 20,
  },
  status: {
    fontSize: 18,
    marginBottom: 20,
  },
  data: {
    fontSize: 14,
    marginBottom: 20,
    textAlign: 'center',
  },
  error: {
    color: 'red',
    marginBottom: 20,
  },
  buttonContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    flexWrap: 'wrap',
    gap: 10,
  },
  button: {
    backgroundColor: '#007AFF',
    padding: 15,
    borderRadius: 8,
    minWidth: 120,
    alignItems: 'center',
  },
  stopButton: {
    backgroundColor: '#FF3B30',
  },
  buttonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: '600',
  },
});
