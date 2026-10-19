package com.example.standbyclock

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Pinch overview: every group and every face on one screen. Tap a face to jump to it. */
class OverviewActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        Settings.FACE_GROUPS.forEach { (groupName, faces) ->
            content.addView(TextView(this).apply {
                text = groupName
                textSize = 20f
                setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
            })
            faces.forEach { face ->
                val label = Settings.FACE_OPTIONS.firstOrNull { it.first == face }?.second ?: face
                content.addView(Button(this).apply {
                    text = label
                    setOnClickListener {
                        settings.face = face
                        finish()
                    }
                })
            }
        }
        setContentView(ScrollView(this).apply { addView(content) })
    }
}
