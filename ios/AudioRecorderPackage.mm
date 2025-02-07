#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>
#import <UserNotifications/UserNotifications.h>
#import <React/RCTLog.h>
#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>

@interface AudioRecorderPackage : RCTEventEmitter <RCTBridgeModule, UNUserNotificationCenterDelegate>

@property (nonatomic, strong) AVAudioRecorder *audioRecorder;
@property (nonatomic, strong) NSTimer *audioLevelTimer;
@property (nonatomic, strong) NSString *outputFilePath;
@property (nonatomic, assign) BOOL isRecording;
@property (nonatomic, assign) BOOL wasRecordingBeforeInterruption;
@property (nonatomic, strong) AVAudioSession *audioSession;
@property (nonatomic, strong) UNUserNotificationCenter *notificationCenter;
@property (nonatomic, assign) BOOL isPaused;
@property (nonatomic, strong) NSTimer *notificationTimer;
@property (nonatomic, strong) NSTimer *recordingTimer;
@property (nonatomic, strong) NSTimer *warningTimer;
@property (nonatomic, assign) double recordingDuration;
@property (nonatomic, assign) double timeLimit;
@property (nonatomic, assign) double warningTime;
@property (nonatomic, assign) BOOL autoCancelFired;
@property (nonatomic, strong) NSTimer *autoStopTimer;
@property (nonatomic, assign) BOOL showedNotification;
@property (nonatomic, assign) NSTimeInterval pausedTime;
@property (nonatomic, assign) BOOL isInterrupted;

@end

@implementation AudioRecorderPackage

RCT_EXPORT_MODULE(AudioRecorderPackage);

- (NSArray<NSString *> *)supportedEvents {
  return @[@"onRecordingStatusChanged"];
}

- (instancetype)init {
    self = [super init];
    if (self) {
        _showedNotification = NO;
        _audioSession = [AVAudioSession sharedInstance];
        _notificationCenter = [UNUserNotificationCenter currentNotificationCenter];
        _notificationCenter.delegate = self;
        [self configureAudioSession];
        [self requestNotificationAuthorization];
        [[NSNotificationCenter defaultCenter] addObserver:self
                                                 selector:@selector(handleAudioSessionInterruption:)
                                                 name:AVAudioSessionInterruptionNotification
                                               object:nil];
    }
    return self;
}

- (void)requestNotificationAuthorization {
    UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];
    [center requestAuthorizationWithOptions:(UNAuthorizationOptionAlert | UNAuthorizationOptionSound)
                          completionHandler:^(BOOL granted, NSError *error) {
                              if (granted) {
                                  NSLog(@"Notification authorization granted.");
                              } else {
                                  NSLog(@"Notification authorization denied: %@", error);
                              }
                          }];
}

- (void)dealloc {
    [[NSNotificationCenter defaultCenter] removeObserver:self name:AVAudioSessionInterruptionNotification object:nil];
}

- (void)handleAudioSessionInterruption:(NSNotification *)notification {
    NSDictionary *userInfo = notification.userInfo;
    NSNumber *interruptionTypeNumber = userInfo[AVAudioSessionInterruptionTypeKey];

    if (interruptionTypeNumber) {
        switch ([interruptionTypeNumber unsignedIntegerValue]) {
            case AVAudioSessionInterruptionTypeBegan: {
                NSLog(@"Audio session interrupted");
                if (self.isRecording) {
                    self.wasRecordingBeforeInterruption = YES;
                    self.isInterrupted = YES;
                    [self.audioRecorder pause];
                    [self sendRecordingStatusEvent:@"Interrupted"];

                    [self.recordingTimer invalidate];
                    self.recordingTimer = nil;

                    [self.autoStopTimer invalidate];
                    self.autoStopTimer = nil;
                    self.autoCancelFired = NO;

                    self.pausedTime = self.recordingDuration;
                }
                break;
            }
            case AVAudioSessionInterruptionTypeEnded: {
                NSLog(@"Audio session interruption ended");
                NSNumber *interruptionOptionsNumber = userInfo[AVAudioSessionInterruptionOptionKey];

                if (interruptionOptionsNumber) {
                    AVAudioSessionInterruptionOptions options = [interruptionOptionsNumber unsignedIntegerValue];

                    if (options == AVAudioSessionInterruptionOptionShouldResume && self.wasRecordingBeforeInterruption && self.isInterrupted) {
                        NSError *error = nil;
                        if (![_audioSession setActive:YES error:&error]) {
                            NSLog(@"Failed to reactivate audio session after interruption: %@", error);
                        } else {
                            [self.audioRecorder record];
                            self.isPaused = NO;
                            self.isInterrupted = NO;
                            [self sendRecordingStatusEvent:@"Resumed"];
                            self.wasRecordingBeforeInterruption = NO;

                            dispatch_async(dispatch_get_main_queue(), ^{
                                self.recordingTimer = [NSTimer scheduledTimerWithTimeInterval:1.0
                                                                                          target:self
                                                                                        selector:@selector(updateRecordingDuration:)
                                                                                        userInfo:nil
                                                                                         repeats:YES];
                            });

                            self.recordingDuration = self.pausedTime;
                            self.pausedTime = 0;

                            if (self.autoCancelFired) {
                                NSTimeInterval remainingTime = self.timeLimit - self.recordingDuration;
                                if (remainingTime > 0) {
                                    dispatch_async(dispatch_get_main_queue(), ^{
                                        self.autoStopTimer = [NSTimer scheduledTimerWithTimeInterval:remainingTime target:self selector:@selector(autoStopRecording:) userInfo:nil repeats:NO];
                                    });
                                } else {
                                    [self autoStopRecording:nil];
                                }
                            }

                        }
                    }
                }
                break;
            }
            default:
                break;
        }
    }
}

- (void)configureAudioSession {
  NSError *error = nil;
  AVAudioSession *audioSession = [AVAudioSession sharedInstance];
  
  AVAudioSessionCategoryOptions options = AVAudioSessionCategoryOptionDefaultToSpeaker | AVAudioSessionCategoryOptionAllowBluetooth | AVAudioSessionCategoryOptionAllowAirPlay | AVAudioSessionCategoryOptionAllowBluetoothA2DP | AVAudioSessionCategoryOptionMixWithOthers;

  @try {
    [audioSession setCategory:AVAudioSessionCategoryPlayAndRecord mode:AVAudioSessionModeDefault options:options error:&error];
    if (error) {
      RCTLogError(@"Failed to set audio session category: %@", error.localizedDescription);
      return;
    }

    [audioSession setActive:YES error:&error];
    if (error) {
        RCTLogError(@"Failed to activate audio session: %@", error.localizedDescription);
        return;
    }
  } @catch (NSException *exception) {
    RCTLogError(@"Exception configuring audio session: %@", exception.reason);
  }
}

RCT_EXPORT_METHOD(startRecording:(double)recordingTimeLimit
                    notifyTimeLimitReached:(nullable NSNumber *)notifyTimeLimitReached
                    notifyTimeLimit:(nullable NSNumber *)notifyTimeLimit
                    resolver:(RCTPromiseResolveBlock)resolve
                    rejecter:(RCTPromiseRejectBlock)reject) {

    AVAudioSession *session = [AVAudioSession sharedInstance];
    NSArray<AVAudioSessionPortDescription *> *availableInputs = session.availableInputs;
    NSLog(@"Inputs: %lu", (unsigned long)availableInputs.count);

    if (availableInputs.count > 1) {
        UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"Choose Microphone"
                                                                  message:@"Select the microphone you want to use."
                                                           preferredStyle:UIAlertControllerStyleAlert];

        for (AVAudioSessionPortDescription *port in availableInputs) {
            NSString *portName = port.portName;
            UIAlertAction *action = [UIAlertAction actionWithTitle:portName
                                                                   style:UIAlertActionStyleDefault
                                                                 handler:^(UIAlertAction * _Nonnull action) {
                                                                     NSError *error = nil;
                                                                     if (![session setPreferredInput:port error:&error]) {
                                                                         NSLog(@"Error setting preferred input: %@", error);
                                                                         reject(@"INPUT_ERROR", @"Failed to set preferred input", error);
                                                                     } else {
                                                                         [self startRecordingWithSelectedInput:port recordingTimeLimit:recordingTimeLimit notifyTimeLimitReached:notifyTimeLimitReached notifyTimeLimit:notifyTimeLimit resolver:resolve rejecter:reject];
                                                                     }
                                                                 }];
            [alert addAction:action];
        }

        UIAlertAction *cancelAction = [UIAlertAction actionWithTitle:@"Cancel" style:UIAlertActionStyleCancel handler:^(UIAlertAction * _Nonnull action) {
            reject(@"CANCELLED", @"Microphone selection cancelled", nil);
        }];
        [alert addAction:cancelAction];

        dispatch_async(dispatch_get_main_queue(), ^{
           UIViewController * presentingVC = RCTPresentedViewController();
            [presentingVC presentViewController:alert animated:YES completion:nil];
        });

    } else {
        [self startRecordingWithSelectedInput:nil recordingTimeLimit:recordingTimeLimit notifyTimeLimitReached:notifyTimeLimitReached notifyTimeLimit:notifyTimeLimit resolver:resolve rejecter:reject];
    }
}

- (void)startRecordingWithSelectedInput:(AVAudioSessionPortDescription *)port recordingTimeLimit:(double)recordingTimeLimit notifyTimeLimitReached:(nullable NSNumber *)notifyTimeLimitReached notifyTimeLimit:(nullable NSNumber *)notifyTimeLimit resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject {

    NSError *error = nil;

    NSString *filePath = [NSTemporaryDirectory() stringByAppendingPathComponent:[NSString stringWithFormat:@"audioRecording_%@.m4a", [[NSUUID UUID] UUIDString]]];
    NSURL *outputURL = [NSURL fileURLWithPath:filePath];

    NSDictionary *settings = @{
        AVFormatIDKey: @(kAudioFormatMPEG4AAC),
        AVSampleRateKey: @(12000),
        AVNumberOfChannelsKey: @(1),
        AVEncoderAudioQualityKey: @(AVAudioQualityHigh)
    };

    self.audioRecorder = [[AVAudioRecorder alloc] initWithURL:outputURL settings:settings error:&error];
    if (error) {
        reject(@"RECORDER_ERROR", @"Failed to initialize recorder", error);
        return;
    }

    [self.audioRecorder setMeteringEnabled:YES];
    [self.audioRecorder prepareToRecord];

    AVAudioSession *session = [AVAudioSession sharedInstance];

    if (port) { // Set the preferred input only if the user selected one
        if (![session setPreferredInput:port error:&error]) {
            NSLog(@"Error setting preferred input: %@", error);
            reject(@"INPUT_ERROR", @"Failed to set preferred input", error);
            return;
        }
    }

    [self.audioRecorder record];

    self.isRecording = YES;
    self.isPaused = NO;
    self.outputFilePath = filePath;

    self.timeLimit = recordingTimeLimit;
    self.warningTime = [notifyTimeLimit doubleValue];

    self.recordingDuration = 0;

    dispatch_async(dispatch_get_main_queue(), ^{
        self.recordingTimer = [NSTimer scheduledTimerWithTimeInterval:1.0
                                                                  target:self
                                                                selector:@selector(updateRecordingDuration:)
                                                                userInfo:nil
                                                                 repeats:YES];
    });

    [self sendRecordingStatusEvent:@"Started"];
    resolve(@{@"started": @(YES), @"filePath": self.outputFilePath});
}


- (void)updateRecordingDuration:(NSTimer *)timer {
    self.recordingDuration++;
    NSLog(@"Recording Duration: %f seconds", self.recordingDuration);

    if (self.recordingDuration >= self.timeLimit - self.warningTime && !self.showedNotification) {
        self.showedNotification = YES;
   
        [self sendEventWithName:@"onRecordingStatusChanged" body:@{@"timeRemaining": @(self.warningTime)}];
        [self scheduleNotification];

        if (!self.autoCancelFired) {
            self.autoCancelFired = YES;
            self.autoStopTimer = [NSTimer scheduledTimerWithTimeInterval:self.warningTime target:self selector:@selector(autoStopRecording:) userInfo:nil repeats:NO];
        }
    }
}

- (void)scheduleNotification {
    dispatch_async(dispatch_get_main_queue(), ^{
        NSLog(@"Scheduling notification on main thread");

        UNMutableNotificationContent *content = [[UNMutableNotificationContent alloc] init];
        content.title = @"Recording Alert";
        content.body = [NSString stringWithFormat:@"Recording will end in %.0f seconds", self.warningTime];
        content.sound = [UNNotificationSound defaultSound];

        UNNotificationRequest *request = [UNNotificationRequest requestWithIdentifier:@"recordingWarning"
                                                                              content:content
                                                                              trigger:nil];

        UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];
        [center addNotificationRequest:request withCompletionHandler:^(NSError * _Nullable error) {
            if (error) {
                NSLog(@"Error scheduling notification: %@", error);
            } else {
                NSLog(@"Notification scheduled successfully.");
            }
        }];
    });
}

- (void)userNotificationCenter:(UNUserNotificationCenter *)center willPresentNotification:(UNNotification *)notification withCompletionHandler:(void (^)(UNNotificationPresentationOptions))completionHandler {
    NSLog(@"Notification will present! (Delegate method called)");

    completionHandler(UNNotificationPresentationOptionAlert | UNNotificationPresentationOptionSound);
}

- (void)autoStopRecording:(NSTimer *)timer {
    if (self.isRecording) {

        NSString *filePath = self.outputFilePath;
        NSTimeInterval duration = self.audioRecorder.currentTime;

        [self.audioRecorder stop];
        self.isRecording = NO;
        self.isPaused = NO;

        [self sendRecordingStatusEvent:@"Stopped"];

        [self sendEventWithName:@"onRecordingStatusChanged" body:@{
            @"reason": @"autoStop",
            @"filePath": filePath ?: @"",
            @"duration": @(duration)
        }];

        [self.recordingTimer invalidate];
        self.recordingTimer = nil;

        [self.autoStopTimer invalidate];
        self.autoStopTimer = nil;

    }
}

RCT_EXPORT_METHOD(pauseRecording:(RCTPromiseResolveBlock)resolve
                     rejecter:(RCTPromiseRejectBlock)reject) {
    if (self.isRecording && !self.isPaused) {
        [self.audioRecorder pause];
        self.isPaused = YES;
        [self sendRecordingStatusEvent:@"Paused"];

        [self.recordingTimer invalidate];
        self.recordingTimer = nil;
        self.pausedTime = self.recordingDuration;

        [self.autoStopTimer invalidate];
        self.autoStopTimer = nil;
        self.autoCancelFired = NO;

        resolve(@{@"paused": @(YES)});
    } else {
        reject(@"PAUSE_ERROR", @"Recording is not active or already paused", nil);
    }
}

RCT_EXPORT_METHOD(resumeRecording:(RCTPromiseResolveBlock)resolve
                      rejecter:(RCTPromiseRejectBlock)reject) {
    if (self.isRecording && self.isPaused) {
        NSError *error = nil;

        if (![_audioSession setActive:YES error:&error]) {
            reject(@"RESUME_ERROR", @"Failed to reactivate audio session", error);
            return;
        }

        [self.audioRecorder record];
        self.isPaused = NO;
        [self sendRecordingStatusEvent:@"Resumed"];

        dispatch_async(dispatch_get_main_queue(), ^{
            self.recordingTimer = [NSTimer scheduledTimerWithTimeInterval:1.0
                                                                      target:self
                                                                    selector:@selector(updateRecordingDuration:)
                                                                    userInfo:nil
                                                                     repeats:YES];
        });

        self.recordingDuration = self.pausedTime;
        self.pausedTime = 0;

        if (self.autoCancelFired) {
            NSTimeInterval remainingTime = self.timeLimit - self.recordingDuration;
            if (remainingTime > 0) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    self.autoStopTimer = [NSTimer scheduledTimerWithTimeInterval:remainingTime target:self selector:@selector(autoStopRecording:) userInfo:nil repeats:NO];
                });
            } else {
                [self autoStopRecording:nil];
            }
        }

        resolve(@{@"resumed": @(YES)});
    } else {
        reject(@"RESUME_ERROR", @"Recording is not paused", nil);
    }
}

RCT_EXPORT_METHOD(stopRecording:(RCTPromiseResolveBlock)resolve
                    rejecter:(RCTPromiseRejectBlock)reject) {
  if (self.isRecording) {
    NSString *filePath = self.outputFilePath;
    NSTimeInterval duration = self.audioRecorder.currentTime;

    [self.audioRecorder stop];
    self.isRecording = NO;
    self.isPaused = NO;

    [self sendRecordingStatusEvent:@"Stopped"];

    [self sendEventWithName:@"onRecordingStatusChanged" body:@{
        @"reason": @"userStop",
        @"filePath": filePath ?: @"",
        @"duration": @(duration)
    }];

    [self.recordingTimer invalidate];
    self.recordingTimer = nil;

    [self.autoStopTimer invalidate];
    self.autoStopTimer = nil;

    resolve(@{@"filePath": filePath, @"duration": @(duration)});
  } else {
    reject(@"STOP_ERROR", @"No recording in progress", nil);
  }
}

- (void)sendRecordingStatusEvent:(NSString *)status {
  NSDictionary *statusDict = @{@"status": status};

    [self sendEventWithName:@"onRecordingStatusChanged" body:statusDict];
}

@end

