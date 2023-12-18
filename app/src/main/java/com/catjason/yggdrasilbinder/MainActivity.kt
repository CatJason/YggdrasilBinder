package com.catjason.yggdrasilbinder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.catjason.yggdrasilbinder.SoFileLoadManager.loadSoFile
import com.catjason.yggdrasilbinder.SoUtils.copyAssetsDirectory
import com.catjason.yggdrasilbinder.SoUtils.soSourcePath
import com.catjason.yggdrasilbinder.ToastUtil.toast
import com.catjason.yggdrasilbinder.ToastUtil.toastShort
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import java.io.File

/**
 * description:1，so动态加载demo，2，用于测试google官方android app bundle
 *
 *
 * 动态加载so库demo，无需修改已有工程的so加载逻辑，支持so动态下发并安全加载的方案。\n
 * 在应用启动的时,注入本地so路径，待程序使用过程中so准备后安全加载。so动态加载黑科技，安全可靠！注入路径后，加载so的姿势：\n
 * 1，System.loadLibrary(soName); 无需改变系统load方法，注入路径后照常加载，推荐。\n
 * 2，使用第三方库ReLinker，有so加载成功、失败的回调，安全加载不崩溃。\n
 * 3，System.load(soAbsolutePath);传统方法指定so路径加载，不适合大项目和第三方lib，不灵活，不推荐。\n
 *
 * @author 杜小菜 Created on 2019-05-07 - 11:03.
 * E-mail:duqian2010@gmail.com
 */
class MainActivity : AppCompatActivity() {
    private val sdcardLibDir = Environment.getExternalStorageDirectory().absolutePath
    private var context: Context? = null
    private var fab: FloatingActionButton? = null

    companion object {
        private const val REQUEST_CODE = 1000
        init {
       //     System.loadLibrary("yggdrasilbinder")
        }
    }

    external fun stringFromJNI(): String

    external fun stringFromNative(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        context = this
        isSoExist = false
        applyForPermissions()

        //1，如果没有so就加载，肯定报错
        //loadLibrary();
        findViewById<View>(R.id.btn_load_so)?.setOnClickListener { //2，copy so，测试so动态加载,apk安装时没有的so库
            applyForPermissions()
            startLoadSoFromLocalPath()
        }
        findViewById<View>(R.id.btn_load_so_local)?.setOnClickListener{
            System.loadLibrary("yggdrasilbinder") //工程自带的so
            val msg: String = stringFromJNI()
            toastShort(context, "工程自带的cpp=$msg")
        }
        findViewById<View>(R.id.btn_clear_so)?.setOnClickListener { //3，清除自定义的so路径，杀进程退出app，再重新进入加载必定失败
            clearSoFileAndPath()
            loadLibrary() //click
            restartApp()
        }
    }

    private fun startLoadSoFromLocalPath() {
        //确保so本地存在
        if (isSoExist) {
            realLoadSoFile()
        } else {
            copyAssetsFile()
        }
    }

    private fun realLoadSoFile() {
        val context = context?: return
        val soFrom = soSourcePath
        //注入so路径，如果清除了的话。没有清除可以不用每次注入
        loadSoFile(context, soFrom)
        //加载so库
        loadLibrary()
    }

    private fun clearSoFileAndPath() {
        val context = context?: return
        LoadLibraryUtil.clearSoPath(classLoader)
        val filePath = "$sdcardLibDir/libs"
        val delete = SoUtils.deleteFile(filePath)
        val privateDir = context.getDir("libs", MODE_PRIVATE).absolutePath
        val delete2 = SoUtils.deleteFile(privateDir)
        Log.d("dq-so", "delete all so=$delete,delete private dir=$delete2")
        isSoExist = !delete
    }

    private fun applyForPermissions() { //申请sdcard读写权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val hasWritePermission = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val hasReadPermission = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasWritePermission || !hasReadPermission) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), REQUEST_CODE
            )
        }
    }

    private var isSoExist = false
    private fun copyAssetsFile() { //将so拷贝到sdcard
        val context = context?: return
        val soTest = "$sdcardLibDir/libs/x86/libnonostub.so"
        if (File(soTest).exists()) {
            isSoExist = true
            realLoadSoFile() //直接加载显示，修复第一次点击不加载的问题
            return
        }

        //简单搞个后台线程,copy so,如果copy会失败， 请手动将assets目录的libs文件夹，拷贝到sdcard根目录
        Thread {
            isSoExist = copyAssetsDirectory(context, "libs", "$sdcardLibDir/libs")
            //SoUtils.copyAssetsToSDCard(context, "libs", sdcardLibDir);
            Log.d("dq-so", "sdcardLibDir=$sdcardLibDir，copy from assets $isSoExist")
            if (isSoExist) {
                toastShort(context, "start copy so..please wait...")
                realLoadSoFile()
            } else {
                toastShort(
                    context,
                    "如果拷贝so失败，请手动将assets目录的libs文件夹，拷贝到sdcard根目录"
                )
            }
        }.start()
    }

    /**
     * 在应用启动的时动态注入本地so路径path，待后期so准备好了，可以安全加载。
     * 加载注入path的so文件，以下几种加载的方式都可以
     * 1，使用第三方库ReLinker，有so加载成功或者失败的回调，没有找到so也不会崩溃
     * 2，System.loadLibrary("nonostub"); //系统方法也能正常加载，无法try catch住异常
     * 3，System.load("sdcard getAbsolutePath"); //对应abi的so完整路径也能加载，无法try catch住异常
     * 4，使用LocalSoHelper可以拷贝so文件并load
     */
    private fun loadLibrary() {
        System.loadLibrary("nonostub") //系统方法也能正常加载，无法try catch住异常
        //msg是测试从assets目录拷贝的so的逻辑（模拟网络下载的某个so文件）
        val msg = stringFromNative()
        Log.d("dq-so", "来自动态下发的so=$msg")
        toastShort(context, "来自动态下发的so=$msg")
        runOnUiThread {
            fab?.let {
                Snackbar.make(it, "来自动态下发的so：$msg", Snackbar.LENGTH_SHORT)
                    .setAction("Action", null).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    toast(context, "no permissions for rw sdcard")
                    return
                }
            }
        }
    }

    /**
     * 为了重启
     */
    private fun restartApp() {
        try {
            val packageName = packageName?: return
            val k = context?.packageManager?.getLaunchIntentForPackage(packageName)
            k?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context?.startActivity(k)
            Process.killProcess(Process.myPid())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}