package com.catjason.yggdrasilbinder

import android.os.Build
import android.os.Environment
import android.util.Log
import com.catjason.yggdrasilbinder.ReflectUtil.expandFieldArray
import com.catjason.yggdrasilbinder.ReflectUtil.findField
import com.catjason.yggdrasilbinder.ReflectUtil.findMethod
import java.io.File
import java.io.IOException

/**
 * Description:动态加载so文件的核心，注入so路径到nativeLibraryDirectories数组第一个位置，会优先从这个位置查找so
 * 更多姿势，请参考开源库动态更新so的黑科技，仅供学习交流
 *
 * @author Dusan, Created on 2019/3/15 - 18:17.
 * E-mail:duqian2010@gmail.com
 */
object LoadLibraryUtil {
    private val TAG = LoadLibraryUtil::class.java.simpleName + "-duqian"
    private var lastSoDir: File? = null

    /**
     * 清除so路径，实际上是设置一个无效的path，用户测试无so库的情况
     */
    fun clearSoPath(classLoader: ClassLoader?) {
        try {
            val testDirNoSo = Environment.getExternalStorageDirectory().absolutePath + "/duqian/"
            File(testDirNoSo).mkdirs()
            installNativeLibraryPath(classLoader, testDirNoSo)
        } catch (throwable: Throwable) {
            Log.e(TAG, "dq clear path error$throwable")
            throwable.printStackTrace()
        }
    }

    @Synchronized
    @Throws(Throwable::class)
    fun installNativeLibraryPath(classLoader: ClassLoader?, folderPath: String?) {
        folderPath?: return
        installNativeLibraryPath(classLoader, File(folderPath))
    }

    /**
     * 安装native库路径到指定的ClassLoader。
     * 根据Android的版本，选择合适的方法来修改ClassLoader的路径，以便加载指定目录下的native库。
     *
     * @param classLoader 用于加载SO文件的ClassLoader。
     * @param folder 包含SO文件的目录。
     * @throws Throwable 如果安装过程中出现反射调用错误或其他异常。
     */
    @Synchronized
    @Throws(Throwable::class)
    fun installNativeLibraryPath(classLoader: ClassLoader?, folder: File?) {
        // 检查ClassLoader和文件夹是否有效。
        if (classLoader == null || folder == null || !folder.exists()) {
            Log.e(TAG, "classLoader or folder is illegal $folder")
            return
        }

        // 获取当前Android SDK的版本号。
        val sdkInt = Build.VERSION.SDK_INT

        // 判断Android版本是否高于或等于25（Android 7.1）。
        val aboveM = sdkInt == 25 && previousSdkInt != 0 || sdkInt > 25

        // 根据不同的Android版本，调用不同的安装方法。
        if (aboveM) {
            // 对于Android 7.1及以上版本，使用V25类的安装方法。
            V25.install(classLoader, folder)
        } else if (sdkInt >= 23) {
            // 对于Android 6.0（SDK版本23）到7.0，使用V23类的安装方法。
            V23.install(classLoader, folder)
        } else if (sdkInt >= 14) {
            // 对于Android 4.0（SDK版本14）及以上，使用V14类的安装方法。
            V14.install(classLoader, folder)
        }

        // 记录最后安装的SO文件目录。
        lastSoDir = folder
    }


    private val previousSdkInt: Int
        /**
         * fuck部分机型删了该成员属性，兼容
         *
         * @return 被厂家删了返回1，否则正常读取
         */
        get() {
            try {
                return Build.VERSION.PREVIEW_SDK_INT
            } catch (ignore: Throwable) {
            }
            return 1
        }

    private object V23 {
        /**
         * 安装native库的路径到ClassLoader。
         *
         * @param classLoader 用于加载SO文件的ClassLoader。
         * @param folder 包含SO文件的目录。
         * @throws Throwable 如果发生反射调用错误或其他意外错误。
         */
        @Throws(Throwable::class)
        fun install(classLoader: ClassLoader, folder: File) {
            // 通过反射找到ClassLoader的pathList字段，这是一个内部的类加载器路径列表。
            val pathListField = findField(classLoader, "pathList")
            val dexPathList = pathListField[classLoader] ?: return

            // 从dexPathList中获取nativeLibraryDirectories字段，这是一个包含native库路径的列表。
            val nativeLibraryDirectories = findField(dexPathList, "nativeLibraryDirectories")
            val libDirsTemp = (nativeLibraryDirectories[dexPathList] as? MutableList<*>)
            val libDirs = libDirsTemp?.filterIsInstance<File>()?.toMutableList() ?: ArrayList(2)

            // 清除已经存在的与folder相同的路径，防止重复加载。
            val libDirIt = libDirs.iterator()
            while (libDirIt.hasNext()) {
                val libDir = libDirIt.next()
                if (folder == libDir || folder == lastSoDir) {
                    libDirIt.remove()
                    Log.d(TAG, "dq libDirIt.remove() " + folder.absolutePath)
                    break
                }
            }
            // 将新的SO文件路径添加到列表的最前面，确保它会被优先加载。
            libDirs.add(0, folder)

            // 获取系统的native库路径列表。
            val systemNativeLibraryDirectories =
                findField(dexPathList, "systemNativeLibraryDirectories")
            val systemLibDirsTemp = systemNativeLibraryDirectories[dexPathList] as? List<*>
            val systemLibDirs = systemLibDirsTemp?.filterIsInstance<File>()?.toMutableList() ?: ArrayList(2)
            Log.d(TAG, "dq systemLibDirs,size=" + systemLibDirs.size)

            // 反射调用makePathElements方法来构造新的路径列表。
            val makePathElements = findMethod(
                dexPathList,
                "makePathElements",
                MutableList::class.java,
                File::class.java,
                MutableList::class.java
            )
            val suppressedExceptions = ArrayList<IOException>()
            libDirs.addAll(systemLibDirs)
            val elements = makePathElements.invoke(
                dexPathList,
                libDirs,
                null,
                suppressedExceptions
            ) as? Array<*>

            // 更新ClassLoader的nativeLibraryPathElements字段，以使用新的路径列表。
            val nativeLibraryPathElements = findField(dexPathList, "nativeLibraryPathElements")
            nativeLibraryPathElements.isAccessible = true
            nativeLibraryPathElements[dexPathList] = elements
        }
    }
    /**
     * 定义用于Android API 25及以上版本的操作，用于处理native库的加载路径。
     * 这个类的目的是将自定义的native库路径添加到类加载器的nativeLibraryDirectories列表的最前面，
     * 确保即使安装包的libs目录中有同名的SO文件，也会优先加载指定路径的外部SO文件。
     */
    private object V25 {
        /**
         * 将指定文件夹的路径添加到ClassLoader的native库路径列表中。
         *
         * @param classLoader 类加载器，用于加载native库。
         * @param folder 包含SO文件的目录。
         * @throws Throwable 反射调用中可能抛出的任何异常。
         */
        @Throws(Throwable::class)
        fun install(classLoader: ClassLoader, folder: File) {
            // 通过反射获取ClassLoader的pathList字段。
            val pathListField = findField(classLoader, "pathList")
            val dexPathList = pathListField[classLoader] ?: return

            // 获取nativeLibraryDirectories字段，该字段是一个保存有native库路径的列表。
            val nativeLibraryDirectories = findField(dexPathList, "nativeLibraryDirectories")
            val libDirsTemp = nativeLibraryDirectories[dexPathList] as? MutableList<*>
            val libDirs = libDirsTemp?.filterIsInstance<File>()?.toMutableList() ?: ArrayList(2)

            // 遍历libDirs列表，移除已存在的与folder相同的路径。
            val libDirIt = libDirs.iterator()
            while (libDirIt.hasNext()) {
                val libDir = libDirIt.next()
                if (folder == libDir || folder == lastSoDir) {
                    libDirIt.remove()
                    Log.d(TAG, "dq libDirIt.remove()" + folder.absolutePath)
                    break
                }
            }

            // 将新的SO文件路径添加到列表的最前面。
            libDirs.add(0, folder)

            // 获取系统native库路径(system/lib)并添加到libDirs列表中。
            val systemNativeLibraryDirectories = findField(dexPathList, "systemNativeLibraryDirectories")
            val systemLibDirsTemp = systemNativeLibraryDirectories[dexPathList] as? List<*>
            val systemLibDirs = systemLibDirsTemp?.filterIsInstance<File>()?.toMutableList() ?: ArrayList(2)
            Log.d(TAG, "dq systemLibDirs,size=" + systemLibDirs.size)

            // 通过反射调用makePathElements方法来构造新的路径元素数组。
            val makePathElements = findMethod(dexPathList, "makePathElements", MutableList::class.java)
            libDirs.addAll(systemLibDirs)
            val elements = makePathElements.invoke(dexPathList, libDirs) as? Array<*>

            // 更新ClassLoader的nativeLibraryPathElements字段，使用新的路径数组。
            val nativeLibraryPathElements = findField(dexPathList, "nativeLibraryPathElements")
            nativeLibraryPathElements.isAccessible = true
            nativeLibraryPathElements[dexPathList] = elements
        }
    }

    private object V14 {
        @Throws(Throwable::class)
        fun install(classLoader: ClassLoader, folder: File) {
            val pathListField = findField(classLoader, "pathList")
            val dexPathList = pathListField[classLoader]?: return
            expandFieldArray(dexPathList, "nativeLibraryDirectories", arrayOf(folder))
        }
    }
}