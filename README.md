# AuraSense - Real-time Stress & Wellness Monitoring

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=26)
[![TensorFlow Lite](https://img.shields.io/badge/TensorFlow-Lite-orange.svg)](https://www.tensorflow.org/lite)

AuraSense is an Android application that provides real-time stress monitoring using BLE sensors and AI-powered analysis. The app uses TensorFlow Lite for on-device machine learning to detect stress levels from biometric data including heart rate, temperature, and motion sensors.

## 🚀 Features

### 📱 **Modern UI/UX**

- **Clean Home Screen** with biometric data cards (❤️ Heart Rate, 🌡️ Temperature, 🏃‍♂️ Motion)
- **Dynamic Status Cards** that change color based on stress levels
- **Consistent Bottom Navigation** across all screens
- **Material Design 3** principles with custom theming

### 🧠 **AI-Powered Stress Detection**

- **TensorFlow Lite Integration** for on-device ML inference
- **Smoothing Algorithm** with 5-sample rolling window for stable predictions
- **Hysteresis Logic** to prevent rapid state switching
- **Real-time Analysis** of 7 biometric parameters

### 🔔 **Smart Notifications**

- **System Notifications** when high stress is detected
- **Vibration Alerts** with customizable patterns
- **Rich Notifications** with heart rate data and wellness advice
- **Notification History** management

### 📊 **Data Management**

- **Historical Data Storage** with stress level analysis
- **Clean History Display** showing stress levels and timestamps
- **Data Export** capabilities
- **Clear History** with confirmation dialogs

### 🔗 **BLE Connectivity**

- **Bluetooth Low Energy** sensor integration
- **Real-time Data Streaming** from wearable devices
- **Connection Management** with automatic reconnection
- **JSON Data Processing** with error handling

## 🏗️ Architecture

### **Design Patterns**

- **Singleton Pattern** - BLEManager for connection management
- **Observer Pattern** - BLECallback for event notifications
- **Strategy Pattern** - TFLiteEmotionInterpreter for AI algorithms
- **Repository Pattern** - HistoryStorage for data abstraction

### **Project Structure**

```
com.example.aurasense/
├── activities/          # UI Activities
│   ├── HomeActivity     # Main dashboard
│   ├── HistoryActivity  # Data history
│   ├── NotificationActivity # Alert management
│   ├── ProfileActivity  # User profile
│   └── SettingsActivity # App settings
├── ble/                 # Bluetooth connectivity
│   └── BLEManager       # BLE connection management
└── utils/               # Utilities & AI
    ├── TFLiteEmotionInterpreter # AI stress detection
    ├── NotificationManager      # System notifications
    └── HistoryStorage          # Data storage
```

## 🛠️ Setup & Installation

### **Prerequisites**

- Android Studio Arctic Fox or newer
- Android SDK API 26+ (Android 8.0)
- Java 11
- Gradle 8.0+

### **Installation Steps**

1. **Clone the repository**

   ```bash
   git clone https://github.com/your-team/aurasense.git
   cd aurasense
   ```

2. **Open in Android Studio**

   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

3. **Sync Project**

   - Android Studio will automatically sync Gradle
   - If not, click "Sync Now" in the notification bar

4. **Build & Run**
   ```bash
   ./gradlew build
   ./gradlew installDebug
   ```

## 📋 Dependencies

### **Core Dependencies**

```kotlin
// Android Core
implementation("androidx.appcompat:appcompat:1.6.1")
implementation("com.google.android.material:material:1.12.0")
implementation("androidx.constraintlayout:constraintlayout:2.1.4")

// TensorFlow Lite
implementation("org.tensorflow:tensorflow-lite:2.13.0")

// Compose (Future UI framework)
implementation(platform(libs.androidx.compose.bom))
implementation(libs.androidx.ui)
implementation(libs.androidx.material3)
```

### **Permissions Required**

```xml
<!-- Bluetooth & Location -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- Notifications -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />
```

## 🤖 AI Model Details

### **TensorFlow Lite Model**

- **Input Features**: 7 normalized parameters (BPM, HRV, Temperature, AccX, AccY, AccZ, AccMag)
- **Output**: Stress probability (0 = Normal, 1 = High Stress)
- **Model File**: `app/src/main/assets/stress_model.tflite`

### **Prediction Algorithm**

```java
// Smoothing with 5-sample window
private static final int SMOOTH_WINDOW = 5;
private static final float STRESS_THRESHOLD = 0.7f;
private static final float NORMAL_THRESHOLD = 0.3f;

// Hysteresis prevents rapid state changes
int prediction = interpreter.predictWithSmoothing(bpm, hrv, temp, accX, accY, accZ, accMag);
```

## 📱 Screenshots

### Home Screen

- Real-time biometric monitoring
- Dynamic stress status card
- Clean card-based layout

### History & Notifications

- Simplified stress history display
- System notification management
- Data clearing capabilities

## 🔧 Development

### **Building**

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test
```

### **Code Style**

- Follow Android development best practices
- Use Material Design guidelines
- Maintain consistent naming conventions
- Add comments for complex algorithms

## 🤝 Contributing

1. **Fork the repository**
2. **Create feature branch** (`git checkout -b feature/amazing-feature`)
3. **Commit changes** (`git commit -m 'Add amazing feature'`)
4. **Push to branch** (`git push origin feature/amazing-feature`)
5. **Open Pull Request**

## 📄 License

This project is part of COEN/ELEC 390 coursework.

## 👥 Team

- **Your Team Members** - Add your names here
- **Course**: COEN/ELEC 390
- **Institution**: [Your University]

## 🆘 Troubleshooting

### **Common Issues**

1. **Build Errors**: Run `./gradlew clean` then rebuild
2. **BLE Connection**: Ensure location permissions are granted
3. **TensorFlow Lite**: Verify model file exists in assets folder
4. **Notifications**: Check notification permissions on Android 13+

### **Support**

- Check the [Issues](https://github.com/your-team/aurasense/issues) page
- Review the [UML Documentation](AuraSense_UML_Diagrams.md)
- Contact team members for help

---

**Built with ❤️ for wellness monitoring and stress management**
