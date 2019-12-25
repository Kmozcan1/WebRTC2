package com.vox.sample.webrtc

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.BindingAdapter

/**
 * Created by Kadir Mert Ozcan on 17/07/2019.
 */
object DataBindingAdapters {
    @BindingAdapter("android:src")
    @JvmStatic
    fun setImageResource(imageView: ImageView, resource: Int) {
        imageView.setImageResource(resource)
    }

    @BindingAdapter("android:title")
    @JvmStatic
    fun setText(textView: TextView, resource: Int) {
        textView.setText(resource)
    }

    @BindingAdapter("android:background")
    @JvmStatic
    fun setBackgroundResource(constraintLayout: ConstraintLayout, resource: Int) {
        constraintLayout.setBackgroundResource(resource)
    }

    @BindingAdapter("android:visibility")
    @JvmStatic
    fun setVisibility(view: View, value: Boolean) {
        view.visibility = if (value) View.VISIBLE else View.GONE
    }

    @BindingAdapter("android:text")
    @JvmStatic
    fun setTextFromBoolean(view: TextView, value: Boolean) {
        if (value) setText(view, R.string.YES) else setText(view, R.string.NO)
    }

    @BindingAdapter("android:textColor")
    @JvmStatic
    fun setTextColor(view: TextView, @ColorInt resource: Int) {
        view.setTextColor(resource)
    }
}