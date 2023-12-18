package com.catjason.yggdrasilbinder

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.ref.WeakReference

/**
 * Description:使用System.load，动态加载指定路径的so文件，对大型项目来说不适用，建议采用路径注入的方式完美适配已有工程的so动态下发
 *
 * @author Dusan, Created on 2019/3/15 - 16:29.
 * E-mail:duqian2010@gmail.com
 */
class LocalSoHelper private constructor(context: Context) {
    private val weakReference: WeakReference<Context>

    init {
        weakReference = WeakReference(context)
    }

    /**
     * 加载so文件
     */
    fun loadSo(loadListener: LoadListener?) {
        val dir = targetDir
        val currentFiles: Array<File> = dir.listFiles()?: return
        if (currentFiles.isEmpty()) {
            loadListener?.failed()
        }
        for (i in currentFiles.indices) {
            System.load(currentFiles[i].absolutePath)
        }
        loadListener?.finish()
    }

    /**
     * @param fromFile 指定的本地目录
     * @param isCover  true覆盖原文件即删除原有文件后拷贝新的文件进来
     * @return
     */
    fun copySo(fromFile: String?, isCover: Boolean, copyListener: CopyListener?) {
        //要复制的文件目录
        val currentFiles: Array<File>
        val root = File(fromFile)
        //如同判断SD卡是否存在或者文件是否存在,如果不存在则 return出去
        if (!root.exists()) {
            copyListener?.failed()
            return
        }
        //如果存在则获取当前目录下的全部文件并且填充数组
        currentFiles = root.listFiles()

        //目标目录
        val targetDir = targetDir
        //创建目录
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        } else {
            //删除全部老文件
            if (isCover) {
                for (file in targetDir.listFiles()) {
                    file.delete()
                }
            }
        }
        var isCopy = false
        //遍历要复制该目录下的全部文件
        for (i in currentFiles.indices) {
            val currentFile = currentFiles[i]
            val currentFileName = currentFile.name
            if (currentFileName.contains(".so")) {
                val copy = copySdcardFile(
                    currentFile.path,
                    targetDir.toString() + File.separator + currentFileName
                )
                if (copy) {
                    isCopy = copy
                }
            }
        }
        if (copyListener != null && isCopy) {
            copyListener.finish()
        }
    }

    private val targetDir: File
        private get() = weakReference.get()!!.getDir(TARGET_LIBS_NAME, Context.MODE_PRIVATE)

    /**
     * copy完成后回调接口
     */
    interface CopyListener {
        //其实方法返回boolean也成
        fun finish()
        fun failed()
    }

    /**
     * load完成后回调接口
     */
    interface LoadListener {
        //其实方法返回boolean也成
        fun finish()
        fun failed()
    }

    companion object {
        private const val TARGET_LIBS_NAME = "test_libs"

        @Volatile
        private var instance: LocalSoHelper? = null
        fun getInstance(context: Context): LocalSoHelper? {
            if (instance == null) {
                synchronized(LocalSoHelper::class.java) {
                    if (instance == null) {
                        instance = LocalSoHelper(context)
                    }
                }
            }
            return instance
        }

        /**
         * 文件拷贝(要复制的目录下的所有非文件夹的文件拷贝)
         *
         * @param fromFile
         * @param toFile
         * @return
         */
        private fun copySdcardFile(fromFile: String, toFile: String): Boolean {
            try {
                val fosfrom = FileInputStream(fromFile)
                val fosto = FileOutputStream(toFile)
                val baos = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                var len = -1
                while (fosfrom.read(buffer).also { len = it } != -1) {
                    baos.write(buffer, 0, len)
                }
                // 从内存到写入到具体文件
                fosto.write(baos.toByteArray())
                // 关闭文件流
                baos.close()
                fosto.close()
                fosfrom.close()
                return true
            } catch (e: Exception) {
                Log.d("dq-so", "copySdcardFile error $e")
            }
            return false
        }
    }
}