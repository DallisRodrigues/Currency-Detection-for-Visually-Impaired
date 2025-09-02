package com.example.currencydetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var results: List<DetectionResult> = listOf()
    private val boxPaint = Paint()
    private val textBackgroundPaint = Paint()
    private val textPaint = Paint()
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    init {
        initPaints()
    }

    private fun initPaints() {
        boxPaint.color = ContextCompat.getColor(context, android.R.color.holo_blue_bright)
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = 8f

        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.alpha = 160

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f
    }

    fun setResults(detectionResults: List<DetectionResult>, imageWidth: Int, imageHeight: Int) {
        results = detectionResults
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate() // This will trigger a redraw of the view
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (imageWidth == 0 || imageHeight == 0) return

        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight

        // Draw each bounding box and its label
        for (result in results) {
            val boundingBox = result.boundingBox

            val scaledBoundingBox = RectF(
                boundingBox.left * scaleX,
                boundingBox.top * scaleY,
                boundingBox.right * scaleX,
                boundingBox.bottom * scaleY
            )

            // Draw the box
            canvas.drawRect(scaledBoundingBox, boxPaint)

            // Draw the label with a background
            val text = result.text
            val textWidth = textPaint.measureText(text)
            val textHeight = textPaint.descent() - textPaint.ascent()
            val textBackgroundRect = RectF(
                scaledBoundingBox.left,
                scaledBoundingBox.top,
                scaledBoundingBox.left + textWidth + 8, // padding
                scaledBoundingBox.top + textHeight + 8
            )
            canvas.drawRect(textBackgroundRect, textBackgroundPaint)
            canvas.drawText(text, scaledBoundingBox.left + 4, scaledBoundingBox.top + textHeight, textPaint)
        }
    }
}