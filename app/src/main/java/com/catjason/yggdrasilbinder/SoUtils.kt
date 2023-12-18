package com.catjason.yggdrasilbinder

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

/**
 * Description:工具类
 *
 * @author Dusan, Created on 2019/3/14 - 20:26.
 * E-mail:duqian2010@gmail.com
 */
object SoUtils {
    @JvmStatic
    val soSourcePath: String
        get() {
            var cpuArchType = cpuArchType
            if (!TextUtils.isEmpty(cpuArchType)) {
                cpuArchType = cpuArchType.lowercase(Locale.getDefault())
            }
            val rootSdcard = Environment.getExternalStorageDirectory().absolutePath
            val soFrom = "$rootSdcard/libs/$cpuArchType/"
            val file = File(soFrom)
            if (!file.exists()) {
                file.mkdirs()
            }
            Log.d("dq", "soFrom=$soFrom")
            return soFrom
        }
    val isX86Phone: Boolean
        get() {
            val archType = cpuArchType
            return !TextUtils.isEmpty(archType) && "x86" == archType.lowercase(Locale.getDefault())
        }
    val cpuArchType: String
        get() {
            var arch = ""
            try {
                val clazz = Class.forName("android.os.SystemProperties")
                val get = clazz.getDeclaredMethod(
                    "get", *arrayOf<Class<*>>(
                        String::class.java
                    )
                )
                arch = get.invoke(clazz, *arrayOf<Any>("ro.product.cpu.abi")) as String
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (TextUtils.isEmpty(arch)) {
                arch = Build.CPU_ABI //可能不准确？
            }
            Log.d("dq getCpuArchType", "arch $arch")
            return arch
        }

    @JvmStatic
    fun copyAssetsDirectory(context: Context, fromAssetPath: String, toPath: String): Boolean {
        return try {
            val assetManager = context.assets
            val files = context.assets.list(fromAssetPath)
            if (isExist(toPath)) {
                //deleteFile(toPath);//很危险
            } else {
                File(toPath).mkdirs()
            }
            var res = true
            for (file in files!!) res = if (file.contains(".")) {
                res and copyAssetFile(assetManager, "$fromAssetPath/$file", "$toPath/$file")
            } else {
                res and copyAssetsDirectory(context, "$fromAssetPath/$file", "$toPath/$file")
            }
            res
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun isExist(path: String?): Boolean {
        if (TextUtils.isEmpty(path)) {
            return false
        }
        val exist: Boolean = try {
            path?: return false
            val file = File(path)
            file.exists()
        } catch (e: Exception) {
            false
        }
        return exist
    }

    /**
     * 删除文件或文件夹(包括目录下的文件)
     *
     * @param filePath
     */
    @JvmStatic
    fun deleteFile(filePath: String): Boolean {
        if (TextUtils.isEmpty(filePath)) {
            return false
        }
        if (Environment.getExternalStorageDirectory().absolutePath == filePath) {
            return false //防止直接删除了sdcard根目录
        }
        try {
            val f = File(filePath)
            if (f.exists() && f.isDirectory) {
                val delFiles = f.listFiles()
                if (delFiles != null && delFiles.size > 0) {
                    for (i in delFiles.indices) {
                        deleteFile(delFiles[i].absolutePath)
                    }
                }
            }
            f.delete()
        } catch (e: Exception) {
            return false
        }
        return true
    }

    fun copyAssetFile(
        assetManager: AssetManager?,
        fromAssetPath: String?,
        toPath: String?
    ): Boolean {
        if (assetManager == null || TextUtils.isEmpty(fromAssetPath) || TextUtils.isEmpty(toPath)) {
            return false
        }
        var bis: BufferedInputStream? = null
        var fos: FileOutputStream? = null
        try {
            val file = File(toPath)
            file.delete()
            file.parentFile.mkdirs()
            val inputStream = assetManager.open(fromAssetPath!!)
            bis = BufferedInputStream(inputStream)
            fos = FileOutputStream(toPath)
            val buf = ByteArray(1024)
            var read: Int
            while (bis.read(buf).also { read = it } != -1) {
                fos.write(buf, 0, read)
            }
            fos.flush()
            fos.close()
            bis.close()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                bis!!.close()
                fos!!.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    /**
     * /system/lib64/libart.so
     */
    private fun isART64(context: Context): Boolean {
        val fileName = "art"
        try {
            val classLoader = context.classLoader
            val cls: Class<*> = ClassLoader::class.java
            val method = cls.getDeclaredMethod("findLibrary", String::class.java)
            val `object` = method.invoke(classLoader, fileName)
            if (`object` != null) {
                return (`object` as String).contains("lib64")
            }
        } catch (e: Exception) {
            //如果发生异常就用方法②
            return is64bitCPU()
        }
        return false
    }

    private fun is64bitCPU(): Boolean {
        var CPU_ABI: String? = null
        if (Build.VERSION.SDK_INT >= 21) {
            val CPU_ABIS = Build.SUPPORTED_ABIS
            if (CPU_ABIS.size > 0) {
                CPU_ABI = CPU_ABIS[0]
            }
        } else {
            CPU_ABI = Build.CPU_ABI
        }
        return CPU_ABI != null && CPU_ABI.contains("arm64")
    }

    /**
     * ELF文件头 e_indent[]数组文件类标识索引
     */
    private const val EI_CLASS = 4

    /**
     * ELF文件头 e_indent[EI_CLASS]的取值：ELFCLASS32表示32位目标
     */
    private const val ELFCLASS32 = 1

    /**
     * ELF文件头 e_indent[EI_CLASS]的取值：ELFCLASS64表示64位目标
     */
    private const val ELFCLASS64 = 2

    /**
     * The system property key of CPU arch type
     */
    private const val CPU_ARCHITECTURE_KEY_64 = "ro.product.cpu.abilist64"

    /**
     * The system libc.so file path
     */
    private const val SYSTEM_LIB_C_PATH = "/system/lib/libc.so"
    private const val SYSTEM_LIB_C_PATH_64 = "/system/lib64/libc.so"
    private const val PROC_CPU_INFO_PATH = "/proc/cpuinfo"
    private fun getSystemProperty(key: String, defaultValue: String): String {
        var value = defaultValue
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java, String::class.java)
            value = get.invoke(clazz, key, "") as String
        } catch (e: Exception) {
            Log.d("dq getSystemProperty", "key = " + key + ", error = " + e.message)
        }
        Log.d("dq getSystemProperty", "$key = $value")
        return value
    }

    private val isCPUInfo64: Boolean
        /**
         * Read the first line of "/proc/cpuinfo" file, and check if it is 64 bit.
         */
        get() {
            val cpuInfo = File(PROC_CPU_INFO_PATH)
            if (cpuInfo.exists()) {
                var inputStream: InputStream? = null
                var bufferedReader: BufferedReader? = null
                try {
                    inputStream = FileInputStream(cpuInfo)
                    bufferedReader = BufferedReader(InputStreamReader(inputStream), 512)
                    val line = bufferedReader.readLine()
                    if (line != null && line.length > 0 && line.lowercase().contains("arch64")) {
                        return true
                    }
                } catch (_: Throwable) {
                } finally {
                    try {
                        bufferedReader?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    try {
                        inputStream?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            return false
        }
    private val isLibc64: Boolean
        /**
         * Check if system libc.so is 32 bit or 64 bit
         */
        get() {
            val libcFile = File(SYSTEM_LIB_C_PATH)
            if (libcFile.exists()) {
                val header = readELFHeadrIndentArray(libcFile)
                if (header != null && header[EI_CLASS].toInt() == ELFCLASS64) {
                    Log.d("dq isLibc64()", SYSTEM_LIB_C_PATH + " is 64bit")
                    return true
                }
            }
            val libcFile64 = File(SYSTEM_LIB_C_PATH_64)
            if (libcFile64.exists()) {
                val header = readELFHeadrIndentArray(libcFile64)
                if (header != null && header[EI_CLASS].toInt() == ELFCLASS64) {
                    Log.d("dq isLibc64()", SYSTEM_LIB_C_PATH_64 + " is 64bit")
                    return true
                }
            }
            return false
        }

    /**
     * ELF文件头格式是固定的:文件开始是一个16字节的byte数组e_indent[16]
     * e_indent[4]的值可以判断ELF是32位还是64位
     */
    private fun readELFHeadrIndentArray(libFile: File?): ByteArray? {
        if (libFile != null && libFile.exists()) {
            var inputStream: FileInputStream? = null
            try {
                inputStream = FileInputStream(libFile)
                val tempBuffer = ByteArray(16)
                val count = inputStream.read(tempBuffer, 0, 16)
                if (count == 16) {
                    return tempBuffer
                }
            } catch (t: Throwable) {
                Log.e("readELFHeadrIndentArray", "Error:$t")
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return null
    }
}