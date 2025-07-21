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

// ========== I2C Pin Setup ==========
#define SDA_PIN 21
#define SCL_PIN 22

// ========== MLX90614 ==========
#define MLX90614_ADDRESS 0x5A
#define MLX90614_TOBJ 0x07

// ========== BLE UUIDs ==========
#define SERVICE_UUID        "a0e6fc00-df5e-11ee-a506-0050569c1234"
#define CHARACTERISTIC_UUID "a0e6fc01-df5e-11ee-a506-0050569c1234"

// ========== Sensor Objects ==========
MAX30105 particleSensor;
Adafruit_MPU6050 mpu;
BLECharacteristic* pCharacteristic;

// ========== HR Variables ==========
const byte RATE_SIZE = 4;
byte rates[RATE_SIZE];
byte rateSpot = 0;
long lastBeat = 0;
float beatsPerMinute = 0;
int beatAvg = 0;
long irValue = 0;

// ========== Temp Variables ==========
float objectTemp = 0.0;

// ========== BLE Setup ==========
void setupBLE() {
  BLEDevice::init("ESP32_EmotionBand");
  BLEServer* pServer = BLEDevice::createServer();
  BLEService* pService = pServer->createService(SERVICE_UUID);
  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_READ |
                      BLECharacteristic::PROPERTY_NOTIFY
                    );
  pCharacteristic->addDescriptor(new BLE2902());
  pService->start();
  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->start();
  Serial.println("BLE is advertising.");
}

// ========== MLX90614 Temp Read ==========
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

void setup() {
  Serial.begin(115200);
  Wire.begin(SDA_PIN, SCL_PIN);
  delay(500);

  // HR Sensor
  if (!particleSensor.begin(Wire, I2C_SPEED_STANDARD)) {
    Serial.println("MAX30102 not found!");
    while (1);
  }
  particleSensor.setup();
  particleSensor.setPulseAmplitudeRed(0x3F);
  particleSensor.setPulseAmplitudeGreen(0);

  // MPU6050
  if (!mpu.begin()) {
    Serial.println("MPU6050 not found!");
    while (1);
  }
  mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
  mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);

  setupBLE();
  Serial.println("Sensors ready. Waiting for data...");
}

void loop() {
  irValue = particleSensor.getIR();
  static unsigned long rrIntervals[RATE_SIZE];
  static int rrIndex = 0;
  static float hrv = 0;

  if (checkForBeat(irValue)) {
    long now = millis();
    long delta = now - lastBeat;

    if (lastBeat != 0 && delta > 300 && delta < 2000) {
      rrIntervals[rrIndex] = delta;
      rrIndex = (rrIndex + 1) % RATE_SIZE;

      // Compute HRV = SDNN (std dev of RR intervals)
      float mean = 0;
      for (int i = 0; i < RATE_SIZE; i++) mean += rrIntervals[i];
      mean /= RATE_SIZE;

      float variance = 0;
      for (int i = 0; i < RATE_SIZE; i++) variance += pow(rrIntervals[i] - mean, 2);
      variance /= RATE_SIZE;
      hrv = sqrt(variance);
    }

    lastBeat = now;
    beatsPerMinute = 60 / (delta / 1000.0);
    if (beatsPerMinute > 20 && beatsPerMinute < 255) {
      rates[rateSpot++] = (byte)beatsPerMinute;
      rateSpot %= RATE_SIZE;
      beatAvg = 0;
      for (byte x = 0; x < RATE_SIZE; x++) beatAvg += rates[x];
      beatAvg /= RATE_SIZE;
    }
  }

  // Send BLE data every 2 sec
  static unsigned long lastSend = 0;
  if (millis() - lastSend >= 2000) {
    sensors_event_t acc, gyro, tempEvent;
    mpu.getEvent(&acc, &gyro, &tempEvent);
    objectTemp = readMLX90614(MLX90614_TOBJ);

    float accX = acc.acceleration.x;
    float accY = acc.acceleration.y;
    float accZ = acc.acceleration.z;
    float accMag = sqrt(accX * accX + accY * accY + accZ * accZ);

    StaticJsonDocument<512> doc;
    doc["bpm"] = beatAvg;
    doc["hrv"] = hrv;
    doc["temp"] = objectTemp;
    doc["acc_x"] = accX;
    doc["acc_y"] = accY;
    doc["acc_z"] = accZ;
    doc["acc_mag"] = accMag;

    String jsonString;
    serializeJson(doc, jsonString);
    pCharacteristic->setValue(jsonString.c_str());
    pCharacteristic->notify();

    Serial.print("Sent BLE payload: ");
    Serial.println(jsonString);

    lastSend = millis();
  }

  delay(10);
}
