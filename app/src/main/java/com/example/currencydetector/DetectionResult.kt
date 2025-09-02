package com.example.currencydetector

import android.graphics.RectF

// A simple data class to hold the results of a detection
data class DetectionResult(val boundingBox: RectF, val text: String)