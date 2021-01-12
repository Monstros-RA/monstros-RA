package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Size;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CalibrateCameraActivity extends AppCompatActivity implements
        CameraBridgeViewBase.CvCameraViewListener2, CameraCalibrator.OnCameraCalibratedListener {
    private static final String DEFAULT_MIN_SAMPLES = "20";
    private static final String DEFAULT_PATTERN_ROWS = "7";
    private static final String DEFAULT_PATTERN_COLUMNS = "7";
    public static final String CALIBRATION_PREFERENCES = "calibration";

    private CameraCalibrator calibrator;
    private CameraBridgeViewBase cameraView;
    private SharedPreferences settings;
    private long lastCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OpenCVLoader.initDebug();

        // Mantém a tela ligada mesmo se não houver interação
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_calibrate_camera);
        settings = PreferenceManager.getDefaultSharedPreferences(this);

        // Configura a CameraView
        cameraView = findViewById(R.id.cameraView);
        cameraView.setVisibility(View.VISIBLE);
        cameraView.setCameraPermissionGranted();
        cameraView.setCvCameraViewListener(this);
        cameraView.setMaxFrameSize(480, 320);
        cameraView.enableView();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (cameraView != null) {
            cameraView.disableView();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        OpenCVLoader.initDebug();

        if (cameraView != null)
            cameraView.enableView();

        int minSamples = Integer.parseInt(settings.getString(getString(R.string.key_calibration_samples),
                DEFAULT_MIN_SAMPLES));
        int rows = Integer.parseInt(settings.getString(getString(R.string.key_calibration_rows),
                DEFAULT_PATTERN_ROWS));
        int columns = Integer.parseInt(settings.getString(getString(R.string.key_calibration_columns),
                DEFAULT_PATTERN_COLUMNS));

        calibrator = new CameraCalibrator(minSamples, new Size(rows, columns));
        calibrator.setOnCameraCalibratedListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (cameraView != null) {
            cameraView.disableView();
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
    }

    @Override
    public void onCameraViewStopped() {
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat image = inputFrame.rgba();
        try {
            long currentTime = Calendar.getInstance().getTimeInMillis();

            if (currentTime > lastCapture + 2000) {
                calibrator.tryFindPattern(image, true);
                lastCapture = currentTime;
                if (calibrator.getSamples() < calibrator.getMinSamples()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String text = String.format("%d/%d amostras coletadas", calibrator.getSamples(), calibrator.getMinSamples());
                            Toast.makeText(CalibrateCameraActivity.this, text, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return image;
    }

    @Override
    public void OnCameraCalibrated(Mat cameraMatrix, Mat distCoeffs, List<Mat> rvecs, List<Mat> tvecs) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (tvecs == null)
                    Toast.makeText(CalibrateCameraActivity.this, "Error: tvecs == null", Toast.LENGTH_SHORT).show();
                else {
                    try {
                        Mat translation = tvecs.get(tvecs.size() - 1);
                        String text = String.format(Locale.ROOT, "Câmera calibrada com sucesso! Posição da câmera: (%.2f, %.2f, %.2f)",
                                translation.get(0, 0)[0], translation.get(1, 0)[0], translation.get(2, 0)[0]);
                        Toast.makeText(CalibrateCameraActivity.this, text, Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(CalibrateCameraActivity.this, e.toString(), Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        SharedPreferences pref = getSharedPreferences(CALIBRATION_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();

        editor.putBoolean("calibrated", true);

        double[] cameraMatrixArray = new double[9];
        cameraMatrix.get(0, 0, cameraMatrixArray);
        for (int i = 0; i < 9; i++) {
            editor.putFloat(Integer.toString(i), (float) cameraMatrixArray[i]);
        }

        double[] distCoeffsArray = new double[5];
        distCoeffs.get(0, 0, distCoeffsArray);
        for (int i = 9; i < 14; i++) {
            editor.putFloat(Integer.toString(i), (float) distCoeffsArray[i - 9]);
        }

        editor.apply();
    }
}