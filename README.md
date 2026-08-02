# Crosstrainer Companion

## Overview

Crosstrainer Companion is an Android application that connects to a Sole E95 elliptical trainer and a Polar H10 heart rate monitor using Bluetooth Low Energy (BLE).

The objective is to provide a large, easy-to-read workout dashboard that can be viewed while exercising.

This application intentionally avoids subscriptions, cloud accounts, advertisements, and unnecessary complexity.

The primary design goals are:

- Large, readable display
- Fast startup
- Extremely reliable BLE connections
- Low battery usage
- Offline operation
- Open architecture
- Future expansion

---

# Project Goals

Current commercial applications do not meet the following requirements:

- Simultaneous display of Polar H10 heart rate and Sole E95 workout metrics.
- Large dashboard designed for viewing from approximately 1�2 metres away.
- User-selectable metrics.
- Workout recording.
- No cloud dependency.
- No subscription.
- No advertisements.

This project aims to solve those problems.

---

# Target Hardware

## Elliptical

Sole E95

Current touchscreen model

Bluetooth FTMS supported

Confirmed BLE services:

Fitness Machine (0x1826)

Characteristics observed:

- Fitness Machine Feature (0x2ACC)
- Fitness Machine Control Point (0x2AD9)
- Fitness Machine Status (0x2ADA)
- Training Status (0x2AD3)
- Supported Incline Range (0x2AD5)
- Supported Resistance Level Range (0x2AD6)
- Cross Trainer Data (0x2ACE)
- Indoor Bike Data (0x2AD2)

Primary workout data source:

Cross Trainer Data (0x2ACE)

---

## Heart Rate

Polar H10

Bluetooth Heart Rate Service

UUID:

0x180D

---

# Minimum Supported Android Version

Android 12+

Target SDK:

Latest Stable

---

# Technology Stack

Language

Kotlin

UI

Jetpack Compose

Architecture

MVVM

BLE

Android Bluetooth LE APIs

Persistence

Room Database

Dependency Injection

Hilt (optional)

Navigation

Jetpack Navigation

Charts

MPAndroidChart
(or Compose equivalent)

---

# Project Architecture

app/

    ble/

        FtmsManager

        PolarManager

        DeviceScanner

        PacketParser

    model/

        WorkoutMetrics

    repository/

    ui/

        dashboard/

        settings/

        workout/

    storage/

    util/

---

# BLE Devices

## Sole E95

Connect to:

Fitness Machine Service

UUID:

0x1826

Subscribe to:

Cross Trainer Data

UUID:

0x2ACE

Optional:

Indoor Bike Data

UUID:

0x2AD2

Read:

Machine Feature

Machine Status

Training Status

---

## Polar H10

Connect to

Heart Rate Service

UUID:

0x180D

Subscribe

Heart Rate Measurement

UUID:

0x2A37

---

# Dashboard

Large text.

Landscape orientation.

Minimal distractions.

Primary metrics:

Heart Rate

Stride Rate (RPM)

Power (Watts)

Resistance Level

Elapsed Time

Calories

Distance

Speed

Average HR

Maximum HR

Workout Zone

Connection Status

Battery (if available)

---

# Future Dashboard

May include:

Gauge style heart rate

Colour coded HR zones

Split timer

Trend graph

Lap timer

Workout notes

Dark mode

Always-on display

Tablet support

---

# Workout Recording

Store locally.

Future export formats:

CSV

FIT

JSON

GPX (optional)

---

# Planned Features

## Phase 1

BLE scanner

Connect to Sole

Read FTMS

Display live metrics

---

## Phase 2

Connect Polar H10

Merge workout data

---

## Phase 3

Dashboard polishing

Settings

Large fonts

Landscape mode

---

## Phase 4

Workout recording

History

Graphs

Export

---

## Phase 5

Cloud backup (optional)

Garmin export

Strava export

Health Connect

---

# Coding Standards

Use Kotlin.

Avoid unnecessary inheritance.

Prefer immutable data.

Small classes.

Small functions.

Document BLE packet formats.

No business logic inside Activities.

No global state.

Everything testable.

---

# Error Handling

Gracefully reconnect BLE devices.

Detect stale connections.

Show connection state.

Log packet parsing errors.

Never crash due to malformed packets.

---

# Performance Goals

Cold startup under 2 seconds.

Reconnect under 5 seconds.

UI updates at BLE notification speed.

Battery usage suitable for multi-hour workouts.

---

# Stretch Goals

Custom workout screens.

Voice prompts.

Heart rate zone alerts.

Cadence alerts.

Audio cues.

Multiple BLE device support.

Bike trainer support.

Treadmill FTMS support.

Garmin watch bridge.

Wear OS companion.

---

# Development Philosophy

The application should feel like dedicated fitness equipment rather than a generic Android app.

Every screen should prioritize usability while exercising.

If a feature makes the interface more complicated without providing meaningful benefit during a workout, it should be reconsidered.

Reliability is more important than feature count.