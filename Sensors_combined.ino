#include <Wire.h>
#include "MAX30105.h"
#include "heartRate.h"
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>

// I2C setup
#define SDA_PIN 21
#define SCL_PIN 22
#define MLX90614_ADDRESS 0x5A
#define MLX90614_TOBJ 0x07
#define MLX90614_TAMB 0x06

// Sensor objects
MAX30105 particleSensor;
Adafruit_MPU6050 mpu;

// Heart rate variables
const byte RATE_SIZE = 4;
byte rates[RATE_SIZE];
byte rateSpot = 0;
long lastBeat = 0;
float beatsPerMinute = 0;
int beatAvg = 0;
long irValue = 0;

// Temperature
float objectTemp = 0.0;
float ambientTemp = 0.0;

// Display timing
unsigned long lastDisplay = 0;

void setup() {
  Serial.begin(115200);
  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(100000);
  delay(500);

  // Initialize MAX30102
  Serial.println("Initializing MAX30102...");
  if (!particleSensor.begin(Wire, I2C_SPEED_STANDARD)) {
    Serial.println("MAX30102 not found. Check wiring.");
    while (1);
  }
  particleSensor.setup();
  particleSensor.setPulseAmplitudeRed(0x3F);
  particleSensor.setPulseAmplitudeGreen(0);

  // Initialize MPU6050
  Serial.println("Initializing MPU6050...");
  if (!mpu.begin()) {
    Serial.println("MPU6050 not found. Check wiring.");
    while (1);
  }
  mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
  mpu.setGyroRange(MPU6050_RANGE_500_DEG);
  mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);

  Serial.println("All sensors initialized. Place finger on MAX30102.");
}

void loop() {
  irValue = particleSensor.getIR();

  // Detect heartbeat
  if (checkForBeat(irValue)) {
    long delta = millis() - lastBeat;
    lastBeat = millis();
    beatsPerMinute = 60 / (delta / 1000.0);
    if (beatsPerMinute > 20 && beatsPerMinute < 255) {
      rates[rateSpot++] = (byte)beatsPerMinute;
      rateSpot %= RATE_SIZE;
      beatAvg = 0;
      for (byte x = 0; x < RATE_SIZE; x++) beatAvg += rates[x];
      beatAvg /= RATE_SIZE;
    }
  }

  // Every 2s, read temperature + motion + print all
  if (millis() - lastDisplay >= 2000) {
    // MLX90614
    ambientTemp = readMLX90614(MLX90614_TAMB);
    objectTemp = readMLX90614(MLX90614_TOBJ);

    // MPU6050 motion
    sensors_event_t acc, gyro, tempEvent;
    mpu.getEvent(&acc, &gyro, &tempEvent);

    Serial.println("\n=== SENSOR READINGS ===");
    Serial.print("IR = ");
    Serial.print(irValue);
    if (irValue < 50000) Serial.print(" (No finger)");

    Serial.print("\nBPM = ");
    Serial.print(beatsPerMinute, 1);
    Serial.print(" | Avg BPM = ");
    Serial.println(beatAvg);

    Serial.print(" Ambient Temp: ");
    Serial.print(ambientTemp, 1);
    Serial.print(" °C | Object Temp: ");
    Serial.print(objectTemp, 1);
    Serial.println(" °C");

    Serial.print("Accel (m/s^2) X: ");
    Serial.print(acc.acceleration.x, 2);
    Serial.print(" Y: ");
    Serial.print(acc.acceleration.y, 2);
    Serial.print(" Z: ");
    Serial.println(acc.acceleration.z, 2);

    Serial.print("Gyro (rad/s) X: ");
    Serial.print(gyro.gyro.x, 2);
    Serial.print(" Y: ");
    Serial.print(gyro.gyro.y, 2);
    Serial.print(" Z: ");
    Serial.println(gyro.gyro.z, 2);

    Serial.println("------------------------");

    lastDisplay = millis();
  }

  delay(20); // Keep sampling responsive
}

float readMLX90614(byte reg) {
  Wire.beginTransmission(MLX90614_ADDRESS);
  Wire.write(reg);
  byte error = Wire.endTransmission(false);
  if (error != 0) return -999;

  Wire.requestFrom(MLX90614_ADDRESS, (uint8_t)3);
  unsigned long timeout = millis();
  while (Wire.available() < 3 && millis() - timeout < 100);

  if (Wire.available() >= 3) {
    byte low = Wire.read();
    byte high = Wire.read();
    Wire.read(); // PEC
    uint16_t raw = (high << 8) | low;
    return (raw * 0.02) - 273.15;
  }

  return -999;
}
