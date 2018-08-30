package com.steven.ri

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.ImageView

/**
 * @author Steven Duan
 * @since 2018/8/27
 * @version 1.0
 */
@Suppress("PrivatePropertyName")
class ReactiveImageView : AppCompatImageView, View.OnTouchListener, ViewTreeObserver.OnGlobalLayoutListener {

  private val TAG = ReactiveImageView::class.java.simpleName
  private val INVALID_AREA = -1
  private val NORMAL_SCALE = 1F
  private val MEDIUM_SCALE = 2F
  private val MAX_SCALE = 4F

  private var originPoints: List<Pair<Float, Float>>? = null
  private lateinit var currentPoints: MutableList<Pair<Float, Float>>
  private var radius: Int = 0
  private val gestureDetector by lazy { GestureDetector(context, gestureListener) }
  private val scaleGestureDetector by lazy { ScaleGestureDetector(context, scaleGestureListener) }
  private var listener: OnResponseClickListener? = null
  private val matrixValues = FloatArray(9)
  private val scaleMatrix = Matrix()
  private var shouldInitScaleMatrix = true
  private var isAutoScaling = false
  private var lastPointerCount = 0
  private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }
  private var canDrag = true
  private var lastX = 0F
  private var lastY = 0F
  private var isHorizontalChecked = false
  private var isVerticalChecked = false

  constructor(context: Context) : super(context)

  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  init {
    scaleType = ImageView.ScaleType.MATRIX
    setOnTouchListener(this)
    // It's important to set following three attrs for touch response.
    isClickable = true
    isFocusable = true
    isLongClickable = true
  }

  override fun onTouch(v: View?, event: MotionEvent?): Boolean {
    scaleGestureDetector.onTouchEvent(event)
    processImageMovement(event!!)
    return gestureDetector.onTouchEvent(event)
  }

  fun setReactPoints(points: List<Pair<Float, Float>>) {
    this.originPoints = points
    this.currentPoints = points.toMutableList()
  }

  fun setRadius(radius: Int) {
    this.radius = radius
  }

  fun setOnResponseClickListener(listener: OnResponseClickListener) {
    this.listener = listener
  }

  private val gestureListener by lazy {
    object : GestureDetector.SimpleOnGestureListener() {
      override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        val position = isInValidArea(e)
        if (position == -1) {
          Log.w(TAG, "No area matched.")
        } else {
          this@ReactiveImageView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
              HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
          listener?.onResponseClick(position, currentPoints[position])
        }
        return super.onSingleTapConfirmed(e)
      }

      override fun onDoubleTap(e: MotionEvent): Boolean {
        if (isAutoScaling) return true
        val x = e.x
        val y = e.y
        val scale = getCurrentScaling()
        when {
          scale < MEDIUM_SCALE -> this@ReactiveImageView.postDelayed(AutoScaleRunnable(MEDIUM_SCALE, x, y), 16L)
          scale < MAX_SCALE -> this@ReactiveImageView.postDelayed(AutoScaleRunnable(MAX_SCALE, x, y), 16L)
          else -> this@ReactiveImageView.postDelayed(AutoScaleRunnable(NORMAL_SCALE, x, y), 16L)
        }
        isAutoScaling = true
        return true
      }
    }
  }

  private fun isInValidArea(event: MotionEvent): Int {
    val x = event.x
    val y = event.y
    for ((index, point) in currentPoints.withIndex()) {
      val distance = Math.sqrt(Math.pow((point.first - x).toDouble(), 2.0)
          + Math.pow((point.second - y).toDouble(), 2.0))
      if (distance <= radius) return index
    }
    return INVALID_AREA
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    viewTreeObserver.addOnGlobalLayoutListener(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    viewTreeObserver.removeOnGlobalLayoutListener(this)
  }

  override fun onGlobalLayout() {
    if (shouldInitScaleMatrix) {
      if (drawable == null) return
      imageMatrix = scaleMatrix
      shouldInitScaleMatrix = false
    }
  }

  private val scaleGestureListener by lazy {
    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
      override fun onScale(detector: ScaleGestureDetector): Boolean {
        val scale = getCurrentScaling()
        Log.d(TAG, "scale:$scale")
        var scaleFactor = detector.scaleFactor
        if ((scale < MAX_SCALE && scaleFactor > 1F)
            || (scale > NORMAL_SCALE && scaleFactor < 1F)) {
          if (scaleFactor * scale < NORMAL_SCALE) {
            scaleFactor = NORMAL_SCALE / scale
          }
          if (scaleFactor * scale > MAX_SCALE) {
            scaleFactor = MAX_SCALE / scale
          }
          scaleMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
          scopeControlWhenScale()
          imageMatrix = scaleMatrix
          onImageScale()
          onImageScaleTranslate()
        }
        return true
      }
    }
  }

  private fun getCurrentScaling(): Float {
    scaleMatrix.getValues(matrixValues)
    return matrixValues[Matrix.MSCALE_X]
  }

  /**
   * Scope control when scale the image.
   */
  private fun scopeControlWhenScale() {
    val rectF = getMatrixRectF()
    var deltaX = 0F
    var deltaY = 0F

    if (rectF.width() >= width) {
      if (rectF.left > 0) {
        deltaX = -rectF.left
      }
      if (rectF.right < width) {
        deltaX = width - rectF.right
      }
    }

    if (rectF.height() >= height) {
      if (rectF.top > 0) {
        deltaY = -rectF.top
      }
      if (rectF.bottom < height) {
        deltaY = height - rectF.bottom
      }
    }

    if (rectF.width() < width) {
      deltaY = height / 2F - rectF.bottom + rectF.bottom / 2F
    }

    scaleMatrix.postTranslate(deltaX, deltaY)
  }

  /**
   * Get rectF from image matrix.
   */
  private fun getMatrixRectF(): RectF {
    val matrix = scaleMatrix
    val rectF = RectF()
    rectF.set(0F, 0F, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
    matrix.mapRect(rectF)
    return rectF
  }

  /**
   * Double tap for auto scale
   */
  inner class AutoScaleRunnable(private val targetScale: Float, private val x: Float,
                                private val y: Float) : Runnable {
    private var tempScale: Float
    private val SCALE_UP = 1.05F
    private val SCALE_DOWN = 0.95F

    init {
      val currentScaling = getCurrentScaling()
      tempScale = if (currentScaling < targetScale) SCALE_UP else SCALE_DOWN
    }

    override fun run() {
      scaleMatrix.postScale(tempScale, tempScale, x, y)
      scopeControlWhenScale()
      imageMatrix = scaleMatrix

      val scale = getCurrentScaling()
      if ((tempScale > 1F && scale < targetScale) || (tempScale < 1F && targetScale < scale)) {
        this@ReactiveImageView.postDelayed(this, 16L)
      } else {
        val destScale = targetScale / scale
        scaleMatrix.postScale(destScale, destScale, x, y)
        scopeControlWhenScale()
        imageMatrix = scaleMatrix
        isAutoScaling = false
        onImageScale()
        onImageScaleTranslate()
      }
    }
  }

  /**
   * Processing the touch event for moving when scaling greater than [NORMAL_SCALE]
   */
  private fun processImageMovement(event: MotionEvent) {
    Log.d(TAG, "${event.x},${event.y}")
    // get average point if multi-touch
    if (getCurrentScaling() == NORMAL_SCALE) return
    val pointerCount = event.pointerCount
    var x = 0F
    var y = 0F
    for (i in 0 until pointerCount) {
      x += event.getX(i)
      y += event.getY(i)
    }
    x /= pointerCount
    y /= pointerCount
    if (pointerCount != lastPointerCount) {
      canDrag = false
      lastX = x
      lastY = y
    }
    lastPointerCount = pointerCount
    when (event.action) {
      MotionEvent.ACTION_MOVE -> {
        var dx = x - lastX
        var dy = y - lastY
        if (!canDrag) canDrag = canDrag(dx, dy)
        if (canDrag) {
          val rectF = getMatrixRectF()

          isHorizontalChecked = true
          isVerticalChecked = true
          if (rectF.width() < width) {
            dx = 0F
            isHorizontalChecked = false
          }
          if (rectF.height() < height) {
            dy = 0F
            isVerticalChecked = false
          }
          scaleMatrix.postTranslate(dx, dy)
          val scopeMove = scopeControlWhenMove(rectF)
          imageMatrix = scaleMatrix
          onMoveTranslate(dx + scopeMove.first, dy + scopeMove.second)
        }
        lastX = x
        lastY = y
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> lastPointerCount = 0
    }
  }

  private fun scopeControlWhenMove(rectF: RectF): Pair<Float, Float> {
    var dx = 0F
    var dy = 0F
    if (rectF.top > 0 && isVerticalChecked) {
      dy = -rectF.top
    }
    if (rectF.bottom < height && isVerticalChecked) {
      dy = height - rectF.bottom
    }
    if (rectF.left > 0 && isHorizontalChecked) {
      dx = -rectF.left
    }
    if (rectF.right < width && isHorizontalChecked) {
      dx = width - rectF.right
    }
    scaleMatrix.postTranslate(dx, dy)
    return Pair(dx, dy)
  }

  private fun canDrag(dx: Float, dy: Float) = Math.sqrt((dx * dx + dy * dy).toDouble()) >= touchSlop

  private fun onImageScale() {
    val scaling = getCurrentScaling()
    currentPoints.clear()
    originPoints?.let {
      for (pair in it) {
        currentPoints.add(Pair(pair.first * scaling, pair.second * scaling))
      }
    }
  }

  private fun onImageScaleTranslate() {
    currentPoints = currentPoints.map {
      val rectF = getMatrixRectF()
      return@map Pair(it.first + rectF.left, it.second + rectF.top)
    }.toMutableList()
  }

  private fun onMoveTranslate(dx: Float, dy: Float) {
    currentPoints = currentPoints.map {
      return@map Pair(it.first + dx, it.second + dy)
    }.toMutableList()
  }
}

