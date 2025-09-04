# Project Description: Currency Detector for the Visually Impaired

This is an Android application, written in Kotlin, designed to assist visually impaired users by identifying currency in real-time. It uses a custom-trained YOLOv5 model to detect and announce currency denominations.

## How it Works

The application utilizes the device's camera to capture a live video stream. Each frame is processed by a TensorFlow Lite model. When the model identifies a currency, it uses the Text-to-Speech (TTS) engine to announce the denomination, for example, "10 Rupees". This provides auditory feedback to the user, making it accessible for those with visual impairments.

A visual overlay also draws a bounding box around the detected currency on the screen, which is useful for sighted developers or for demonstration purposes.

## Core Components

*   **`MainActivity.kt`**: The main screen of the application, written in Kotlin. It handles camera initialization, permissions, and coordinates the different parts of the application.
*   **`TFliteHelper.kt`**: A helper class that loads the YOLOv5 model (`best-fp16.tflite`) and performs the object detection on each camera frame. It also processes the model's output to generate a list of `DetectionResult` objects.
*   **`OverlayView.kt`**: A custom Android View that is placed on top of the camera preview. It is responsible for drawing the bounding boxes and labels for the detected currencies.
*   **`DetectionResult.kt`**: A simple data class that holds the information about a detected object, including its bounding box and the recognized text.
*   **`best-fp16.tflite`**: The trained YOLOv5 model, converted to TensorFlow Lite format for use on mobile devices. This file is located in the `app/src/main/assets` directory.
*   **`labels.txt`**: A text file containing the names of the currencies that the model can recognize. This file is also in the `app/src/main/assets` directory.

## Project Structure

```
CurrencyDetector/
├── app/
│   ├── build.gradle.kts
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── assets/
│   │   │   │   ├── best-fp16.tflite
│   │   │   │   └── labels.txt
│   │   │   ├── java/
│   │   │   │   └── com/
│   │   │   │       └── example/
│   │   │   │           └── currencydetector/
│   │   │   │               ├── DetectionResult.kt
│   │   │   │               ├── MainActivity.kt
│   │   │   │               ├── OverlayView.kt
│   │   │   │               └── TFliteHelper.kt
│   │   │   └── res/
│   │   │       └── layout/
│   │   │           └── activity_main.xml
