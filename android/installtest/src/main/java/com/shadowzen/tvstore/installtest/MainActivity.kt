package com.shadowzen.tvstore.installtest

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

/** 安装测试用的最小应用，只显示自己的版本和签名标识。 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val versionName = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        }
        setContentView(
            TextView(this).apply {
                text = getString(R.string.content, versionName, getString(R.string.signer_label))
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                setTextColor(0xFFF4F7FC.toInt())
                setBackgroundColor(0xFF0B1020.toInt())
            },
        )
    }
}
