package dev.yourapp.blem3

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView

class HelloActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NỀN TRẮNG + TEXT -> đảm bảo vẽ frame đầu tiên ngay
        val root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        val tv = TextView(this).apply {
            text = "HelloActivity OK ✅\n(Bấm back để thoát)"
            textSize = 18f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }
        root.addView(
            tv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { gravity = Gravity.CENTER }
        )
        setContentView(root)
    }
}
