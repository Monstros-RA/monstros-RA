package com.example.myapplication;

import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.OpenCVLoader;
import org.opencv.aruco.DetectorParameters;
import org.opencv.aruco.Dictionary;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
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
    private SeekBar mThreshSeekBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        mOpenCvCameraView = findViewById(R.id.HelloOpenCvView);
        mThreshSeekBar = findViewById(R.id.threshSeekBar);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCameraPermissionGranted();
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.enableView();
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
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        try {
            return detectMarkers(inputFrame.gray());
        } catch (Exception e) {
            Log.d("[DETECT MARKERS]", e.getMessage());
            return inputFrame.gray();
        }
    }

    private static void drawRotatedRect(Mat image, RotatedRect rotatedRect, Scalar color, int thickness) {
        Point[] vertices = new Point[4];
        rotatedRect.points(vertices);
        MatOfPoint points = new MatOfPoint(vertices);
        Imgproc.drawContours(image, Arrays.asList(points), -1, color, thickness);
    }

    private Mat detectMarkers(Mat inputImage) {
        Mat ids = new Mat();
        List<Mat> corners = new ArrayList<Mat>();
        List<Mat> rejectedCandidates = new ArrayList<Mat>();
        DetectorParameters parameters = DetectorParameters.create();
        Dictionary dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_6X6_250);
        Aruco.detectMarkers(inputImage, dictionary, corners, ids, parameters, rejectedCandidates);

        Mat outputImage = inputImage.clone();
        if(corners.size() > 0){
            Aruco.drawDetectedMarkers(outputImage, corners, ids);

            //Mat rvecs, tvecs;
            //Aruco.estimatePoseSingleMarkers(corners, 0.5, cameraMatrix, distCoeffs, rvecs, tvecs);
        }

        return outputImage;
    }
}
