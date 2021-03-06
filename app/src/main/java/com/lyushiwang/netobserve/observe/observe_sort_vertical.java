package com.lyushiwang.netobserve.observe;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.lyushiwang.netobserve.R;
import com.lyushiwang.netobserve.connect.ConnectRobot;
import com.tools.ClassMeasFunction;
import com.tools.ListView_observe_now;
import com.tools.My_Func;
import com.tools.Observe_data;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOError;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by 吕世望 on 2017/5/1.
 */

public class observe_sort_vertical extends AppCompatActivity {

    private StringBuilder survingString;//存储消息
    private EditText editText_in1_name;
    private TextView textView_in1_text;
    private Button button_get_vert_data;
    private Button button_check;
    private Button button_tranfer;
    private ImageButton imageButton_houtui;

    private My_Func my_func = new My_Func();
    private String ProName = new String();

    private Dialog dialog_tip;
    private ProgressDialog PD_transfer;
    private StringBuffer knowing_points = new StringBuffer();//已知点
    private StringBuffer observe_data = new StringBuffer();//观测数据
    private StringBuffer Code_Block = new StringBuffer();
    private String write_content = new String();

    private File file_gsi;
    private File file_in1;

    private HandlerThread thread;
    private Handler handler;
    private Handler MsgHandler;

    private ClassMeasFunction classmeasFun;//GeoCom
    private BluetoothAdapter BluetoothAdap;// 本地蓝牙适配器
    private boolean bound = false;//存储是否绑定
    //绑定服务的连接
    private ServiceConnection contact_sc = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ClassMeasFunction.LocalBinder binder = (ClassMeasFunction.LocalBinder) service;
            classmeasFun = binder.getService();
            bound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bound) {
            bound = false;
            unbindService(contact_sc);
        }
    }

    //绑定监听服务
    private void bindContactService() {
        Intent intent = new Intent(observe_sort_vertical.this, ClassMeasFunction.class);
        bindService(intent, contact_sc, BIND_AUTO_CREATE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.observe_sort_vertical);

        define_palettes();
        init();
        givetip();
        button_check.setOnClickListener(listener_check);
        button_get_vert_data.setOnClickListener(listener_get);
        button_tranfer.setOnClickListener(listener_transfer);
    }

    protected void define_palettes() {
        editText_in1_name = (EditText) findViewById(R.id.editText_in1_name);
        textView_in1_text = (TextView) findViewById(R.id.textView_in1_text);
        button_check = (Button) findViewById(R.id.button_check);
        button_get_vert_data = (Button) findViewById(R.id.button_get_vert_data);
        button_tranfer = (Button) findViewById(R.id.button_transfer);
        imageButton_houtui = (ImageButton) findViewById(R.id.imageButton_houtui);
    }

    //初始化
    @SuppressLint("NewApi")
    @TargetApi(11)
    private void init() {
        BluetoothAdap = BluetoothAdapter.getDefaultAdapter();// 获取本地蓝牙适配器
        bindContactService();
        ProName = null;

        PD_transfer = new ProgressDialog(observe_sort_vertical.this);

        PD_transfer.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        PD_transfer.setTitle("提示");
        PD_transfer.setCanceledOnTouchOutside(false);//dialog弹出后会点击屏幕，dialog不消失；点击物理返回键dialog消失
        PD_transfer.setMessage("正在转换格式，请等待......");
        PD_transfer.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                String filename = ProName + ".gsi";
                if (transfer_data(filename)) {
                    dialog_tip = new AlertDialog.Builder(observe_sort_vertical.this)
                            .setTitle("提示")
                            .setMessage("转换成功！")
                            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog_tip.dismiss();
                                }
                            }).create();
                    dialog_tip.show();
                } else {
                    dialog_tip = new AlertDialog.Builder(observe_sort_vertical.this)
                            .setTitle("提示")
                            .setMessage("转换失败，请检查")
                            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog_tip.dismiss();
                                    handler.removeCallbacks(mRunnable);
                                }
                            }).create();
                    dialog_tip.show();
                    handler.removeCallbacks(mRunnable);
                }
            }
        });

        handler = new Handler();
        MsgHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == 1) {
                    PD_transfer.dismiss();
                }
                if (msg.what == 2) {
                    Toast.makeText(getApplicationContext(), msg.obj.toString(), Toast.LENGTH_LONG).show();
                }
            }
        };
    }

    public void givetip() {
        AlertDialog.Builder AD_tip = new AlertDialog.Builder(observe_sort_vertical.this);
        AD_tip.setTitle("提示").setMessage("输入工程名后，点击“确定”。然后在水准仪上发送数据，提示“接收成功”后点击“获取数据”");
        AlertDialog adg_tip = AD_tip.show();
    }

    Button.OnClickListener listener_check = new Button.OnClickListener() {
        public void onClick(View v) {
            ProName = editText_in1_name.getText().toString();
            if (ProName.equals("") || ProName.equals(null)) {
                AlertDialog.Builder AD_error = new AlertDialog.Builder(observe_sort_vertical.this);
                AlertDialog adg_error = AD_error.setTitle("警告").setMessage("工程名出错！请检查后重新输入").show();
            } else {
                classmeasFun.CleanData();
                makeToast("输入工程名成功！");
            }
        }
    };


    Button.OnClickListener listener_get = new Button.OnClickListener() {
        public void onClick(View v) {
            if (!BluetoothAdap.isEnabled()) {
                android.app.AlertDialog.Builder AD_check_BT = new android.app.AlertDialog.Builder(
                        observe_sort_vertical.this);
                AlertDialog adg_check_BT = AD_check_BT.setMessage("未打开蓝牙！请打开！").show();
            } else {
                AlertDialog.Builder AD_receive = new AlertDialog.Builder(observe_sort_vertical.this);
                AlertDialog adg_receive = AD_receive.setMessage("正在接收文件...").show();
                String data = new String();
                try {
                    data = classmeasFun.receiveData();
                    adg_receive.dismiss();

                    textView_in1_text.setText("");
                    textView_in1_text.setText(ProName + ".GSI\r\n" + data);
                } catch (Exception e) {
                    e.printStackTrace();
                    android.app.AlertDialog.Builder AD_check_measfun = new android.app.AlertDialog.Builder(observe_sort_vertical.this);
                    AD_check_measfun.setMessage("未连接到蓝牙模块！请重试")
                            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent_to_bluedevices = new Intent();
                                    intent_to_bluedevices.setClass(
                                            observe_sort_vertical.this, ConnectRobot.class);
                                    startActivity(intent_to_bluedevices);
                                }
                            }).create().show();
                }

                if (!data.equals(null) && !data.equals("")) {
                    file_gsi = new File(my_func.get_main_file_path() + "/" + ProName,
                            ProName + ".GSI");
                    try {
                        if (!file_gsi.exists()) {
                            file_gsi.createNewFile();
                        }
                        BufferedWriter bw = new BufferedWriter(new FileWriter(file_gsi, true));
                        bw.flush();
                        bw.write(data);
                        bw.flush();
                        bw.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    AlertDialog.Builder AD_data_error = new AlertDialog.Builder(observe_sort_vertical.this);
                    AlertDialog adg_data_error = AD_data_error.setTitle("警告").setMessage("接收的数据出现问题！").show();
                }
            }
        }
    };

    Button.OnClickListener listener_transfer = new Button.OnClickListener() {
        public void onClick(View v) {
            final AlertDialog.Builder AD_transfer = new AlertDialog.Builder(observe_sort_vertical.this);
            AD_transfer.setMessage("是否将" + ProName + ".GSI文件转为.in1文件？")
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            file_in1 = new File(my_func.get_main_file_path() + "/" + ProName,
                                    ProName + ".in1");
                            try {
                                if (!file_in1.exists()) {
                                    file_in1.createNewFile();
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            PD_transfer.show();
                            thread = new HandlerThread("MyHandlerThread");
                            thread.start();
                            handler = new Handler(thread.getLooper());
                            handler.post(mRunnable);
                        }
                    }).show();
        }
    };

    public boolean transfer_data(String GSIFileName) {
        boolean istransfered = false;

        //File file_gsi=new File(my_func.get_main_file_path()+"/"+ProName,
        //      ProName+".GSI");

        //test
        File file_gsi = new File(my_func.get_main_file_path() + "/" + "0709",
                "0709.gsi");

        try {
            BufferedReader gsi_reader = new BufferedReader(new FileReader(file_gsi));
            String read_line = "";
            while ((read_line = gsi_reader.readLine()) != null) {
                byte byte_test = read_line.getBytes("UTF-8")[0];
                System.out.println(byte_test);

                if (byte_test != 26) {
                    String first_data_word = read_line.split(" ")[0];
                    String Word_Index = first_data_word.substring(0, 2);
                    //String row_number=first_data_word.substring(3,first_data_word.length());
                    if (Word_Index.equals("41")) {
                        Integer row_number = Integer.valueOf(first_data_word.substring(2, 6));
                        if (row_number != 1) {//每一个41模块的结尾，进行数据读取
                            if (handle_41_block(Code_Block)) {
                                Code_Block = new StringBuffer();
                            } else {
                                System.out.println("Failed handling 41 data block!");
                                makeToast("处理数据失败！");
                            }
                        }
                    }
                    Code_Block.append(read_line + "\n");
                } else {//文件已读完
                    if (handle_41_block(Code_Block)) {
                        System.out.println("The file is over!The last string is \"SUB\"!");
                    }
                }
            }

            //接下来把observe_data里面的数据写入到.in1文件中
            try {
                BufferedWriter bw_in1 = new BufferedWriter(new FileWriter(file_in1));
                if (knowing_points.length() != 0) {
                    if (observe_data.length() != 0) {
                        bw_in1.flush();
                        bw_in1.write(knowing_points.toString());
                        bw_in1.write(observe_data.toString());
                        bw_in1.flush();
                    } else {
                        makeToast("未找到观测点高程！请重试");
                    }
                } else {
                    makeToast("未找到已知点高程！请重试");
                }

                istransfered = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
            makeToast("读取.gsi文件出错！");
        }

        return istransfered;
    }

    public boolean handle_41_block(StringBuffer Code_Block1) {
        String[] Code_Block = Code_Block1.toString().split("\n");
        boolean ishandled = false;


        try {
            String Word_Index = new String();

            String[] first_line = String.valueOf(Code_Block[1]).split(" ");
            String name_code = first_line[0];
            String start_point_name = name_code.substring(7, name_code.length()).replaceFirst("0*", "");//去掉左边的零
            if (start_point_name == "") {
                start_point_name = "O";
            }
            String second_data_word = first_line[1];
            Word_Index = second_data_word.substring(0, 2);
            if (Word_Index.equals("83")) {//第二个Data word information的开头是“83”，是已知点
                String height_data = get_measurement_data(second_data_word);
                knowing_points.append(start_point_name + "," + height_data + "\n");
            }

            String[] end_line = String.valueOf(Code_Block[Code_Block.length - 1]).split(" ");
            name_code = end_line[0];
            String end_point_name = name_code.substring(7, name_code.length()).replaceFirst("0*", "");//去掉左边的零
            if (end_point_name == "") {
                end_point_name = "O";
            }
            String distance_code = end_line[4];
            Double data_distance_code = Double.valueOf(get_measurement_data(distance_code)) / 1000;
            DecimalFormat df_data = new DecimalFormat("0.00000000");
            String distance_data = df_data.format(data_distance_code);

            String measure_height_code = end_line[5];
            String measure_height = get_measurement_data(measure_height_code);

            observe_data.append(start_point_name + "," + end_point_name + "," + measure_height + "," + distance_data + "\n");

            ishandled = true;
        } catch (Error e) {
            e.printStackTrace();
            ishandled = false;
        }
        return ishandled;
    }

    public String get_measurement_data(String data_word) {
        String unit_number = String.valueOf(data_word.charAt(5));

        String data = data_word.substring(7, data_word.length()).replaceFirst("^0*", "");//去掉左边的零
        if (data.equals("")) {
            data = "0";
        }

        switch (unit_number) {
            case "0":
                data = String.valueOf(Double.valueOf(data) / 1000);
                break;
            case "6":
                data = String.valueOf(Double.valueOf(data) / 10000);
                break;
            case "8":
                data = String.valueOf(Double.valueOf(data) / 100000);
                break;
            default:
                data = "error";
        }

        return data;
    }

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            Message msg2 = new Message();
            msg2.what = 1;
            MsgHandler.sendMessage(msg2);
        }
    };

    public void makeToast(String text) {
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
    }
}
