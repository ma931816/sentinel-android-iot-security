<img width="1536" height="1024" alt="Architecture-diagram" src="https://github.com/user-attachments/assets/0889a791-5ebf-4a3a-8436-c7cb68767407" /># SENTINEL

### Secure Edge & IoT Asset Monitoring with AWS

SENTINEL is an IoT-based monitoring project that I built to explore how an edge device can securely collect and send real-time telemetry to AWS. The current prototype uses an Android device as the edge device, taking advantage of its built-in GPS, accelerometer, battery, and network sensors.

The application collects location, motion, battery level, network status, and heartbeat information and sends this telemetry to AWS IoT Core over MQTT using X.509 certificate-based authentication.

On the AWS side, the data is stored, monitored, and processed through different services. Amazon Location Service tracks device positions and evaluates them against a configured geographic boundary. When the device leaves an authorized area, an EXIT event is generated and routed through Amazon EventBridge to Amazon SNS, which sends a security notification by email.

Although the current implementation uses an Android device, the idea behind SENTINEL is broader than mobile-device tracking. The same architecture could be adapted to compatible IoT hardware attached to valuable equipment, machinery, vehicles, IT assets, or other physical assets that need remote location and status monitoring.

For example, an organization could attach an IoT edge device with GPS and appropriate sensors to an expensive piece of equipment. Telemetry could then be securely transmitted to the cloud, allowing authorized users to monitor its latest location and operational information remotely. A geofence could define where the equipment is expected to remain, while leaving that area could automatically generate a security alert.

The main goal of SENTINEL was therefore not simply to build a GPS-tracking Android application, but to understand the complete lifecycle of an IoT monitoring system — **edge data collection, secure communication, cloud processing, storage, location tracking, event-driven alerts, and operational monitoring.**

---

## Potential Use Cases

SENTINEL was developed as a prototype, but its architecture can represent several real-world asset-monitoring scenarios.

### Industrial Equipment Monitoring

A compatible IoT device could be installed on high-value machinery or portable industrial equipment. Location and other available sensor information could be periodically transmitted to AWS, giving an organization remote visibility into the asset.

Geofencing could also be used to identify when equipment leaves an approved site or operational area.

### IT and Data Center Assets

Organizations often operate valuable servers, networking equipment, backup systems, and other IT infrastructure.

For equipment where physical movement needs to be monitored, a dedicated SENTINEL-type edge module could report location or movement information. Additional sensors could also be integrated depending on the hardware and use case.

### Construction Equipment

Portable generators, specialized tools, machinery, and other high-value construction assets may move between different sites.

An IoT implementation based on the SENTINEL architecture could provide:

* Periodic location updates
* Movement information
* Geofence monitoring
* Historical telemetry
* Alerts when an asset leaves an authorized area

### Vehicle and Fleet Monitoring

The architecture could also be adapted to vehicles or other mobile assets.

GPS telemetry could be transmitted through AWS IoT Core while Amazon Location Service handles location tracking and geographic boundaries. Event-driven alerts could notify operators when a tracked asset enters or exits defined areas.

### Laboratory and Technical Equipment

Universities, research facilities, hospitals, engineering organizations, and technical laboratories may operate specialized equipment that is expensive or difficult to replace.

A compatible IoT monitoring device could provide additional visibility into the physical location and movement of selected equipment.

### Remote Asset Monitoring

Because device telemetry is sent to cloud infrastructure rather than being limited to a local network, the architecture is suitable for scenarios where an asset and the person responsible for monitoring it are in different locations.

Conceptually:

```text
Physical Asset / Equipment
          │
          ▼
GPS + Sensors + Edge Device
          │
          │ Secure MQTT / TLS
          ▼
      AWS IoT Core
          │
    ┌─────┼─────────┐
    ▼     ▼         ▼
 Device  DynamoDB  Amazon Location
 Shadow              │
                     ▼
                  Geofence
                     │
                 EXIT Event
                     ▼
                 EventBridge
                     │
                     ▼
                    SNS
                     │
                     ▼
              Remote Alert
```

This means the person responsible for an asset does not need to be physically near it to receive cloud-generated status and security events, provided the deployed edge device has suitable network connectivity.

---

## Current Prototype

The current SENTINEL implementation uses an **Android smartphone as the edge device**.

This provides a practical way to demonstrate the architecture without requiring dedicated IoT hardware because an Android device already provides:

* GPS
* Accelerometer
* Battery information
* Network connectivity
* Processing capability
* Internet connectivity

The Android application currently monitors:

* GPS latitude and longitude
* GPS accuracy
* Motion state
* Battery percentage
* Network status
* Device heartbeat
* Device online status

Telemetry can be sent manually for testing or automatically at five-second intervals.

The Android implementation demonstrates the edge layer of the system. A future version could replace the smartphone with dedicated IoT hardware while retaining a similar AWS cloud architecture.

---

## Architecture

![SENTINEL Architecture](<img width="1536" height="1024" alt="Architecture-diagram" src="https://github.com/user-attachments/assets/d7a9c88b-3b17-4a96-a686-3b40df3c1e83" />
)

The current implementation follows this flow:

```text
Android Edge Device
        │
        │ GPS / Motion / Battery / Network / Heartbeat
        ▼
SENTINEL Application
        │
        │ MQTT over TLS
        │ X.509 Authentication
        ▼
AWS IoT Core
        │
        ├──────────────► Device Shadow
        │
        ├──────────────► DynamoDB
        │
        └──────────────► Amazon Location Service
                                │
                                ▼
                         SentinelTracker
                                │
                                ▼
                           Geofencing
                                │
                           EXIT Event
                                ▼
                           EventBridge
                                │
                                ▼
                           Amazon SNS
                                │
                                ▼
                        Security Email Alert

AWS IoT Core ────────────────► CloudWatch
                                │
                                ├─ IoT Logs
                                ├─ Rule Execution
                                └─ Monitoring
```

---

## Telemetry Format

A telemetry message generated by the prototype looks similar to:

```json
{
  "deviceId": "SENTINEL-001",
  "timestamp": 0,
  "latitude": 0.0,
  "longitude": 0.0,
  "accuracy": 5.0,
  "motion": "MOVING",
  "battery": 80,
  "networkStatus": "ONLINE",
  "deviceStatus": "ONLINE"
}
```

Actual development/test location values have intentionally been omitted from this repository.

Telemetry is published to:

```text
sentinel/SENTINEL-001/telemetry
```

---

## Android Application

The Android prototype is written in Kotlin and uses Jetpack Compose for the interface.

The application displays the current state of the edge device and provides controls for starting GPS monitoring, connecting to AWS IoT Core, manually sending telemetry, and stopping automatic telemetry.

The main Android components include:

* `MainActivity.kt` — GPS monitoring, sensor collection, heartbeat, telemetry generation, and UI
* `AwsIotManager.kt` — AWS IoT connection and MQTT telemetry publishing

Google Play Services Location API is used for GPS updates, while Android's SensorManager provides accelerometer data.

---

## Secure MQTT Communication

The edge device connects to AWS IoT Core using MQTT over TLS.

The device is authenticated using an X.509 certificate and private key, allowing AWS IoT Core to verify its identity before accepting MQTT communication.

The implementation uses:

* MQTT
* TLS
* X.509 device certificates
* AWS IoT policies
* Device-specific client identification

The private key and device certificate used during development are intentionally excluded from this repository.

---

## AWS IoT Core

AWS IoT Core acts as the main communication layer between the Android edge device and the AWS infrastructure.

The device publishes telemetry to:

```text
sentinel/SENTINEL-001/telemetry
```

IoT Rules route incoming data to other AWS services.

Two primary rules are used:

### SentinelTelemetryToDynamoDB

Routes incoming telemetry to Amazon DynamoDB for storage.

### SentinelGPSLocationRule

Extracts location information from telemetry and sends position updates to Amazon Location Service.

This keeps the edge application focused on collecting and publishing telemetry while AWS handles cloud-side routing and processing.

---

## Device Shadow

AWS IoT Device Shadow is used to maintain device state in the cloud.

This provides a cloud-side representation of the SENTINEL device and allows its latest reported state to remain available independently of the live MQTT connection.

The Shadow functionality was included to explore device-state management as part of an IoT architecture.

---

## Telemetry Storage

Amazon DynamoDB stores telemetry received from the device.

The `SentinelTelemetry` table maintains records using the device identifier and timestamp, allowing multiple readings from the same device to be stored over time.

This provides historical telemetry that can be inspected independently of live MQTT communication.

---

## Location Tracking and Geofencing

GPS coordinates received from the Android device are forwarded from AWS IoT Core to Amazon Location Service.

The project uses:

```text
Tracker:
SentinelTracker

Geofence Collection:
SentinelGeofences
```

A geographic boundary represents an authorized monitoring area.

When the tracked device moves from inside the boundary to outside it, Amazon Location Service generates an `EXIT` event.

```text
Device GPS Position
        ↓
AWS IoT Core
        ↓
SentinelGPSLocationRule
        ↓
SentinelTracker
        ↓
Geofence Evaluation
        ↓
EXIT Event
```

This demonstrates how physical movement of an edge device can trigger a cloud-side security workflow.

---

## Security Alerts

Geofence events are routed through Amazon EventBridge.

Relevant EXIT events are forwarded to an Amazon SNS topic:

```text
Geofence EXIT
      ↓
Amazon EventBridge
      ↓
SentinelSecurityAlerts
      ↓
Amazon SNS
      ↓
Email Notification
```

This allows a geographic event detected in the cloud to automatically generate a notification without someone continuously monitoring the device manually.

---

## CloudWatch Monitoring

Amazon CloudWatch is used to monitor the AWS IoT infrastructure.

AWS IoT logging provides visibility into:

* MQTT publishing
* IoT Rule matching
* IoT Rule execution
* Successful actions
* Failed actions

The `AWSIotLogsV2` log group was particularly useful during development.

For example, CloudWatch helped identify an issue where the Location rule successfully matched incoming telemetry but its action failed because the expected timestamp was missing. After correcting timestamp handling, the complete geofence workflow was successfully tested.

A CloudWatch dashboard is also used for visibility into IoT activity and rule execution.

---

## AWS Services Used

| Service                 | Purpose                                       |
| ----------------------- | --------------------------------------------- |
| AWS IoT Core            | Secure MQTT communication and message routing |
| AWS IoT Device Shadow   | Cloud representation of device state          |
| Amazon DynamoDB         | Telemetry storage                             |
| Amazon Location Service | Device tracking and geofence evaluation       |
| Amazon EventBridge      | Routing geofence events                       |
| Amazon SNS              | Security email notifications                  |
| Amazon CloudWatch       | Logging, troubleshooting, and monitoring      |
| AWS IAM                 | Service permissions and role-based access     |

---

## Technology Stack

### Android

* Kotlin
* Jetpack Compose
* Android Sensor APIs
* Google Play Services Location API

### IoT and Security

* MQTT
* TLS
* X.509 certificates
* AWS IoT Device SDK / AWS CRT

### AWS

* AWS IoT Core
* AWS IoT Device Shadow
* DynamoDB
* Amazon Location Service
* EventBridge
* SNS
* CloudWatch
* IAM

### Development

* Android Studio
* Gradle
* Git
* GitHub

---

## Security Considerations

Security was an important part of the implementation.

The Android device authenticates to AWS IoT Core using an X.509 certificate rather than embedding AWS access keys in the application.

Sensitive credential files are excluded from Git.

The following are **not included in this repository**:

```text
*.pem
*.key
*.crt
app/src/main/assets/certs/
```

Anyone cloning this project must provide their own AWS IoT Thing, device credentials, endpoint, policies, and supporting AWS infrastructure.

Never commit AWS IoT private keys or cloud credentials to a public repository.

---

## Project Structure

```text
sentinel-android-iot-security/
│
├── app/
│   └── src/
│       └── main/
│           ├── java/com/sentinel/edge/
│           │   ├── MainActivity.kt
│           │   ├── AwsIotManager.kt
│           │   └── ui/theme/
│           │
│           ├── res/
│           └── AndroidManifest.xml
│
├── docs/
│   ├── architecture/
│   │   └── sentinel-architecture.png
│   └── screenshots/
│
├── gradle/
├── .gitignore
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── settings.gradle.kts
```

---

## Running the Project

This repository does not contain the AWS IoT credentials used during development.

To create your own deployment:

1. Clone the repository.
2. Open it in Android Studio.
3. Create an AWS IoT Thing.
4. Generate an X.509 certificate and private key.
5. Attach an appropriate IoT policy.
6. Configure your AWS IoT endpoint.
7. Store your certificates locally under:

```text
app/src/main/assets/certs/
```

8. Configure the required DynamoDB, Amazon Location, EventBridge, SNS, CloudWatch, and IAM resources.
9. Build and run the application on an Android device.
10. Start GPS monitoring and connect the device to AWS IoT Core.

AWS resources and permissions should be configured for the account and deployment in which the project is being tested.

---

## Testing

The system was tested across the complete telemetry and security pipeline.

### Telemetry

```text
Android
→ MQTT/TLS
→ AWS IoT Core
→ IoT Rule
→ DynamoDB
```

### Location

```text
Android GPS
→ AWS IoT Core
→ IoT Location Rule
→ SentinelTracker
```

### Geofence Security Event

A test device was first positioned inside the configured geofence and subsequently outside it.

```text
Inside Position
      ↓
Outside Position
      ↓
Geofence EXIT
      ↓
EventBridge
      ↓
SNS
      ↓
Security Email
```

The resulting EXIT event and security notification were successfully received.

---

## What I Learned

Building SENTINEL gave me practical experience connecting an actual edge device to cloud infrastructure rather than working with AWS services individually.

A large part of the learning came from troubleshooting communication between services. I worked through certificate configuration, MQTT connectivity, IoT Rules, IAM permissions, Location Service integration, timestamp handling, geofence events, EventBridge routing, and CloudWatch logging.

The project helped me better understand how **edge computing, IoT messaging, cloud services, identity and access management, event-driven architectures, and monitoring work together as one system.**

It also showed me how an Android prototype can be used to validate an architecture that could later be adapted to dedicated IoT hardware for broader asset-monitoring scenarios.

---

## Future Improvements

Possible next steps include:

* Replace the Android prototype with dedicated IoT/GPS hardware
* Support multiple devices and assets
* Add heartbeat-based offline detection
* Add configurable geofences
* Display location history on an interactive map
* Build a web-based monitoring dashboard
* Add additional environmental or equipment sensors
* Add more detailed security-event severity logic
* Improve the Android dashboard
* Automate AWS infrastructure deployment with Terraform

---

## Demo

A short end-to-end demonstration of SENTINEL will be added after final testing.

The demo will show:

```text
Android Edge Device
        ↓
Live Telemetry
        ↓
AWS IoT Core
        ↓
DynamoDB / Device Shadow
        ↓
Amazon Location
        ↓
Geofence EXIT
        ↓
EventBridge
        ↓
SNS Security Alert
        ↓
CloudWatch Monitoring
```

---

## Author

**Syed Muhammad Ali Hussain**

Computer Science student interested in Cloud Computing, DevOps, IoT, and cloud security.

---

## Disclaimer

SENTINEL is an educational and portfolio project designed to explore edge computing and AWS IoT architecture. The current implementation is a prototype and is not intended to be used as a production-grade asset tracking or physical security system.
