//
// Created by seymour on 1/29/24.
//

#include <opencv2/opencv.hpp>
#include <algorithm>
#include <array>

#include "ImageProcessor.h"

void ImageProcessor::process(int rotation) {
    using namespace std;
    using namespace cv;

    // Smart rotation
    switch (rotation) {
        case 0:
            rotate(frameIn, frameRot, ROTATE_90_CLOCKWISE);
            break;
        case 1:
            copyTo(frameIn, frameRot, Mat());
            break;
        case 2:
            rotate(frameIn, frameRot, ROTATE_90_COUNTERCLOCKWISE);
            break;
        case 3:
            rotate(frameIn, frameRot, ROTATE_180);
            break;
    }

    if (currentMode == MODE_BODY_ESTIMATE) {
        frameRot.copyTo(frameOut);
        lastMetrics = estimateBodyMetrics();
        if (lastMetrics[3] > 0.0f) {
            std::string text = cv::format("%.0f cm  %.0f kg  BMI %.1f", lastMetrics[0], lastMetrics[1], lastMetrics[2]);
            putText(frameOut, text, {20, 50}, FONT_HERSHEY_SIMPLEX, 1.0, {60, 196, 124}, 2);
        } else {
            putText(frameOut, "Frame full body", {20, 50}, FONT_HERSHEY_SIMPLEX, 1.0, {215, 168, 78}, 2);
        }
        return;
    }

    // Process the image with Computer Vision algos
    lastMetrics = {0.0f, 0.0f, 0.0f, 0.0f};
    engine.process(frameRot, frameOut);
}

std::array<float, 4> ImageProcessor::estimateBodyMetrics() {
    using namespace cv;
    using namespace std;

    if (frameRot.empty()) {
        return {0.0f, 0.0f, 0.0f, 0.0f};
    }

    cvtColor(frameRot, gray, COLOR_BGR2GRAY);
    GaussianBlur(gray, gray, Size(7, 7), 0);
    Canny(gray, edges, 45, 120);

    Mat kernel = getStructuringElement(MORPH_ELLIPSE, Size(9, 9));
    morphologyEx(edges, mask, MORPH_CLOSE, kernel);
    dilate(mask, mask, kernel);

    vector<vector<Point>> contours;
    findContours(mask, contours, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);

    Rect bestRect;
    double bestArea = 0.0;
    for (const auto &contour: contours) {
        double area = contourArea(contour);
        Rect rect = boundingRect(contour);
        double frameArea = static_cast<double>(frameRot.cols) * frameRot.rows;
        double aspect = rect.width > 0 ? static_cast<double>(rect.height) / rect.width : 0.0;
        bool plausibleBody = area > frameArea * 0.025 && aspect > 1.15 && rect.height > frameRot.rows * 0.25;
        if (plausibleBody && area > bestArea) {
            bestArea = area;
            bestRect = rect;
        }
    }

    if (bestArea <= 0.0) {
        return {0.0f, 0.0f, 0.0f, 0.0f};
    }

    rectangle(frameOut, bestRect, Scalar(60, 196, 124), 3);

    float bodyHeightRatio = static_cast<float>(bestRect.height) / frameRot.rows;
    float bodyWidthRatio = static_cast<float>(bestRect.width) / frameRot.cols;
    float contourRatio = static_cast<float>(bestArea / (static_cast<double>(frameRot.cols) * frameRot.rows));
    float aspect = static_cast<float>(bestRect.height) / std::max(1, bestRect.width);

    float heightCm = std::clamp(128.0f + bodyHeightRatio * 62.0f, 135.0f, 205.0f);
    float estimatedBmi = std::clamp(17.0f + bodyWidthRatio * 22.0f + contourRatio * 18.0f, 17.0f, 38.0f);
    float heightM = heightCm / 100.0f;
    float weightKg = std::clamp(estimatedBmi * heightM * heightM, 38.0f, 160.0f);
    float bmi = weightKg / (heightM * heightM);

    float areaConfidence = std::clamp((contourRatio - 0.025f) / 0.18f, 0.0f, 1.0f);
    float framingConfidence = std::clamp((bodyHeightRatio - 0.30f) / 0.45f, 0.0f, 1.0f);
    float shapeConfidence = std::clamp((aspect - 1.15f) / 2.35f, 0.0f, 1.0f);
    float confidence = std::clamp(0.25f + 0.35f * areaConfidence + 0.25f * framingConfidence + 0.15f * shapeConfidence, 0.0f, 0.92f);

    return {heightCm, weightKg, bmi, confidence};
}
