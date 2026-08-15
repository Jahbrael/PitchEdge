package com.betai.export;

import com.betai.api.dto.ModelAccuracyResponse;
import com.betai.api.dto.PredictionResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public interface ExcelExportService {

    void writePredictionResponse(PredictionResponse response, OutputStream outputStream) throws IOException;

    void writeModelAccuracy(List<ModelAccuracyResponse> accuracyRows, OutputStream outputStream) throws IOException;
}
