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
BLEServer* pServer;

// ===== Heart Rate & HRV Variables =====
const byte RATE_SIZE = 16;
unsigned long rrIntervals[RATE_SIZE];
int rrIndex = 0;
float hrv = 0;
float bpmAvg = 0;
long lastBeat = 0;
bool fingerDetected = false;

// ===== BLE Connection Timing =====
unsigned long connectionTime = 0;

class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) override {
    Serial.println("Central connected");
    connectionTime = millis(); // start delay timer
  }

  void onDisconnect(BLEServer* pServer) override {
    Serial.println("Central disconnected");
    connectionTime = 0;
  }
};

// ===== BLE Setup =====
void setupBLE() {
  BLEDevice::init("ESP32_EmotionBand");
  BLEDevice::setMTU(256);
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService* pService = pServer->createService(SERVICE_UUID);

  pCharacteristic = pService->createCharacteristic(
    CHARACTERISTIC_UUID,
    BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
  );

  BLE2902* descriptor = new BLE2902();
  descriptor->setNotifications(true);
  pCharacteristic->addDescriptor(descriptor);

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
  Serial.println("Sensors initialized");
}

// ===== Loop =====
void loop() {
  static unsigned long lastSend = 0;

  // Wait 3 seconds after connection before sending data
  if (pServer->getConnectedCount() > 0 && (millis() - connectionTime < 3000)) {
    delay(10);
    return;
  }

  uint32_t irValue = particleSensor.getIR();
  fingerDetected = irValue > 50000;

  if (fingerDetected && checkForBeat(irValue)) {
    long now = millis();
    long delta = now - lastBeat;

    if (delta >= 300 && delta <= 2000) {
      rrIntervals[rrIndex] = delta;
      rrIndex = (rrIndex + 1) % RATE_SIZE;

      float meanRR = 0;
      for (int i = 0; i < RATE_SIZE; i++) meanRR += rrIntervals[i];
      meanRR /= RATE_SIZE;

      float variance = 0;
      for (int i = 0; i < RATE_SIZE; i++) {
        variance += pow(rrIntervals[i] - meanRR, 2);
      }
      hrv = sqrt(variance / RATE_SIZE);

      if (meanRR > 0) {
        bpmAvg = 60000.0 / meanRR;
      }
    }

    lastBeat = millis();
  }

  if (millis() - lastSend >= 2000) {
    sensors_event_t acc, gyro, tempEvent;
    mpu.getEvent(&acc, &gyro, &tempEvent);
    float objectTemp = readMLX90614(MLX90614_TOBJ);
    float bvp = irValue / 100000.0f;

    StaticJsonDocument<256> doc;
    doc["acc_x"] = round(acc.acceleration.x * 100) / 100.0;
    doc["acc_y"] = round(acc.acceleration.y * 100) / 100.0;
    doc["acc_z"] = round(acc.acceleration.z * 100) / 100.0;
    doc["temp"]  = round(objectTemp * 100) / 100.0;
    doc["bvp"]   = round(bvp * 1000) / 1000.0;
    doc["bpm"]   = round(bpmAvg);
    doc["hrv"]   = round(hrv);
    doc["finger"] = fingerDetected ? 1 : 0;

    String jsonString;
    serializeJson(doc, jsonString);
    pCharacteristic->setValue(jsonString.c_str());

    if (pServer->getConnectedCount() > 0) {
      pCharacteristic->notify();
      Serial.print("Sent: ");
      Serial.println(jsonString);
    }

    lastSend = millis();
    delay(20);
  }

  delay(10);
}