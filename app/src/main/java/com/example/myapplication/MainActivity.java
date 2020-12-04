package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.OpenCVLoader;
import org.opencv.aruco.DetectorParameters;
import org.opencv.aruco.Dictionary;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.aruco.Aruco;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CvCameraViewListener2 {
    private static final String TAG = "MYAPP::OPENCV";

    private CameraBridgeViewBase mOpenCvCameraView;
    private SharedPreferences calibrationPreferences;
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
        if (OpenCVLoader.initDebug())
            Toast.makeText(this, "OpenCV loaded successfully!", Toast.LENGTH_SHORT).show();
        else
            Toast.makeText(this, "Failed to load OpenCV", Toast.LENGTH_SHORT).show();

        if (calibrationPreferences.getBoolean("calibrated", false)) {
            loadIntrinsicParameters();
        } else {
            Toast.makeText(this, "Câmera não calibrada! Não é possível detectar marcadores",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.acivity_main_options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()){
            case R.id.calibrate_camera:
                Intent intent = new Intent(MainActivity.this, CalibrateCameraActivity.class);
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
        List<Mat> corners = new ArrayList<Mat>();
        List<Mat> rejectedCandidates = new ArrayList<Mat>();
        DetectorParameters parameters = DetectorParameters.create();
        Dictionary dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_ARUCO_ORIGINAL);
        Aruco.detectMarkers(inputImage, dictionary, corners, ids, parameters, rejectedCandidates);

        Mat outputImage = inputImage.clone();
        if (rejectedCandidates.size() > 0) {
            Aruco.drawDetectedMarkers(outputImage, rejectedCandidates);
        }
        if (corners.size() > 0) {
            Aruco.drawDetectedMarkers(outputImage, corners, ids);

            Aruco.estimatePoseSingleMarkers(corners, 1f, cameraMatrix, distCoeffs, rvecs, tvecs);
            for (int i = 0; i < rvecs.rows(); ++i) {
                Aruco.drawAxis(outputImage, cameraMatrix, distCoeffs, rvecs.row(i), tvecs.row(i), 0.1f);
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
}
