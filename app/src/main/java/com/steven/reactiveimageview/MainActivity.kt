package com.steven.reactiveimageview

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import com.steven.ri.OnResponseClickListener
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
  @Suppress("PrivatePropertyName")
  private val TAG = MainActivity::class.java.simpleName

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    val density = resources.displayMetrics.density
    Log.d(TAG, "density:$density")

    //test in hdpi
    mReactiveImageView.setReactPoints(listOf(
        Pair(63F, 39F),
        Pair(377F, 27F),
        Pair(237F, 90F),
        Pair(70F, 263F),
        Pair(307F, 249F)
    ))

    mReactiveImageView.setRadius(30)
    mReactiveImageView.setOnResponseClickListener(object : OnResponseClickListener {
      override fun onResponseClick(position: Int, point: Pair<Float, Float>) {
        Toast.makeText(this@MainActivity, "$position was clicked.", Toast.LENGTH_SHORT).show()
      }
    })
  }
}
