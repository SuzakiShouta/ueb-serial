package com.example.python

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import co.dobot.magicain.client.DobotMessageClient
import co.dobot.magicain.message.DobotMessage
import co.dobot.magicain.message.cmd.CMDParams.PTPCmd
import co.dobot.magicain.message.cmd.CMDParams.PTPMode
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber


class MainActivity : AppCompatActivity() {
    private val ACTION_USB_PERMISSION = "com.example.USB_PERMISSION"
    private lateinit var mUsbManager: UsbManager
    private lateinit var mUsbDevice: UsbDevice
    private lateinit var mPermissionIntent: PendingIntent
    private var mUsbDeviceConnection: UsbDeviceConnection? = null
    private val YOUR_VENDOR_ID = 4292
    private val YOUR_PRODUCT_ID = 60000
    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        // Pythonコードを実行する前にPython.start()の呼び出しが必要
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        val upButton: Button = findViewById(R.id.button_arm_up)
        val leftButton: Button = findViewById(R.id.button_arm_left)
        val rightButton: Button = findViewById(R.id.button_arm_right)
        val downButton: Button = findViewById(R.id.button_arm_down)
        logText = findViewById(R.id.text_log)

        logText.text = "Logが出力されます!"

        fun openDevice() {
            mUsbManager = getSystemService(Context.USB_SERVICE) as UsbManager

            // USBデバイスにアクセスするためのBroadcast Intentを作成
            mPermissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE)

            // USBデバイスの検索とリクエスト
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            registerReceiver(mUsbReceiver, filter)
            val deviceList = mUsbManager.deviceList
            addLogText(deviceList.toString())
            for (device in deviceList.values) {
                if (device.vendorId == YOUR_VENDOR_ID && device.productId == YOUR_PRODUCT_ID) {
                    mUsbDevice = device
                    addLogText(mUsbDevice.toString())
                    mUsbManager.requestPermission(mUsbDevice, mPermissionIntent)
                    break
                }
            }
        }

        upButton.setOnClickListener {
            try {
                openDevice()
//                val py = Python.getInstance()
//                val pydobot = py.getModule("basic_example")
//                pydobot.callAttr("start_dobot")
            } catch (e: PyException) {
                addLogText(e.message.toString())
            }
        }

    }

    // USBデバイスへの権限リクエストのBroadcast Receiver
    private val mUsbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                    addLogText("Action: $action")
                    addLogText("Device: $device")
                    addLogText("Permission Granted: $permissionGranted")

                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (!granted) {
                        addLogText("拒否")
                    }

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // ユーザーがアクセスを許可した場合、USBデバイスにアクセスする
                            mUsbDevice = device
                            mUsbDeviceConnection = mUsbManager.openDevice(mUsbDevice)
                            if (mUsbDeviceConnection != null) {
                                val usbInterface: UsbInterface = mUsbDevice.getInterface(0)
                                mUsbDeviceConnection!!.claimInterface(usbInterface, true)
                                val outEndpoint = usbInterface.getEndpoint(1)
                                move(mUsbDeviceConnection!!, outEndpoint)
                                try {
//                                    val py = Python.getInstance()
//                                    val pydobot = py.getModule("basic_example")
//                                    pydobot.callAttr("start_dobot")
                                } catch (e: PyException) {
                                    addLogText(e.message.toString())
                                }
                            } else {
                                addLogText("failed to open USB device")
                            }
                        } else {
                            addLogText("USB device is null")
                        }
                    } else {
                        addLogText("permission denied for device $device")
                    }
                }
            }
        }
    }

    fun addLogText(text: String) {
        logText.text = logText.text.toString().plus("\n\n").plus(text)
    }

    fun move(connection: UsbDeviceConnection, outEndpoint: UsbEndpoint) {
        val clearMessage = DobotMessage()
        clearMessage.cmdClearQueue()
        DobotMessageClient.Instance().sendMessage(clearMessage)
        val startQueue = DobotMessage()
        startQueue.cmdStartQueue()
        DobotMessageClient.Instance().sendMessage(startQueue)
        val ptpCmd1 = PTPCmd()
        ptpCmd1.ptpMode = PTPMode.MOVE_L
        ptpCmd1.x = 250f
        ptpCmd1.y = 20f
        ptpCmd1.z = 0f
        ptpCmd1.r = 0f
        val ptpCmd2 = PTPCmd(PTPMode.MOVE_L, 250f, -20f, 0f, 0f)
        val ptpCmd3 = PTPCmd(PTPMode.MOVE_L, 200f, -20f, 0f, 0f)
        val ptpCmd4 = PTPCmd(PTPMode.MOVE_L, 200f, 20f, 0f, 0f)
        val count = 60

        val ptpCmds = arrayOf(
            ptpCmd1, ptpCmd2, ptpCmd3, ptpCmd4
        )

        for (i in 0 until count) {
            val message = DobotMessage()
            message.cmdPTP(ptpCmds[i % 4])
            val buffer = message.data
            addLogText(buffer.toString())
            addLogText(message.toString())
            val result = connection.bulkTransfer(outEndpoint, buffer, buffer.size, 100)
            addLogText(result.toString())
            DobotMessageClient.Instance().sendMessage(message)
        }
    }

    fun move2(connection: UsbDeviceConnection, outEndpoint: UsbEndpoint){
        val arduinoPort = "/dev/ttyACM0"
        val baudRate = 9600
        val dataBits = 8
        val stopBits = 1
        val parity = UsbSerialPort.PARITY_NONE

        // USB通信の設定
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val usbDriver = UsbSerialProber.getDefaultProber().probeDevice(usbManager.deviceList.values.first())
        val usbConnection = usbManager.openDevice(usbDriver.device)
        val usbSerialPort = usbDriver.ports[0]
        usbSerialPort.open(usbConnection)
        usbSerialPort.setParameters(baudRate, dataBits, stopBits, parity)

        // Arduinoに命令を送信
        val command = "Hello, Arduino!"
        val commandBytes = command.toByteArray()
        usbSerialPort.write(commandBytes, 1000)

    }
}