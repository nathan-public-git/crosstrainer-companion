# FTMS.md

# CrossTrainer Companion
## Bluetooth FTMS Reference

---

# Purpose

This document is the technical reference for all Bluetooth Low Energy
(BLE) communication used by CrossTrainer Companion.

It serves three purposes:

1. Development reference
2. Packet decoding reference
3. Device compatibility notes

The application should always prefer Bluetooth SIG standard behaviour.

Manufacturer-specific workarounds should only be implemented when
absolutely necessary.

---

# References

Bluetooth SIG

Fitness Machine Service (FTMS)

Service UUID

0x1826

Heart Rate Service

0x180D

Cycling Speed and Cadence Service

0x1816

Device Information Service

0x180A

Battery Service

0x180F

---

# Supported BLE Devices

## Required

Fitness Machine (FTMS)

## Optional

Heart Rate Monitor

Cadence Sensor

Power Meter

Speed Sensor

Battery Service

---

# Current Test Hardware

Elliptical

Manufacturer

SOLE

Model

E95

Display

13.3" Touchscreen

Bluetooth

FTMS

Heart Rate

Polar H10

Android

(Android version)

---

# FTMS Services

Primary Service

UUID

0x1826

Observed Characteristics

| UUID | Name | Purpose |
|------|------|---------|
|2ACC|Fitness Machine Feature|Capabilities|
|2AD9|Fitness Machine Control Point|Commands|
|2ADA|Fitness Machine Status|Machine Events|
|2AD3|Training Status|Workout State|
|2AD5|Supported Incline Range|Capabilities|
|2AD6|Supported Resistance Range|Capabilities|
|2ACE|Cross Trainer Data|Primary Workout Data|
|2AD2|Indoor Bike Data|Compatibility Data|

---

# Primary Characteristic

Cross Trainer Data

UUID

0x2ACE

Properties

READ

NOTIFY

Primary notification source.

This characteristic is expected to provide:

- Stride Rate
- Resistance
- Speed
- Distance
- Energy
- Elapsed Time
- Power
- Heart Rate (if available)

Exact fields depend on FTMS flags.

---

# Secondary Characteristic

Indoor Bike Data

UUID

0x2AD2

Purpose

Some manufacturers transmit equivalent data using this
characteristic for compatibility with cycling applications.

If Cross Trainer Data is unavailable or incomplete,
Indoor Bike Data may be used.

Cross Trainer Data should always take precedence.

---

# Heart Rate

Preferred Device

Polar H10

Service

0x180D

Characteristic

0x2A37

Primary Metric

Heart Rate (BPM)

The Polar H10 is considered the authoritative source
for heart rate whenever connected.

Machine-reported heart rate should only be used as a fallback.

---

# Device Discovery

Scanner filters:

Fitness Machine Service

0x1826

Heart Rate Service

0x180D

Cycling Speed & Cadence

0x1816

Battery Service

0x180F

---

# Connection Priority

1.
Fitness Machine

2.
Polar Heart Rate

3.
Other BLE Sensors

The dashboard should continue functioning even if one
device disconnects.

---

# Workout Model

Every BLE packet updates a single shared model.

WorkoutMetrics

Contains

Heart Rate

Stride Rate

Power

Resistance

Speed

Distance

Calories

Elapsed Time

Average Heart Rate

Maximum Heart Rate

Connection Status

Timestamp

UI observes only this model.

BLE code never directly updates the UI.

---

# Packet Capture

When investigating new hardware, capture:

Manufacturer

Model

Firmware Version

Android Version

BLE Service List

Characteristic List

Example Notification Packets

Decoded Values

Observed Behaviour

---

# Packet Samples

## Sole E95

Cross Trainer Data

Raw

TODO

Decoded

TODO

---

# Compatibility Notes

## SOLE E95

Status

Verified

Observations

Supports standard FTMS.

Advertises

0x1826

Provides

Cross Trainer Data

Indoor Bike Data

No proprietary services required.

---

# Manufacturer Compatibility

| Manufacturer | Model | Status | Notes |
|--------------|-------|--------|------|
|SOLE|E95|Verified|Standard FTMS|
|SOLE|Unknown|Untested||
|Bowflex||Untested||
|NordicTrack||Untested||
|Spirit||Untested||
|Matrix||Untested||
|Life Fitness||Untested||
|Schwinn||Untested||

---

# Logging

Development builds should log

Device Name

Device Address

Connected Services

Characteristic UUIDs

Raw Notification Packets

Decoded Packets

Packet Frequency

Reconnect Events

Errors

Release builds should disable verbose BLE logging.

---

# Future Extensions

Supported sensors

Foot Pods

Cycling Power

Cycling Cadence

Running Speed

Blood Pressure

Pulse Oximeter

Body Composition

Future support should prefer Bluetooth SIG standard services.

---

# Design Philosophy

CrossTrainer Companion is an FTMS client.

The application should support any standards-compliant
fitness machine without modification.

Where manufacturer-specific behaviour is required,
it should be isolated into a compatibility layer
rather than affecting the core BLE implementation.

Compatibility code should remain the exception,
not the rule.