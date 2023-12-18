package com.catjason.yggdrasilbinder

import android.content.Context
import android.os.Looper
import android.widget.Toast

/**
 * ToastUtil:线程安全的Toast
 *
 * @author duqian, Created on 2017/5/20 - 11:51.
 * E-mail:duqian2010@gmail.com
 */
object ToastUtil {
    private var isLong = false
    fun toastLong(context: Context?, vararg args: String?) {
        isLong = true
        toast(context, *args)
    }

    @JvmStatic
    fun toastShort(context: Context?, vararg args: String?) {
        isLong = false
        toast(context, *args)
    }

    @JvmStatic
    fun toast(context: Context?, vararg args: String?) {
        if (context == null) {
            return
        }
        if (isMainThread) {
            makeText(context, *args)
            return
        }
        //子线程looper
        Looper.prepare()
        makeText(context, *args)
        Looper.loop()
    }

    private fun makeText(context: Context, vararg args: String?) {
        val sb = StringBuilder()
        var temp = ""
        for (obj in args) {
            temp = obj.toString()
            sb.append(temp)
        }
        if (isLong) {
            Toast.makeText(context, sb.toString(), Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, sb.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    private val isMainThread: Boolean
        get() = Looper.getMainLooper() == Looper.myLooper()
}