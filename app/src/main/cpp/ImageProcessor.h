//
// Created by seymour on 1/29/24.
//

#ifndef ANDROIDOPENCVDEMO_IMAGEPROCESSOR_H
#define ANDROIDOPENCVDEMO_IMAGEPROCESSOR_H


#include <array>

#include <opencv2/core.hpp>

#include "mills/MillEngine.h"

struct ImageProcessor {
    static constexpr int MODE_BODY_ESTIMATE = 5;

    /// OpenCV frames, implemented as fields for optimization to avoid creating/deleting cv::Mat objects
    cv::Mat frameIn, frameOut, frameRot, gray, edges, mask;

    /// Computer vision algos are here
    mills::MillEngine engine;

    std::array<float, 4> lastMetrics = {0.0f, 0.0f, 0.0f, 0.0f};

    /// Process frameIn to frameOut
    void process(int rotation);

    /// Estimate height, weight, BMI, confidence from the current rotated frame.
    std::array<float, 4> estimateBodyMetrics();

    void setMode(int mode) {
        currentMode = mode;
        if (mode < MODE_BODY_ESTIMATE) {
            engine.setMode(mode);
        }
    }

private:
    int currentMode = MODE_BODY_ESTIMATE;
};


#endif //ANDROIDOPENCVDEMO_IMAGEPROCESSOR_H
