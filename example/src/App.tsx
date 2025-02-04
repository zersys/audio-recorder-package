import { Text, View, StyleSheet } from 'react-native';
import { startRecording } from 'audio-recorder-package';
import { useEffect } from 'react';

export default function App() {
  const testFunction = async () => {
    try {
      console.log('test');
      const data = await startRecording();
      console.log(data, 'ieufiueriueiru');
    } catch (error) {
      console.log(error);
    }
  };

  useEffect(() => {
    testFunction();
  }, []);

  return (
    <View style={styles.container}>
      <Text>Result: </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
