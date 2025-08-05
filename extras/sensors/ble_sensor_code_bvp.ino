#include <Arduino.h>
#include <Wire.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include <ArduinoJson.h>
#include "MAX30105.h"
#include "heartRate.h"
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>

// ===== I2C Setup =====
#define SDA_PIN 11
#define SCL_PIN 12

// ===== MLX90614 Temp Sensor =====
#define MLX90614_ADDRESS 0x5A
#define MLX90614_TOBJ 0x07

// ===== BLE UUIDs =====
#define SERVICE_UUID        "a0e6fc00-df5e-11ee-a506-0050569c1234"
#define CHARACTERISTIC_UUID "a0e6fc01-df5e-11ee-a506-0050569c1234"

// ===== Sensor Objects =====
MAX30105 particleSensor;
Adafruit_MPU6050 mpu;
BLECharacteristic* pCharacteristic;

// ===== Heart Rate & HRV Variables =====
const byte RATE_SIZE = 16;
byte rates[RATE_SIZE];
byte rateSpot = 0;
long lastBeat = 0;
float beatsPerMinute;
float beatAvg = 0;

unsigned long rrIntervals[RATE_SIZE];
int rrIndex = 0;
float hrv = 0;

// ===== BLE Setup =====
void setupBLE() {
  BLEDevice::init("ESP32_EmotionBand");
  BLEServer* pServer = BLEDevice::createServer();
  BLEService* pService = pServer->createService(SERVICE_UUID);
  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
                    );
  pCharacteristic->addDescriptor(new BLE2902());
  pService->start();
  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->start();
  Serial.println("BLE advertising started");
}

// ===== MLX90614 Temp Read =====
float readMLX90614(byte reg) {
  Wire.beginTransmission(MLX90614_ADDRESS);
  Wire.write(reg);
  if (Wire.endTransmission(false) != 0) return -999;

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

// ===== Setup =====
void setup() {
  Serial.begin(115200);
  Wire.begin(SDA_PIN, SCL_PIN);
  delay(500);

  if (!particleSensor.begin(Wire, I2C_SPEED_STANDARD)) {
    Serial.println("MAX30102 not found!");
    while (1);
  }
  particleSensor.setup();
  particleSensor.setPulseAmplitudeRed(0x3F);
  particleSensor.setPulseAmplitudeGreen(0);

  if (!mpu.begin()) {
    Serial.println("MPU6050 not found!");
    while (1);
  }
  mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
  mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);

  setupBLE();
  Serial.println("Sensors initialized.");
}

// ===== Loop =====
void loop() {
  static unsigned long lastSend = 0;
  uint32_t irValue = particleSensor.getIR();

  if (irValue > 50000) {
    if (checkForBeat(irValue)) {
      long now = millis();
      long delta = now - lastBeat;

      if (lastBeat != 0 && delta > 300 && delta < 2000) {
        rrIntervals[rrIndex] = delta;
        rrIndex = (rrIndex + 1) % RATE_SIZE;

        // Compute HRV = SDNN (Standard Deviation of RR intervals)
        float mean = 0;
        for (int i = 0; i < RATE_SIZE; i++) mean += rrIntervals[i];
        mean /= RATE_SIZE;

        float variance = 0;
        for (int i = 0; i < RATE_SIZE; i++) variance += pow(rrIntervals[i] - mean, 2);
        variance /= RATE_SIZE;
        hrv = sqrt(variance);  // HRV in ms
      }

      lastBeat = now;
      beatsPerMinute = 60.0 / (delta / 1000.0);

      if (beatsPerMinute < 255 && beatsPerMinute > 20) {
        rates[rateSpot++] = (byte)beatsPerMinute;
        rateSpot %= RATE_SIZE;

        float sum = 0;
        for (byte i = 0; i < RATE_SIZE; i++) {
          if (rates[i] > 0) sum += rates[i];
        }
        beatAvg = sum / RATE_SIZE;
      }
    }

    if (millis() - lastSend >= 2000) {
      sensors_event_t acc, gyro, tempEvent;
      mpu.getEvent(&acc, &gyro, &tempEvent);
      float objectTemp = readMLX90614(MLX90614_TOBJ);
      float bvp = irValue / 100000.0f;

      StaticJsonDocument<256> doc;
      doc["bpm"] = beatAvg;
      doc["hrv"] = hrv;
      doc["temp"] = objectTemp;
      doc["acc_x"] = acc.acceleration.x;
      doc["acc_y"] = acc.acceleration.y;
      doc["acc_z"] = acc.acceleration.z;
      doc["acc_mag"] = sqrt(acc.acceleration.x * acc.acceleration.x +
                            acc.acceleration.y * acc.acceleration.y +
                            acc.acceleration.z * acc.acceleration.z);
      doc["bvp"] = bvp;

      String jsonString;
      serializeJson(doc, jsonString);
      pCharacteristic->setValue(jsonString.c_str());
      pCharacteristic->notify();

      Serial.print("Sent: ");
      Serial.println(jsonString);

      lastSend = millis();
    }
  } else {
    Serial.println("No finger detected.");
    Serial.println(irValue);
  }

  delay(10); // 10ms loop delay
}
