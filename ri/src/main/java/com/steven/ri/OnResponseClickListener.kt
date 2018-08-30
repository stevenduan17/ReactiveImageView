package com.steven.ri

/**
 * @author Steven Duan
 * @since 2018/8/27
 * @version 1.0
 */
interface OnResponseClickListener {
  fun onResponseClick(position: Int, point: Pair<Float, Float>)
}