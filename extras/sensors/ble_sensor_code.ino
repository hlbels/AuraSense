#include <Arduino.h>
#include <Wire.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include <ArduinoJson.h>
#include "MAX30105.h"
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

void loop() {
  static unsigned long lastSend = 0;
  if (millis() - lastSend >= 2000) {
    // Read sensors
    sensors_event_t acc, gyro, tempEvent;
    mpu.getEvent(&acc, &gyro, &tempEvent);
    float objectTemp = readMLX90614(MLX90614_TOBJ);
    float accX = acc.acceleration.x;
    float accY = acc.acceleration.y;
    float accZ = acc.acceleration.z;

    // Use raw IR as BVP-like signal
    float bvp = (float)particleSensor.getIR();

    // Format JSON
    StaticJsonDocument<256> doc;
    doc["acc_x"] = accX;
    doc["acc_y"] = accY;
    doc["acc_z"] = accZ;
    doc["temp"] = objectTemp;
    doc["bvp"] = bvp;

    // Send over BLE
    String jsonString;
    serializeJson(doc, jsonString);
    pCharacteristic->setValue(jsonString.c_str());
    pCharacteristic->notify();

    Serial.print("Sent: ");
    Serial.println(jsonString);

    lastSend = millis();
  }

  delay(10);
}
