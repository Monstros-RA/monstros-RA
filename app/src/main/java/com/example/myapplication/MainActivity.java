package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.OpenCVLoader;
import org.opencv.aruco.Aruco;
import org.opencv.aruco.DetectorParameters;
import org.opencv.aruco.Dictionary;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CvCameraViewListener2 {
    private static final String TAG = "MYAPP::OPENCV";

    // Settings
    private int dict;
    private boolean showDecteted;
    private boolean showEstimatedPose;
    private boolean showRejected;
    private float markerLength;

    private CameraBridgeViewBase mOpenCvCameraView;
    private SharedPreferences calibrationPreferences;
    private SharedPreferences settings;
    private Mat cameraMatrix;
    private Mat distCoeffs;
    private Mat rvecs;
    private Mat tvecs;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        OpenCVLoader.initDebug();

        setContentView(R.layout.activity_main);

        mOpenCvCameraView = findViewById(R.id.cameraView);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCameraPermissionGranted();
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setMaxFrameSize(640, 480);
        mOpenCvCameraView.enableView();

        calibrationPreferences = getSharedPreferences(CalibrateCameraActivity.CALIBRATION_PREFERENCES,
                Context.MODE_PRIVATE);
        settings = PreferenceManager.getDefaultSharedPreferences(this);

        rvecs = new Mat();
        tvecs = new Mat();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        OpenCVLoader.initDebug();

        if(mOpenCvCameraView != null)
            mOpenCvCameraView.enableView();

        if (calibrationPreferences.getBoolean("calibrated", false)) {
            loadIntrinsicParameters();
        } else {
            Toast.makeText(this, "Câmera não calibrada! Não é possível detectar marcadores",
                    Toast.LENGTH_SHORT).show();
        }

        loadSettings();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.acivity_main_options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.calibrate_camera:
                intent = new Intent(MainActivity.this, CalibrateCameraActivity.class);
                startActivity(intent);
                return true;
            case R.id.settings:
                intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
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
        // Checa se a câmera já foi calibrada
        if (cameraMatrix != null && distCoeffs != null) {
            return detectMarkers(inputFrame.rgba());
        }

        return inputFrame.rgba();
    }

    private static void drawRotatedRect(Mat image, RotatedRect rotatedRect, Scalar color, int thickness) {
        Point[] vertices = new Point[4];
        rotatedRect.points(vertices);
        MatOfPoint points = new MatOfPoint(vertices);
        Imgproc.drawContours(image, Arrays.asList(points), -1, color, thickness);
    }

    private Mat detectMarkers(Mat inputImage) {
        Imgproc.cvtColor(inputImage, inputImage, Imgproc.COLOR_BGRA2BGR);

        Mat ids = new Mat();
        List<Mat> corners = new ArrayList<>();
        List<Mat> rejectedCandidates = new ArrayList<>();
        DetectorParameters parameters = DetectorParameters.create();
        Dictionary dictionary = Aruco.getPredefinedDictionary(dict);
        Aruco.detectMarkers(inputImage, dictionary, corners, ids, parameters, rejectedCandidates);

        Mat outputImage = inputImage.clone();
        if (showRejected && rejectedCandidates.size() > 0) {
            Aruco.drawDetectedMarkers(outputImage, rejectedCandidates);
        }
        if (corners.size() > 0) {
            if (showDecteted) {
                Aruco.drawDetectedMarkers(outputImage, corners, ids);
            }

            Aruco.estimatePoseSingleMarkers(corners, markerLength, cameraMatrix, distCoeffs, rvecs, tvecs);
            if (showEstimatedPose) {
                for (int i = 0; i < rvecs.rows(); ++i) {
                    Aruco.drawAxis(outputImage, cameraMatrix, distCoeffs, rvecs.row(i), tvecs.row(i), markerLength);
                }
            }

        }

        return outputImage;
    }

    private void loadIntrinsicParameters() {
        cameraMatrix = new Mat(new Size(3, 3), CvType.CV_64F);
        double[] cameraMatrixArray = new double[9];
        for (int i = 0; i < 9; i++) {
            cameraMatrixArray[i] = (double) calibrationPreferences.getFloat(Integer.toString(i), 0.0f);
        }
        cameraMatrix.put(0, 0, cameraMatrixArray);

        distCoeffs = new Mat(new Size(5, 1), CvType.CV_64F);
        double[] distCoeffsArray = new double[5];
        for (int i = 9; i < 9 + 5; i++) {
            distCoeffsArray[i - 9] = calibrationPreferences.getFloat(Integer.toString(i), 0.0f);
        }
        distCoeffs.put(0, 0, distCoeffsArray);
    }

    private void loadSettings() {
        dict = Integer.parseInt(settings.getString(getString(R.string.key_aruco_dictionary), "0"));
        showDecteted = settings.getBoolean(getString(R.string.key_aruco_show_detected), false);
        showEstimatedPose = settings.getBoolean(getString(R.string.key_aruco_show_estimated_pose), false);
        markerLength = Float.parseFloat(settings.getString(getString(R.string.key_aruco_marker_length), "1.0f"));
        showRejected = settings.getBoolean(getString(R.string.key_aruco_show_rejected), false);
    }
}
