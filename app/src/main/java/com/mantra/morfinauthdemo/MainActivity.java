package com.mantra.morfinauthdemo;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


import com.mantra.morfinauth.MorfinAuth;
import com.mantra.morfinauth.DeviceInfo;
import com.mantra.morfinauth.MorfinAuth_Callback;
import com.mantra.morfinauth.enums.DeviceDetection;
import com.mantra.morfinauth.enums.DeviceModel;
import com.mantra.morfinauth.enums.LogLevel;

public class MainActivity extends AppCompatActivity implements MorfinAuth_Callback {

    private MorfinAuth morfinAuth;
    private DeviceInfo lastDeviceInfo;
    private DeviceModel connectedDeviceModel;

    private boolean isInitRunning = false;
    private boolean isUninitRunning = false;
    private boolean isStartCaptureRunning = false;
    private boolean isStopCaptureRunning = false;

    private TextView txtStatus;
    private Button btnInit, btnUninit, btnStartCapture, btnStopCapture, btnSyncCapture;

    private Thread captureThread;

    private ImageView imgFinger;

    private String clientKey = "";

    private int minQuality = 60;
    private int timeOut = 10000;

    private int captureCount = 0;
    private static final int MAX_FINGERS = 10;
    private boolean isContinuousCaptureMode = false;
    private boolean stopCaptureRequested = false;


    private volatile boolean shouldContinueCapture = true;





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        txtStatus = findViewById(R.id.txtStatus);
        btnInit = findViewById(R.id.btnInit);
        btnUninit = findViewById(R.id.btnUninit);
        btnStartCapture = findViewById(R.id.btnStartCapture);
        btnStopCapture = findViewById(R.id.btnStopCapture);
        imgFinger = findViewById(R.id.imgFinger);
        btnSyncCapture = findViewById(R.id.btnSyncCapture);

        btnUninit.setEnabled(false);
        btnStartCapture.setEnabled(false);
        btnStopCapture.setEnabled(false);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        morfinAuth = new MorfinAuth(this, this);
        String logPath = getExternalFilesDir(null).toString();
        morfinAuth.SetLogProperties(logPath, LogLevel.DEBUG);

        txtStatus.setText(R.string.status_disconnected);

        setupInitClick();
        setupUninitClick();
        setupStartCaptureClick();
        setupStopCaptureClick();
        setupSyncCaptureClick();
    }


    private void setupInitClick() {
        btnInit.setOnClickListener(v -> {
            if (isInitRunning) {
                txtStatus.setText("Status : Init already running...");
                return;
            }


            if (connectedDeviceModel == null) {
                txtStatus.setText("Status : No supported device detected\nConnect MFS500, MARC10, or MELO31");
                return;
            }

            txtStatus.setText("Status : Initializing " + connectedDeviceModel.name() + "...");
            btnInit.setEnabled(false);

            new Thread(() -> {
                try {
                    isInitRunning = true;
                    DeviceInfo info = new DeviceInfo();
                    String key = (clientKey == null || clientKey.isEmpty()) ? null : clientKey;


                    int ret = morfinAuth.Init(connectedDeviceModel, key, info);

                    runOnUiThread(() -> {
                        if (ret == 0) {
                            lastDeviceInfo = info;
                            setDeviceInfo(info);
                            btnUninit.setEnabled(true);
                            btnInit.setEnabled(false);
                            btnStartCapture.setEnabled(true);
                        } else {
                            txtStatus.setText(
                                    "Status : INIT FAILED (" + ret + ")\n" +
                                            morfinAuth.GetErrorMessage(ret)
                            );
                            btnInit.setEnabled(true);
                        }
                        isInitRunning = false;
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        txtStatus.setText("Status : Error\n" + e.getMessage());
                        btnInit.setEnabled(true);
                        isInitRunning = false;
                    });
                }
            }).start();
        });
    }

    private void setupUninitClick() {
        btnUninit.setOnClickListener(v -> {
            if (isUninitRunning) {
                txtStatus.setText("Status : Uninit already running...");
                return;
            }

            txtStatus.setText("Status : Uninitializing...");
            btnUninit.setEnabled(false);
            isUninitRunning = true;

            new Thread(() -> {
                try {
                    int ret = morfinAuth.Uninit();
                    isStartCaptureRunning = false;
                    isStopCaptureRunning = false;

                    runOnUiThread(() -> {
                        if (ret == 0) {
                            txtStatus.setText("Status : UNINIT SUCCESS");
                            lastDeviceInfo = null;
                            imgFinger.setImageDrawable(null);
                            setClearDeviceInfo();
                            btnInit.setEnabled(true);
                            btnUninit.setEnabled(false);
                            btnStartCapture.setEnabled(false);
                            btnStopCapture.setEnabled(false);
                        } else {
                            txtStatus.setText(
                                    "Status : UNINIT FAILED (" + ret + ")\n" +
                                            morfinAuth.GetErrorMessage(ret)
                            );
                            btnUninit.setEnabled(true);
                            btnStopCapture.setEnabled(false);
                        }
                        isUninitRunning = false;
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        txtStatus.setText("Status : UNINIT ERROR\n" + e.getMessage());
                        btnUninit.setEnabled(true);
                        btnStopCapture.setEnabled(false);
                        isUninitRunning = false;
                    });
                }
            }).start();
        });
    }

    private void setupStartCaptureClick() {
        btnStartCapture.setOnClickListener(v -> {

            if (isStartCaptureRunning) {
                txtStatus.setText("Status : Capture already running...");
                return;
            }

            if (isStopCaptureRunning) {
                txtStatus.setText("Status : Stop capture in progress...");
                return;
            }

            if (lastDeviceInfo == null) {
                txtStatus.setText("Status : Please run device init first");
                return;
            }


            captureCount = 0;
            stopCaptureRequested = false;

            imgFinger.setImageResource(android.R.color.white);
            txtStatus.setText("Status : FINGER 1/10\nPlace your finger on sensor");
            btnStartCapture.setEnabled(false);
            btnStopCapture.setEnabled(true);
            isStartCaptureRunning = true;

            try {
                int ret = morfinAuth.StartCapture(minQuality, timeOut);

                if (ret != 0) {
                    isStartCaptureRunning = false;
                    btnStartCapture.setEnabled(true);
                    btnStopCapture.setEnabled(false);
                    txtStatus.setText(
                            "Status : START CAPTURE FAILED (" + ret + ")\n" +
                                    morfinAuth.GetErrorMessage(ret)
                    );
                }

            } catch (Exception e) {
                e.printStackTrace();
                isStartCaptureRunning = false;
                btnStartCapture.setEnabled(true);
                btnStopCapture.setEnabled(false);
                txtStatus.setText("Status : Error starting capture\n" + e.getMessage());
            }
        });
    }


    private void setupStopCaptureClick() {
        btnStopCapture.setOnClickListener(v -> {
            if (isStopCaptureRunning) {
                txtStatus.setText("Status : Stop capture already running...");
                return;
            }

            stopCaptureRequested = true;

            txtStatus.setText("Status : Stopping capture...");
            btnStopCapture.setEnabled(false);
            isStopCaptureRunning = true;

            new Thread(() -> {
                try {
                    int ret = morfinAuth.StopCapture();

                    runOnUiThread(() -> {
                        if (ret == 0) {
                            String message = String.format(
                                    "Status : Manually stopped\nCaptured %d/10 fingers",
                                    captureCount
                            );
                            txtStatus.setText(message);
                            finishCaptureSession();
                        } else {
                            txtStatus.setText(
                                    "Status : STOP CAPTURE FAILED (" + ret + ")\n" +
                                            morfinAuth.GetErrorMessage(ret)
                            );
                            btnStopCapture.setEnabled(true);
                            stopCaptureRequested = false;
                        }
                        isStopCaptureRunning = false;
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        txtStatus.setText("Status : Error stopping capture\n" + e.getMessage());
                        btnStopCapture.setEnabled(true);
                        stopCaptureRequested = false;
                        isStopCaptureRunning = false;
                    });
                }
            }).start();
        });
    }



    private void setupSyncCaptureClick() {
        btnSyncCapture.setOnClickListener(v -> {


            if (isStartCaptureRunning || (captureThread != null && captureThread.isAlive())) {
                txtStatus.setText("Status : capture already running...");
                return;
            }

            if (lastDeviceInfo == null) {
                txtStatus.setText("Status : Please run device init first");
                return;
            }

            imgFinger.setImageResource(android.R.color.white);
            txtStatus.setText("Status : Starting AutoCapture...\nPlace your finger on sensor");
            btnSyncCapture.setEnabled(false);
            btnStartCapture.setEnabled(false);
            btnStopCapture.setEnabled(false);
            isStartCaptureRunning = true;


            captureThread = new Thread(() -> {
                try {

                    int[] qty = new int[1];
                    int[] nfiq = new int[1];


                    int ret = morfinAuth.AutoCapture(minQuality, timeOut, qty, nfiq);

                    runOnUiThread(() -> {
                        if (ret == 0) {

                            txtStatus.setText(
                                    "Status : AUTOCAPTURE SUCCESS\n" +
                                            "Quality: " + qty[0] + "\n" +
                                            "NFIQ: " + nfiq[0]
                            );
                            btnStartCapture.setEnabled(true);
                            btnStopCapture.setEnabled(false);
                        } else {

                            if (ret == -2057) {
                                txtStatus.setText("Status : Device not connected");
                            } else {
                                txtStatus.setText(
                                        "Status : AUTOCAPTURE FAILED (" + ret + ")\n" +
                                                morfinAuth.GetErrorMessage(ret)
                                );
                            }
                            btnStartCapture.setEnabled(true);
                            btnStopCapture.setEnabled(false);
                        }
                        btnSyncCapture.setEnabled(true);
                        isStartCaptureRunning = false;
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        txtStatus.setText("Status : Error in AutoCapture\n" + e.getMessage());
                        btnSyncCapture.setEnabled(true);
                        btnStartCapture.setEnabled(true);
                        isStartCaptureRunning = false;
                    });
                } finally {
                    captureThread = null;
                }
            });

            captureThread.start();
        });
    }



    private void setDeviceInfo(DeviceInfo info) {
        runOnUiThread(() -> {
            try {
                if (info == null) return;

                String model = (info.Model != null) ? info.Model : "N/A";
                String make = (info.Make != null) ? info.Make : "N/A";
                String serial = (info.SerialNo != null) ? info.SerialNo : "N/A";

                txtStatus.setText(
                        "Status : INIT SUCCESS\n" +
                                "Model: " + model + "\n" +
                                "Make: " + make + "\n" +
                                "Serial: " + serial + "\n" +
                                "Size (W * H) :" + info.Width + "x" + info.Height
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }


    private void setClearDeviceInfo() {
        runOnUiThread(() -> {
            txtStatus.setText(R.string.status_disconnected);
        });
    }


    @Override
    public void OnDeviceDetection(String deviceName, DeviceDetection detection) {
        isStartCaptureRunning = false;
        isStopCaptureRunning = false;

        runOnUiThread(() -> {
            if (detection == DeviceDetection.CONNECTED) {

                try {
                    connectedDeviceModel = DeviceModel.valueFor(deviceName);

                    txtStatus.setText("Status : Device connected - " + deviceName);

                    if (lastDeviceInfo == null && !isInitRunning) {
                        btnInit.setEnabled(true);
                    }
                } catch (Exception e) {
                    txtStatus.setText("Status : Unknown device connected - " + deviceName);
                }
            } else if (detection == DeviceDetection.DISCONNECTED) {

                try {

                    isStartCaptureRunning = false;
                    isStopCaptureRunning = false;

                    lastDeviceInfo = null;
                    connectedDeviceModel = null;
                    txtStatus.setText(R.string.status_disconnected);
                    btnInit.setEnabled(false);
                    btnUninit.setEnabled(false);
                    btnStartCapture.setEnabled(false);
                    btnStopCapture.setEnabled(false);
                    imgFinger.setImageDrawable(null);

                    try {
                        morfinAuth.StopCapture();
                        morfinAuth.Uninit();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    setClearDeviceInfo();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void OnPreview(int errorCode, int quality, byte[] image) {
        if (errorCode == 0 && image != null && image.length > 0) {

            try {
                android.graphics.Bitmap previewBitmap = android.graphics.BitmapFactory.decodeByteArray(
                        image, 0, image.length
                );

                runOnUiThread(() -> {

                    imgFinger.setImageBitmap(previewBitmap);
                    txtStatus.setText("Status : Preview\nQuality: " + quality);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {

            runOnUiThread(() -> {
                if (errorCode == -2057) {
                    txtStatus.setText("Status : Device not connected");
                } else {
                    txtStatus.setText(
                            "Status : Preview Error (" + errorCode + ")\n" +
                                    morfinAuth.GetErrorMessage(errorCode)
                    );
                }
            });
        }
    }



    @Override
    public void OnComplete(int errorCode, int Quality, int NFIQ) {
        try {
            if (errorCode == 0) {

                captureCount++;

                runOnUiThread(() -> {
                    String status = String.format(
                            "Status : FINGER %d/10 SUCCESS\nQuality: %d\nNFIQ: %d",
                            captureCount,
                            Quality,
                            NFIQ
                    );
                    txtStatus.setText(status);


                    if (captureCount >= MAX_FINGERS) {
                        isStartCaptureRunning = false;
                        finishCaptureSession();
                    } else if (!stopCaptureRequested) {
                        restartCaptureForNextFinger();
                    } else {
                        isStartCaptureRunning = false;
                        finishCaptureSession();
                    }
                });

            } else {

                isStartCaptureRunning = false;

                runOnUiThread(() -> {

                    String errorMsg = String.format(
                            " ERROR CODE: %d\n\n" +
                                    "Message: %s\n\n" +
                                    "Quality: %d\nNFIQ: %d",
                            errorCode,
                            morfinAuth.GetErrorMessage(errorCode),
                            Quality,
                            NFIQ
                    );
                    txtStatus.setText(errorMsg);


//                    if (errorCode == -2057) {
//                        txtStatus.setText("Status : Device not connected");
//                    } else {
//                        txtStatus.setText(
//                                "Status : CAPTURE FAILED (" + errorCode + ")\n" +
//                                        morfinAuth.GetErrorMessage(errorCode)
//                        );
//                    }
//                    finishCaptureSession();

                    btnStartCapture.setEnabled(true);
                    btnStopCapture.setEnabled(false);
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            isStartCaptureRunning = false;
            runOnUiThread(() -> {
                txtStatus.setText("Status : Error in OnComplete\n" + e.getMessage());
                finishCaptureSession();
            });
        }
    }

    private void restartCaptureForNextFinger() {
        try {

            imgFinger.setImageResource(android.R.color.white);


            txtStatus.setText(
                    String.format(
                            "Status : FINGER %d/10\nPlace your finger on sensor",
                            captureCount + 1
                    )
            );

            int ret = morfinAuth.StartCapture(minQuality, timeOut);

            if (ret != 0) {
                isStartCaptureRunning = false;
                txtStatus.setText(
                        "Status : START CAPTURE FAILED (" + ret + ")\n" +
                                morfinAuth.GetErrorMessage(ret)
                );
                finishCaptureSession();
            } else {

                isStartCaptureRunning = true;
            }

        } catch (Exception e) {
            e.printStackTrace();
            isStartCaptureRunning = false;
            txtStatus.setText("Status : Error restarting capture\n" + e.getMessage());
            finishCaptureSession();
        }
    }






    private void finishCaptureSession() {
        isStartCaptureRunning = false;
        stopCaptureRequested = false;

        btnStartCapture.setEnabled(true);
        btnStopCapture.setEnabled(false);

        String finalMessage = String.format(
                "Status : Capture Complete\nTotal fingers: %d/10",
                captureCount
        );
        txtStatus.setText(finalMessage);

        Log.d("CaptureSession", "Finished. Captured " + captureCount + " fingers");
    }



    @Override
    public void OnFingerPosition(int errorCode, int position) {
        // OnFingerPosition
    }

    @Override
    protected void onStop() {
        Log.e("MainActivity", "onStop");
        if (isStartCaptureRunning) {
            try {
                morfinAuth.StopCapture();
                isStartCaptureRunning = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        super.onStop();
    }


    @Override
    public void onBackPressed() {
        Log.e("MainActivity", "onBackPressed");
        if (isStartCaptureRunning) {
            try {
                morfinAuth.StopCapture();
                isStartCaptureRunning = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.e("MainActivity", "onPause");
        if (isStartCaptureRunning) {
            try {
                morfinAuth.StopCapture();
                isStartCaptureRunning = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    protected void onDestroy() {
        Log.e("MainActivity", "onDestroy");
        try {
            isStartCaptureRunning = false;
            isStopCaptureRunning = false;
            if (morfinAuth != null) {
                morfinAuth.Uninit();
                morfinAuth.Dispose();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }
}
