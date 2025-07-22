# Design Document

## Overview

The redesigned AuraSense home screen will transform the current single-card layout into a modern, card-based dashboard that prioritizes visual hierarchy, accessibility, and user experience. The design follows Material Design 3 principles with a focus on health and wellness applications, using a clean color palette that conveys trust and calm while ensuring critical stress alerts remain prominent.

## Architecture

### UI Architecture
- **MVVM Pattern**: Separate UI logic from business logic using ViewModel
- **Data Binding**: Reduce findViewById calls and improve performance
- **Modular Components**: Break down the monolithic layout into reusable components
- **State Management**: Clear separation between connected, disconnected, and error states

### Layout Structure
```
HomeActivity
├── AppBarLayout (Custom header with status indicator)
├── ScrollView
│   ├── StatusSummaryCard (Primary stress status)
│   ├── BiometricDataGrid (Heart rate, temperature, motion)
│   ├── ConnectionCard (Device connection status/actions)
│   └── DebugSection (Collapsible, dev-only)
└── BottomNavigationView
```

## Components and Interfaces

### 1. StatusSummaryCard Component
**Purpose**: Primary stress level indicator with visual prominence
- **Normal State**: Green background, checkmark icon, "All Good" message
- **High Stress State**: Red background, warning icon, "High Stress Detected" message
- **Disconnected State**: Gray background, disconnected icon, "Device Disconnected" message
- **Error State**: Orange background, error icon, "Unable to Analyze" message

**Visual Design**:
- Large, rounded card with colored background
- Icon + text layout with proper spacing
- Subtle shadow for depth
- Smooth color transitions between states

### 2. BiometricDataGrid Component
**Purpose**: Display individual sensor readings in organized cards

**Layout**: 2x2 grid on larger screens, vertical stack on smaller screens
- **Heart Rate Card**: BPM with heart icon, trend indicator
- **Temperature Card**: Celsius with thermometer icon, normal range indicator
- **Motion Card**: Activity level with motion icon, simplified from raw accelerometer
- **HRV Card**: Heart rate variability with pulse icon (if available)

**Card Design**:
- White background with subtle border
- Icon + value + unit layout
- Consistent padding and typography
- Loading states with skeleton animations

### 3. ConnectionCard Component
**Purpose**: Device connection management and status

**Connected State**:
- Green accent with checkmark
- Device name and connection time
- "Disconnect" button (secondary action)

**Disconnected State**:
- Primary "Connect Device" button
- Last connected time (if available)
- Connection troubleshooting link

### 4. DebugSection Component
**Purpose**: Developer tools and raw data display

**Features**:
- Collapsible section with "Debug Info" header
- JSON formatter with syntax highlighting
- Copy to clipboard functionality
- Hidden in production builds

### 5. Enhanced Navigation
**Improvements**:
- Active state indicators
- Smooth transitions between screens
- Badge notifications for alerts

## Data Models

### HomeScreenState
```kotlin
data class HomeScreenState(
    val connectionStatus: ConnectionStatus,
    val stressLevel: StressLevel,
    val biometricData: BiometricData,
    val isDebugMode: Boolean,
    val lastUpdated: Long
)

enum class ConnectionStatus {
    CONNECTED, DISCONNECTED, CONNECTING, ERROR
}

enum class StressLevel {
    NORMAL, HIGH, UNKNOWN, ERROR
}

data class BiometricData(
    val heartRate: Float?,
    val temperature: Float?,
    val motionLevel: MotionLevel,
    val hrv: Float?,
    val rawAccelerometer: AccelerometerData?
)

enum class MotionLevel {
    STATIONARY, LIGHT, MODERATE, ACTIVE
}
```

### UI State Management
- Use StateFlow for reactive UI updates
- Implement proper loading states
- Handle error states gracefully
- Maintain state during configuration changes

## Error Handling

### Connection Errors
- **Bluetooth Disabled**: Show enable Bluetooth prompt
- **Device Not Found**: Provide pairing instructions
- **Connection Lost**: Auto-reconnect with user notification
- **Permission Denied**: Guide user to settings

### Data Processing Errors
- **Invalid JSON**: Show "Data Error" state without crashing
- **Model Prediction Failure**: Fallback to basic heart rate analysis
- **Sensor Malfunction**: Individual card error states

### UI Error States
- **Network Issues**: Offline mode indicators
- **Low Battery**: Device battery warnings
- **App Crashes**: Graceful recovery with error reporting

## Testing Strategy

### Unit Tests
- ViewModel logic testing
- Data parsing and validation
- State management verification
- Error handling scenarios

### UI Tests
- Card layout rendering
- State transition animations
- Accessibility compliance
- Different screen sizes

### Integration Tests
- BLE connection flow
- Data flow from sensor to UI
- Navigation between screens
- Background/foreground transitions

### Accessibility Tests
- Screen reader compatibility
- Color contrast validation
- Touch target size verification
- Keyboard navigation support

## Visual Design Specifications

### Color Palette
```xml
<!-- Primary Colors -->
<color name="primary_teal">#009688</color>
<color name="primary_teal_dark">#00695C</color>
<color name="primary_teal_light">#4DB6AC</color>

<!-- Status Colors -->
<color name="status_normal">#4CAF50</color>
<color name="status_warning">#FF9800</color>
<color name="status_error">#F44336</color>
<color name="status_disconnected">#9E9E9E</color>

<!-- Background Colors -->
<color name="background_primary">#F9FAFB</color>
<color name="card_background">#FFFFFF</color>
<color name="card_border">#E0E0E0</color>
```

### Typography
- **Headers**: Roboto Medium, 24sp
- **Card Titles**: Roboto Medium, 18sp
- **Body Text**: Roboto Regular, 16sp
- **Captions**: Roboto Regular, 14sp
- **Debug Text**: Roboto Mono, 12sp

### Spacing System
- **Extra Small**: 4dp
- **Small**: 8dp
- **Medium**: 16dp
- **Large**: 24dp
- **Extra Large**: 32dp

### Card Design
- **Corner Radius**: 12dp
- **Elevation**: 2dp (normal), 4dp (elevated)
- **Padding**: 16dp internal
- **Margin**: 8dp between cards

## Implementation Approach

### Phase 1: Core Restructuring
1. Create new layout with card-based structure
2. Implement basic state management
3. Add proper color theming
4. Ensure existing functionality works

### Phase 2: Enhanced Components
1. Implement StatusSummaryCard with animations
2. Create BiometricDataGrid with proper formatting
3. Add ConnectionCard with improved UX
4. Implement collapsible DebugSection

### Phase 3: Polish and Accessibility
1. Add smooth transitions and animations
2. Implement accessibility features
3. Add loading states and error handling
4. Performance optimization

### Migration Strategy
- Maintain backward compatibility during transition
- Feature flags for gradual rollout
- A/B testing for user preference validation
- Rollback plan if issues arise