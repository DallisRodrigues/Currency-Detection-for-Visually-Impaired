package com.example.currencydetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.PriorityQueue

class TFLiteHelper(
    private val context: Context,
    private val listener: DetectionListener,
    private val confidenceThreshold: Float = 0.5f,
    private val iouThreshold: Float = 0.45f
) {

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var inputSize = 0

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val modelByteBuffer = loadModelFile("best-fp16.tflite")
            interpreter = Interpreter(modelByteBuffer)
            labels = FileUtil.loadLabels(context, "labels.txt")
            val inputTensor = interpreter?.getInputTensor(0)
            inputSize = inputTensor?.shape()?.get(1) ?: 0
            Log.d(TAG, "TFLite model and labels loaded successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading TFLite model", e)
        }
    }

    private fun loadModelFile(modelName: String): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelName)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun detect(image: ImageProxy) {
        if (interpreter == null || inputSize == 0) {
            image.close()
            return
        }

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
            .add(Rot90Op(-image.imageInfo.rotationDegrees / 90))
            .add(NormalizeOp(0f, 255f))
            .build()

        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(image.toBitmap())
        val processedImage = imageProcessor.process(tensorImage)

        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 25200, labels.size + 5), DataType.FLOAT32)
        val outputs = mutableMapOf<Int, Any>()
        outputs[0] = outputBuffer.buffer

        val inputs = arrayOf(processedImage.buffer)
        interpreter?.runForMultipleInputsOutputs(inputs, outputs)

        val results = postProcess(outputBuffer)

        listener.onResults(results, image.width, image.height)

        image.close()
    }

    private fun postProcess(outputBuffer: TensorBuffer): List<DetectionResult> {
        val scores = outputBuffer.floatArray
        val numBoxes = outputBuffer.shape[1]
        val numClasses = labels.size
        val detections = mutableListOf<Detection>()

        for (i in 0 until numBoxes) {
            val offset = i * (numClasses + 5)
            val confidence = scores[offset + 4]

            if (confidence >= confidenceThreshold) {
                var maxClassScore = 0f
                var maxClassIndex = -1
                for (j in 0 until numClasses) {
                    val classScore = scores[offset + 5 + j]
                    if (classScore > maxClassScore) {
                        maxClassScore = classScore
                        maxClassIndex = j
                    }
                }

                if (maxClassScore > confidenceThreshold) {
                    val cx = scores[offset]
                    val cy = scores[offset + 1]
                    val w = scores[offset + 2]
                    val h = scores[offset + 3]

                    val left = (cx - w / 2)
                    val top = (cy - h / 2)
                    val right = (cx + w / 2)
                    val bottom = (cy + h / 2)

                    detections.add(
                        Detection(
                            RectF(left, top, right, bottom),
                            maxClassScore,
                            maxClassIndex
                        )
                    )
                }
            }
        }

        return applyNMS(detections)
    }

    private fun applyNMS(detections: List<Detection>): List<DetectionResult> {
        val sortedDetections = detections.sortedByDescending { it.score }
        val selectedDetections = mutableListOf<DetectionResult>()
        val occupied = BooleanArray(sortedDetections.size)

        for (i in sortedDetections.indices) {
            if (occupied[i]) continue

            val detection = sortedDetections[i]
            selectedDetections.add(
                DetectionResult(
                    detection.boundingBox,
                    "${labels[detection.classIndex]} (${(detection.score * 100).toInt()}%)"
                )
            )
            occupied[i] = true

            for (j in (i + 1) until sortedDetections.size) {
                if (occupied[j]) continue
                if (iou(detection.boundingBox, sortedDetections[j].boundingBox) > iouThreshold) {
                    occupied[j] = true
                }
            }
        }
        return selectedDetections
    }

    private fun iou(box1: RectF, box2: RectF): Float {
        val xA = maxOf(box1.left, box2.left)
        val yA = maxOf(box1.top, box2.top)
        val xB = minOf(box1.right, box2.right)
        val yB = minOf(box1.bottom, box2.bottom)
        val intersectionArea = maxOf(0f, xB - xA) * maxOf(0f, yB - yA)
        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)
        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    fun close() {
        interpreter?.close()
    }

    interface DetectionListener {
        fun onResults(results: List<DetectionResult>?, imageWidth: Int, imageHeight: Int)
    }

    private data class Detection(val boundingBox: RectF, val score: Float, val classIndex: Int)

    companion object {
        private const val TAG = "TFLiteHelper"
        const val MAX_DETECTIONS = 10
    }
}