# AuraSense UML Diagrams

## 1. Class Diagram - Overall System Architecture

```mermaid
classDiagram
    %% Activities Package
    class HomeActivity {
        -TextView hrValue, tempCard, motionCard, debugRawJsonText
        -Button connectDeviceBtn
        -BLEManager bleManager
        -TFLiteEmotionInterpreter interpreter
        -NotificationManager notificationManager
        -boolean highStressTriggered
        -LinearLayout statusSummaryCard
        -ImageView statusIcon
        -TextView statusMessage
        +onCreate(Bundle)
        +onConnected()
        +onDisconnected()
        +onDataReceived(String)
        +onResume()
        -updateStatusCard(String, String)
    }

    class HistoryActivity {
        -ListView historyListView
        -Button clearHistoryBtn
        -TFLiteEmotionInterpreter interpreter
        +onCreate(Bundle)
        -loadHistory()
        -showClearHistoryDialog()
    }

    class NotificationActivity {
        -TextView notificationHistory
        -Button clearNotificationsBtn
        +onCreate(Bundle)
        -loadNotifications()
        -showClearNotificationsDialog()
    }

    class WelcomeActivity {
        +onCreate(Bundle)
    }

    class LoginActivity {
        +onCreate(Bundle)
    }

    class SignupActivity {
        +onCreate(Bundle)
    }

    class DevicePairingActivity {
        +onCreate(Bundle)
    }

    class ProfileActivity {
        +onCreate(Bundle)
    }

    class SettingsActivity {
        +onCreate(Bundle)
    }

    class ConsentFormActivity {
        +onCreate(Bundle)
    }

    %% BLE Package
    class BLEManager {
        <<Singleton>>
        -static BLEManager instance
        -Context context
        -BLECallback callback
        -BluetoothAdapter bluetoothAdapter
        -BluetoothGatt bluetoothGatt
        -StringBuilder bleBuffer
        +static getInstance(Context, BLECallback) BLEManager
        +setCallback(BLECallback)
        +connectToDevice(BluetoothDevice)
        +disconnect()
        -BluetoothGattCallback gattCallback
    }

    class BLECallback {
        <<interface>>
        +onConnected()
        +onDisconnected()
        +onDataReceived(String)
    }

    %% Utils Package
    class TFLiteEmotionInterpreter {
        -static String TAG
        -static String MODEL_NAME
        -Interpreter interpreter
        +TFLiteEmotionInterpreter(Context)
        -loadModelFile(Context) MappedByteBuffer
        +predict(float, float, float, float, float, float, float) int
        +close()
    }

    class NotificationManager {
        -static String CHANNEL_ID
        -static String CHANNEL_NAME
        -static String CHANNEL_DESCRIPTION
        -static int NOTIFICATION_ID
        -Context context
        -android.app.NotificationManager notificationManager
        -Vibrator vibrator
        +NotificationManager(Context)
        -createNotificationChannel()
        +sendStressAlert(float, String)
        -vibrateDevice()
        +cancelStressAlert()
    }

    class HistoryStorage {
        <<Utility>>
        -static List~Entry~ history
        +static add(Entry)
        +static getHistory() List~Entry~
        +static clearHistory()
    }

    class Entry {
        +final long timestamp
        +final float bpm, temp, hrv, accX, accY, accZ, accMag
        +Entry(long, float, float, float, float, float, float, float)
    }

    %% Relationships
    HomeActivity ..|> BLECallback : implements
    HomeActivity --> BLEManager : uses
    HomeActivity --> TFLiteEmotionInterpreter : uses
    HomeActivity --> NotificationManager : uses
    HomeActivity --> HistoryStorage : uses

    HistoryActivity --> HistoryStorage : uses
    HistoryActivity --> TFLiteEmotionInterpreter : uses

    BLEManager --> BLECallback : notifies
    BLEManager ..> HomeActivity : callback

    HistoryStorage --> Entry : contains
    Entry --* HistoryStorage : composition

    NotificationManager --> HomeActivity : launches via Intent

    %% Android Framework Dependencies
    HomeActivity --|> AppCompatActivity : extends
    HistoryActivity --|> AppCompatActivity : extends
    NotificationActivity --|> AppCompatActivity : extends
    WelcomeActivity --|> AppCompatActivity : extends
    LoginActivity --|> AppCompatActivity : extends
    SignupActivity --|> AppCompatActivity : extends
    DevicePairingActivity --|> AppCompatActivity : extends
    ProfileActivity --|> AppCompatActivity : extends
    SettingsActivity --|> AppCompatActivity : extends
    ConsentFormActivity --|> AppCompatActivity : extends
```

## 2. Sequence Diagram - Stress Detection Flow

```mermaid
sequenceDiagram
    participant User
    participant HomeActivity
    participant BLEManager
    participant TFLiteInterpreter
    participant NotificationManager
    participant HistoryStorage

    User->>HomeActivity: Launch App
    HomeActivity->>BLEManager: getInstance()
    HomeActivity->>TFLiteInterpreter: new TFLiteInterpreter()
    HomeActivity->>NotificationManager: new NotificationManager()

    User->>HomeActivity: Connect Device
    HomeActivity->>BLEManager: connectToDevice()
    BLEManager-->>HomeActivity: onConnected()

    loop Real-time Data Processing
        BLEManager->>BLEManager: Receive BLE Data
        BLEManager-->>HomeActivity: onDataReceived(jsonData)
        HomeActivity->>HomeActivity: Parse JSON Data
        HomeActivity->>HistoryStorage: add(Entry)
        HomeActivity->>HomeActivity: Update UI Cards
        HomeActivity->>TFLiteInterpreter: predict(biometrics)
        TFLiteInterpreter-->>HomeActivity: prediction result

        alt High Stress Detected
            HomeActivity->>HomeActivity: updateStatusCard("high_stress")
            HomeActivity->>NotificationManager: sendStressAlert()
            NotificationManager->>User: System Notification
            HomeActivity->>HomeActivity: Save to SharedPreferences
        else Normal Stress
            HomeActivity->>HomeActivity: updateStatusCard("normal")
        end
    end
```

## 3. Activity Diagram - User Navigation Flow

```mermaid
flowchart TD
    A[Launch App] --> B[Welcome Activity]
    B --> C{User Registered?}
    C -->|No| D[Signup Activity]
    C -->|Yes| E[Login Activity]
    D --> E
    E --> F[Consent Form Activity]
    F --> G[Device Pairing Activity]
    G --> H{Device Connected?}
    H -->|No| I[Show Connect Button]
    H -->|Yes| J[Home Activity]
    I --> G

    J --> K[Bottom Navigation]
    K --> L[Home Screen]
    K --> M[History Screen]
    K --> N[Notifications Screen]
    K --> O[Profile Screen]
    K --> P[Settings Screen]

    L --> Q{Stress Detected?}
    Q -->|Yes| R[Send Notification]
    Q -->|No| S[Continue Monitoring]
    R --> S
    S --> L

    M --> T[View History]
    T --> U[Clear History?]
    U -->|Yes| V[Confirm Dialog]
    V --> W[Clear Data]
    W --> M
    U -->|No| M

    N --> X[View Notifications]
    X --> Y[Clear Notifications?]
    Y -->|Yes| Z[Confirm Dialog]
    Z --> AA[Clear Notifications]
    AA --> N
    Y -->|No| N
```

## 4. Component Diagram - System Architecture

```mermaid
graph TB
    subgraph "AuraSense Android App"
        subgraph "Presentation Layer"
            UI[Activities & Fragments]
            NAV[Bottom Navigation]
        end

        subgraph "Business Logic Layer"
            BLE[BLE Manager]
            AI[TensorFlow Lite Interpreter]
            NOTIF[Notification Manager]
        end

        subgraph "Data Layer"
            HIST[History Storage]
            PREFS[SharedPreferences]
            ASSETS[TensorFlow Model Assets]
        end
    end

    subgraph "External Systems"
        SENSOR[BLE Sensor Device]
        ANDROID[Android System Services]
        TFLITE[TensorFlow Lite Runtime]
    end

    UI --> BLE
    UI --> AI
    UI --> NOTIF
    UI --> HIST
    UI --> PREFS

    BLE --> SENSOR
    AI --> ASSETS
    AI --> TFLITE
    NOTIF --> ANDROID

    NAV --> UI
```

## 5. State Diagram - BLE Connection States

```mermaid
stateDiagram-v2
    [*] --> Disconnected

    Disconnected --> Connecting : connectToDevice()
    Connecting --> Connected : onConnectionStateChange(CONNECTED)
    Connecting --> Disconnected : onConnectionStateChange(DISCONNECTED)

    Connected --> DiscoveringServices : discoverServices()
    DiscoveringServices --> ServicesDiscovered : onServicesDiscovered()
    DiscoveringServices --> Disconnected : Service Discovery Failed

    ServicesDiscovered --> NotificationsEnabled : Enable Notifications
    NotificationsEnabled --> DataReceiving : Notifications Enabled

    DataReceiving --> DataReceiving : onCharacteristicChanged()
    DataReceiving --> Disconnected : Connection Lost
    DataReceiving --> Disconnected : disconnect()

    Connected --> Disconnected : disconnect()
    NotificationsEnabled --> Disconnected : disconnect()

    Disconnected --> [*] : App Closed
```

## 6. Use Case Diagram - User Interactions

```mermaid
graph LR
    subgraph "AuraSense System"
        UC1[Monitor Stress Levels]
        UC2[View Historical Data]
        UC3[Receive Stress Alerts]
        UC4[Connect BLE Device]
        UC5[Manage Notifications]
        UC6[Clear History]
        UC7[User Authentication]
    end

    subgraph "Actors"
        USER[User]
        SENSOR[BLE Sensor]
        SYSTEM[Android System]
    end

    USER --> UC1
    USER --> UC2
    USER --> UC3
    USER --> UC4
    USER --> UC5
    USER --> UC6
    USER --> UC7

    SENSOR --> UC1
    SENSOR --> UC4

    SYSTEM --> UC3
    SYSTEM --> UC5

    UC1 --> UC3 : triggers
    UC1 --> UC2 : stores data for
```

## 7. Package Diagram - Code Organization

```mermaid
graph TB
    subgraph "com.example.aurasense"
        subgraph "activities"
            ACT[HomeActivity<br/>HistoryActivity<br/>NotificationActivity<br/>WelcomeActivity<br/>LoginActivity<br/>SignupActivity<br/>DevicePairingActivity<br/>ProfileActivity<br/>SettingsActivity<br/>ConsentFormActivity]
        end

        subgraph "ble"
            BLE[BLEManager<br/>BLECallback]
        end

        subgraph "utils"
            UTILS[TFLiteEmotionInterpreter<br/>NotificationManager<br/>HistoryStorage<br/>HistoryStorage.Entry]
        end
    end

    subgraph "Android Framework"
        ANDROID[AppCompatActivity<br/>Context<br/>SharedPreferences<br/>BluetoothAdapter<br/>NotificationManager]
    end

    subgraph "External Libraries"
        TFLITE[TensorFlow Lite<br/>Interpreter]
        MATERIAL[Material Design<br/>BottomNavigationView]
    end

    ACT --> BLE
    ACT --> UTILS
    ACT --> ANDROID
    ACT --> MATERIAL

    BLE --> ANDROID
    UTILS --> ANDROID
    UTILS --> TFLITE
```

## Key Design Patterns Used

### 1. Singleton Pattern

- **BLEManager**: Ensures single instance for BLE connection management

### 2. Observer Pattern

- **BLECallback Interface**: Allows activities to observe BLE events
- **Bottom Navigation**: Activities observe navigation events

### 3. Strategy Pattern

- **TFLiteEmotionInterpreter**: Encapsulates stress prediction algorithm

### 4. Factory Pattern

- **NotificationManager**: Creates different types of notifications

### 5. Repository Pattern

- **HistoryStorage**: Provides abstraction for data storage operations

## Architecture Highlights

### 📱 **Presentation Layer**

- Multiple Activities with consistent Bottom Navigation
- Material Design components and custom styling
- Real-time UI updates based on sensor data

### 🧠 **Business Logic Layer**

- BLE communication management
- AI-powered stress detection using TensorFlow Lite
- Smart notification system with user preferences

### 💾 **Data Layer**

- In-memory history storage with static collections
- SharedPreferences for notification history
- TensorFlow Lite model assets

### 🔗 **Integration Layer**

- Android Bluetooth APIs for sensor communication
- Android Notification System for alerts
- TensorFlow Lite runtime for AI inference

This architecture provides a clean separation of concerns, making the codebase maintainable and extensible for future enhancements.
