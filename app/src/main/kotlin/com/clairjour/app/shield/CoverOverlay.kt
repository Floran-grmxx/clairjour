package com.clairjour.app.shield

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.clairjour.app.R

/**
 * Opaque panel drawn above Instagram by the accessibility service. It swallows the touches
 * in its area, so the covered content can neither be seen nor scrolled.
 */
class CoverOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun show(bounds: NodeBounds) {
        val params = WindowManager.LayoutParams(
            bounds.width,
            bounds.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.left
            y = bounds.top
        }
        val current = view
        if (current == null) {
            val created = createView()
            windowManager.addView(created, params)
            view = created
        } else {
            windowManager.updateViewLayout(current, params)
        }
    }

    fun hide() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }

    private fun createView(): View = FrameLayout(context).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.clairjour_primary))
        isClickable = true
        addView(
            TextView(context).apply {
                setText(R.string.shield_cover_message)
                setTextColor(ContextCompat.getColor(context, R.color.clairjour_surface_light))
                textSize = COVER_TEXT_SIZE_SP
                gravity = Gravity.CENTER
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
    }

    private companion object {
        const val COVER_TEXT_SIZE_SP = 16f
    }
}
