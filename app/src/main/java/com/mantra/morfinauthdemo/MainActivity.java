package com.mantra.morfinauthdemo;

import android.database.Cursor;
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
import com.mantra.morfinauth.enums.ImageFormat;
import com.mantra.morfinauth.enums.LogLevel;
import com.mantra.morfinauth.enums.TemplateFormat;


import java.io.File;
import java.io.FileOutputStream;



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

    private boolean stopCaptureRequested = false;
    private boolean isAutoCaptureMode = false;


    private String currentSessionFolder;

    private enum ScannerAction {
        ENROLL,
        MATCH
    }


    private ScannerAction currentAction = ScannerAction.ENROLL;
    private FingerprintDatabaseHelper dbHelper;
    private byte[] lastCapturedTemplate = null;
    private byte[] lastCapturedImage = null;


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


        dbHelper = new FingerprintDatabaseHelper(this);
        Log.d("MainActivity", " Database initialized");

        txtStatus.setText(R.string.status_disconnected);

        setupInitClick();
        setupUninitClick();
        setupStartCaptureClick();
        setupStopCaptureClick();
        setupSyncCaptureClick();
        setupMatchFingerClick();

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
                            btnSyncCapture.setEnabled(true);
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


//    private String generateAutoUserId() {
//        int count = dbHelper.getUserCount();
//        return String.format("USER_%03d", count + 1);
//    }


//    private String getUserNameFromId(String userId) {
//        return dbHelper.getUserNameById(userId);
//    }



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


            currentSessionFolder = createSessionFolder();
            if (currentSessionFolder == null) {
                txtStatus.setText("Status : Failed to create storage folder");
                return;
            }

            captureCount = 0;
            stopCaptureRequested = false;
            isAutoCaptureMode = false;

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

            if (isAutoCaptureMode) {
                txtStatus.setText("Status : Stopping AutoCapture...");
                btnStopCapture.setEnabled(false);
            } else {
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
                                finishCaptureSession(message);
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
            }
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


            currentSessionFolder = createSessionFolder();
            if (currentSessionFolder == null) {
                txtStatus.setText("Status : Failed to create storage folder");
                return;
            }

            captureCount = 0;
            stopCaptureRequested = false;
            isAutoCaptureMode = true;

            imgFinger.setImageResource(android.R.color.white);
            txtStatus.setText("Status : Starting AutoCapture...\nPlace your finger on sensor");
            btnSyncCapture.setEnabled(false);
            btnStartCapture.setEnabled(false);
            btnStopCapture.setEnabled(true);
            isStartCaptureRunning = true;

            captureThread = new Thread(() -> {
                try {

                    while (captureCount < MAX_FINGERS && !stopCaptureRequested) {
                        int[] qty = new int[1];
                        int[] nfiq = new int[1];

                        int ret = morfinAuth.AutoCapture(minQuality, timeOut, qty, nfiq);

                        if (ret == 0) {
                            captureCount++;

                            saveImageToStorage(captureCount, qty[0], nfiq[0]);


                            runOnUiThread(() -> {
                                if (captureCount < MAX_FINGERS) {
                                    txtStatus.setText(
                                            String.format(
                                                    "Status : FINGER %d/10 SAVED\nQuality: %d\nNFIQ: %d\n\nPlace next finger...",
                                                    captureCount,
                                                    qty[0],
                                                    nfiq[0]
                                            )
                                    );
                                } else {
                                    txtStatus.setText(
                                            String.format(
                                                    "Status : FINGER %d/10 SAVED\nQuality: %d\nNFIQ: %d\n\nAll 10 fingers captured!",
                                                    captureCount,
                                                    qty[0],
                                                    nfiq[0]
                                            )
                                    );
                                }
                            });
                        } else if (ret == -2019) {
                            if (!stopCaptureRequested && captureCount < MAX_FINGERS) {
                                runOnUiThread(() -> {
                                    txtStatus.setText(
                                            String.format(
                                                    "Status : Timeout\nRetrying FINGER %d/10\nPlace your finger...",
                                                    captureCount + 1
                                            )
                                    );
                                });
                            }
                        } else if (ret == -2057) {
                            runOnUiThread(() -> {
                                txtStatus.setText("Status : Device not connected");
                            });
                            finishAutoCaptureSession();
                        } else {
                            runOnUiThread(() -> {
                                txtStatus.setText(
                                        String.format(
                                                "Status : AUTOCAPTURE FAILED (%d)\n%s\nCaptured %d/10 fingers",
                                                ret,
                                                morfinAuth.GetErrorMessage(ret),
                                                captureCount
                                        )
                                );
                            });
                            finishAutoCaptureSession();
                        }

                        if (ret == -2019) {
                            continue;
                        }

                        if (ret != 0) {
                            break;
                        }

                        if (stopCaptureRequested) {
                            break;
                        }
                    }

                    runOnUiThread(() -> {
                        if (stopCaptureRequested) {
                            String message = String.format(
                                    "Status : Manually stopped\nSaved %d/10 fingers",
                                    captureCount
                            );
                            finishAutoCaptureSession(message);
                        } else if (captureCount >= MAX_FINGERS) {
                            finishAutoCaptureSession(
                                    "Status : AUTOCAPTURE COMPLETE\nSaved 10/10 fingers"
                            );
                        } else {
                            finishAutoCaptureSession();
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        txtStatus.setText("Status : Error in AutoCapture\n" + e.getMessage());
                        finishAutoCaptureSession();
                    });
                } finally {
                    captureThread = null;
                    isAutoCaptureMode = false;
                }
            });

            captureThread.start();
        });
    }

    private void finishAutoCaptureSession() {
        finishAutoCaptureSession(null);
    }

    private void finishAutoCaptureSession(String customMessage) {
        isStartCaptureRunning = false;
        stopCaptureRequested = false;

        btnSyncCapture.setEnabled(true);
        btnStartCapture.setEnabled(true);
        btnStopCapture.setEnabled(false);

        if (customMessage != null && !customMessage.isEmpty()) {
            txtStatus.setText(customMessage);
        } else {
            String finalMessage = String.format(
                    "Status : AutoCapture Complete\n" +
                            " Saved %d/10 images\n" +
                            "Location:\n%s",
                    captureCount,
                    currentSessionFolder
            );
            txtStatus.setText(finalMessage);
        }

        Log.d("AutoCaptureSession", "Finished. Saved " + captureCount + " images to " + currentSessionFolder);
    }

    private String createSessionFolder() {
        try {

            String timestamp = new java.text.SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    java.util.Locale.US
            ).format(new java.util.Date());


            File storageDir;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {

                File[] dirs = getExternalMediaDirs();


                storageDir = (dirs != null && dirs.length > 0) ? dirs[0] : null;


                if (storageDir == null) {
                    storageDir = getExternalFilesDir(null);
                }
            } else {

                storageDir = getExternalFilesDir(null);
            }


            String folderPath = storageDir.getAbsolutePath() +
                    File.separator + "FingerData" +
                    File.separator + timestamp;


            File folder = new File(folderPath);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            Log.d("SessionFolder", "Created session folder: " + folderPath);

            return folderPath;

        } catch (Exception e) {

            Log.e("SessionFolder", "Failed to create folder", e);
            e.printStackTrace();
            return null;
        }
    }

    private byte[] getTemplateFromCapture() {
        try {
            int size = lastDeviceInfo.Width * lastDeviceInfo.Height + 1111;
            byte[] bTemplateData = new byte[size];
            int[] tSize = new int[1];


            int ret = morfinAuth.GetTemplate(bTemplateData, tSize, TemplateFormat.FMR_V2011);

            if (ret == 0) {
                byte[] template = new byte[tSize[0]];
                System.arraycopy(bTemplateData, 0, template, 0, tSize[0]);
                Log.d("Template", "Template size: " + tSize[0] + " bytes");
                return template;
            } else {
                Log.e("Template", "GetTemplate failed: " + ret);
                return null;
            }
        } catch (Exception e) {
            Log.e("Template", "Error getting template", e);
            return null;
        }
    }





    private void saveImageToStorage(int fingerNumber, int quality, int nfiq) {
        try {
            int size = lastDeviceInfo.Width * lastDeviceInfo.Height + 1111;
            int[] iSize = new int[1];
            byte[] bImage1 = new byte[size];

            int ret = morfinAuth.GetImage(bImage1, iSize, 1, ImageFormat.BMP);

            if (ret == 0) {
                byte[] bImage = new byte[iSize[0]];
                System.arraycopy(bImage1, 0, bImage, 0, iSize[0]);


                String fileName = "finger_" + fingerNumber + ".jpg";
                String filePath = currentSessionFolder + File.separator + fileName;
                FileOutputStream fos = new FileOutputStream(filePath);
                fos.write(bImage);
                fos.close();

                Log.d("ImageCapture", "Saved finger " + fingerNumber +
                        " (Quality:" + quality + ", NFIQ:" + nfiq +
                        ", Size:" + iSize[0] + " bytes) to " + filePath);


                byte[] template = getTemplateFromCapture();
                if (template != null) {
                    String userId = dbHelper.saveFingerprint(bImage, template, quality, nfiq);
                    Log.d("Database", "Saved to DB: " + userId);
                }

                bImage = null;

            } else {
                Log.e("ImageCapture", "GetImage failed with code: " + ret);
            }

        } catch (Exception e) {
            Log.e("ImageCapture", "Error saving image", e);
            e.printStackTrace();
        }
    }


    private void setupMatchFingerClick() {

        Button btnMatchFinger = findViewById(R.id.btnMatchFinger);


        btnMatchFinger.setOnClickListener(v -> {
            if (isStartCaptureRunning || (captureThread != null && captureThread.isAlive())) {
                txtStatus.setText("Status : Capture already running...");
                return;
            }

            if (lastDeviceInfo == null) {
                txtStatus.setText("Status : Please init device first");
                return;
            }

            int totalUsers = dbHelper.getTotalFingerprints();
            if (totalUsers == 0) {
                txtStatus.setText("Status : No fingerprints in DB!\nCapture first");
                return;
            }

            txtStatus.setText("Status : MATCH MODE\n" + totalUsers + " stored\nPlace your finger");
            startMatchCapture();
        });
    }

    private void startMatchCapture() {
        if (isStartCaptureRunning || (captureThread != null && captureThread.isAlive())) {
            txtStatus.setText("Status : Capture already running...");
            return;
        }

        isStartCaptureRunning = true;

        captureThread = new Thread(() -> {
            try {
                imgFinger.post(() -> {
                    imgFinger.setImageResource(android.R.color.white);
                });

                int[] qty = new int[1];
                int[] nfiq = new int[1];

                int ret = morfinAuth.AutoCapture(minQuality, timeOut, qty, nfiq);

                if (ret != 0) {
                    imgFinger.post(() -> {
                        imgFinger.setImageResource(android.R.color.white);
                    });

                    if (ret == -2057) {
                        txtStatus.setText("Status : Device not connected");
                    } else {
                        txtStatus.setText("Status : Capture failed\n" + morfinAuth.GetErrorMessage(ret));
                    }
                    isStartCaptureRunning = false;
                    return;
                }

                byte[] capturedTemplate = getTemplateFromCapture();

                if (capturedTemplate == null) {
                    runOnUiThread(() -> {
                        txtStatus.setText("Status : Failed to get template");
                    });
                    isStartCaptureRunning = false;
                    return;
                }

                performMatching(capturedTemplate, qty[0]);
                isStartCaptureRunning = false;

            } catch (Exception e) {
                Log.e("Match", "Error", e);
                runOnUiThread(() -> {
                    txtStatus.setText("Status : Error\n" + e.getMessage());
                });
                isStartCaptureRunning = false;
            } finally {
                captureThread = null;
            }
        });

        captureThread.start();
    }


    private void performMatching(byte[] capturedTemplate, int quality) {
        new Thread(() -> {
            try {
                Cursor cursor = dbHelper.getAllFingerprints();

                if (cursor == null || cursor.getCount() == 0) {
                    runOnUiThread(() -> {
                        txtStatus.setText("Status : Database empty");
                    });
                    return;
                }


                final boolean[] isMatchArray = {false};
                final String[] matchedUserIdArray = {""};
                final int[] matchScoreArray = {0};
                final int[] fingersCheckedArray = {0};

                int userIdIdx = cursor.getColumnIndex(FingerprintDatabaseHelper.COL_USER_ID);
                int templateIdx = cursor.getColumnIndex(FingerprintDatabaseHelper.COL_TEMPLATE);

                while (cursor.moveToNext()) {
                    if (userIdIdx >= 0 && templateIdx >= 0) {
                        String userId = cursor.getString(userIdIdx);
                        byte[] storedTemplate = cursor.getBlob(templateIdx);

                        int[] score = new int[1];


                        int ret = morfinAuth.MatchTemplate(
                                capturedTemplate,
                                storedTemplate,
                                score,
                                TemplateFormat.FMR_V2011
                        );

                        fingersCheckedArray[0]++;
                        Log.d("Match", userId + ": Score=" + score[0] + ", Ret=" + ret);

                        if (ret == 0 && score[0] >= 400) {
                            isMatchArray[0] = true;
                            matchedUserIdArray[0] = userId;
                            matchScoreArray[0] = score[0];
                            break;
                        }
                    }
                }
                cursor.close();


                runOnUiThread(() -> {
                    if (isMatchArray[0]) {
                        txtStatus.setText(String.format(
                                "Status : MATCH FOUND!\nUser: %s\nScore: %d \nQuality: %d",
                                matchedUserIdArray[0], matchScoreArray[0], quality));
                    } else {
                        txtStatus.setText(String.format(
                                "Status : NO MATCH\nChecked: %d\nQuality: %d",
                                fingersCheckedArray[0], quality));
                    }
                });

            } catch (Exception e) {
                Log.e("Match", "Error", e);
                runOnUiThread(() -> {
                    txtStatus.setText("Status : Match error\n" + e.getMessage());
                });
            }
        }).start();
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
                    stopCaptureRequested = true;

                    lastDeviceInfo = null;
                    connectedDeviceModel = null;
                    txtStatus.setText(R.string.status_disconnected);
                    btnInit.setEnabled(false);
                    btnUninit.setEnabled(false);
                    btnStartCapture.setEnabled(false);
                    btnStopCapture.setEnabled(false);
                    btnSyncCapture.setEnabled(false);
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
                saveImageToStorage(captureCount, Quality, NFIQ);

                runOnUiThread(() -> {
                    String status = String.format(
                            "Status : FINGER %d/10 SAVED\nQuality: %d\nNFIQ: %d",
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

            } else if (errorCode == -2019) {

                runOnUiThread(() -> {
                    if (!stopCaptureRequested && captureCount < MAX_FINGERS) {
                        txtStatus.setText(
                                String.format(
                                        "Status : Timeout waiting for finger\nRetrying FINGER %d/10\nPlace your finger...",
                                        captureCount + 1
                                )
                        );

                        restartCaptureForNextFinger();
                    } else {
                        isStartCaptureRunning = false;
                        finishCaptureSession();
                    }
                });

            } else {

                isStartCaptureRunning = false;

                runOnUiThread(() -> {
                    if (errorCode == -2057) {
                        txtStatus.setText("Status : Device not connected");
                    } else {
                        txtStatus.setText(
                                String.format(
                                        "Status : CAPTURE FAILED (%d)\n%s\nCaptured %d/10 fingers",
                                        errorCode,
                                        morfinAuth.GetErrorMessage(errorCode),
                                        captureCount
                                )
                        );
                    }
                    finishCaptureSession();
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
        finishCaptureSession(null);
    }

    private void finishCaptureSession(String customMessage) {
        isStartCaptureRunning = false;
        stopCaptureRequested = false;

        btnStartCapture.setEnabled(true);
        btnStopCapture.setEnabled(false);

        if (customMessage != null && !customMessage.isEmpty()) {
            txtStatus.setText(customMessage);
        } else {
            String finalMessage = String.format(
                    "Status : Capture Complete\n" +
                            " Saved %d/10 images\n" +
                            "Location:\n%s",
                    captureCount,
                    currentSessionFolder
            );
            txtStatus.setText(finalMessage);
        }

        Log.d("CaptureSession", "Finished. Saved " + captureCount + " images to " + currentSessionFolder);
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
