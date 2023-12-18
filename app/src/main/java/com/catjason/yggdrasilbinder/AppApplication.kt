package com.catjason.yggdrasilbinder

import android.app.Application
import android.content.Context
import com.catjason.yggdrasilbinder.ToastUtil.toast
import java.io.File

/**
 * description:应用初始化时，注入so本地路径
 *
 * @author Dusan Created on 2019/3/15 - 18:26.
 * E-mail:duqian2010@gmail.com
 */
class AppApplication : Application() {
    private var mContext: Context? = null
    override fun onCreate() {
        super.onCreate()
        mContext = this
        try {
            //动态加载x86的so文件，提前注入so本地路径，只需要注入一次，后续copy或者下载完so文件后再加载。demo方便测试才后续再次注入
            dynamicSo()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dynamicSo() {
        val soFrom = SoUtils.soSourcePath
        if (!File(soFrom).exists()) {
            toast(this, "哈哈，本地so文件不存在，$soFrom")
        }
        SoFileLoadManager.loadSoFile(this, soFrom)
    }
}