# AI1 Emotion Model — Wrist-Based Stress Detection

This repository contains the **data preprocessing pipeline**, **model training notebook**, and **utilities** for a wrist-worn, sensor-based stress detection system.  
The system is based on the _WESAD_ dataset and tailored for integration into the **AuraSense** mobile application.  
It uses data from **accelerometer (ACC)**, **temperature (TEMP)**, and **blood volume pulse (BVP)** sensors.

---

## 1. Repository Structure

```bash
AI1_EmotionModel/
│
├── utils/
│ └── preprocessing.py # Preprocessing script for WESAD wrist data
│
├── notebooks/
│ └── train_model.ipynb # Model training, evaluation, and export
│
├── .gitignore # Ignores data, models, and large outputs
├── requirements.txt # Python dependencies
└── README.md # Documentation
```

---

## 2. Dataset Requirements

This project uses the **WESAD** dataset (University of Mannheim).  
Due to licensing and size constraints, raw data is **not** included in this repository.

**Steps to obtain data:**

1. Download WESAD from the official repository:  
   [https://www.uni-mannheim.de/dws/research/projects/wesad/](https://www.uni-mannheim.de/dws/research/projects/wesad/)
2. Extract the dataset into:

```bash
AI1_EmotionModel/data
```

3. The `/data` directory should contain the original `.pkl` and associated `.csv` questionnaire files for each subject.

---

## 3. Preprocessing Pipeline

The preprocessing script:

- Extracts **wrist-only** sensor data (Empatica E4 subset).
- Uses **ACC (3-axis)**, **TEMP**, and **BVP** streams.
- Computes **10 statistical features** per window:
- Mean and standard deviation for each of: `acc_x`, `acc_y`, `acc_z`, `temp`, `bvp`
- Labels each window into:
- `0` = Baseline (calm)
- `1` = Amusement (positive arousal)
- `2` = Stress (negative arousal)
- Saves:
- `X.npy` → Feature matrix
- `y.npy` → Label vector
- `feature_scaler_train.pkl` → StandardScaler fitted on training data
- `rf_model.pkl` → Trained Random Forest model (if training is performed)

**To run preprocessing:**

```bash
python utils/preprocessing.py
```

Outputs will be saved to:

```bash
AI1_EmotionModel/preprocessed/
```

---

## 4. Model Training

Model training is performed in `notebooks/train_model.ipynb`.

- Open the notebook and execute the cells **in order**. There is a dedicated **Preprocessing** cell that **invokes `utils/preprocessing.py` directly**; you do not need to run anything from a terminal. That cell will:

  - Load raw WESAD wrist data from `/data`
  - Produce `preprocessed/X.npy` and `preprocessed/y.npy` (10 features per window; labels 0/1/2)

- After preprocessing finishes, the notebook:

  - Splits data into train/validation/test (70/15/15 split)
  - Fits `StandardScaler` **on the training split only**
  - Trains a `RandomForestClassifier` for 3-class classification (baseline, amusement, stress)
  - Evaluates performance with:
    - Overall accuracy and macro/micro F1-score
    - Per-class precision/recall/F1
    - Confusion matrix (absolute and normalized)
    - ROC curves (one-vs-rest with micro and macro AUC)

- Exported artifacts (written by the final cells of the notebook):
  - `models/rf_model.pkl` — pickled scikit-learn model
  - `models/feature_scaler_train.pkl` — **train-only** scaler used at inference time
  - `models/stress_model.onnx` — ONNX model for mobile integration

If you are running in a hosted environment, ensure the notebook’s working directory is the repository root so relative paths like `/preprocessed` and `/models` resolve correctly.

---

## 5. Mobile Integration Notes

- **Feature order (must match training exactly)**:

```bash
['acc_x_mean', 'acc_y_mean', 'acc_z_mean',
'temp_mean', 'bvp_mean',
'acc_x_std', 'acc_y_std', 'acc_z_std',
'temp_std', 'bvp_std']
```

using the arrays saved in `models/feature_scaler_train.pkl`. In Android, either hard-code these arrays or load them from a resource. The feature order above must be preserved.

- **ONNX input**: a single standardized vector with shape `float32[1, 10]`.

- **Model outputs**: three-class logits/probabilities mapped to
- `0` → Baseline
- `1` → Amusement
- `2` → Stress

- **Windowing**: the app should aggregate **30 samples** per decision (≈30 seconds if your inter-packet interval is ~1 s; or 60 s if ~2 s), then compute the 10 features over that window. Use the exact mean/std definitions as in training.

- **Signal quality**: for robust behavior on-device, discard windows with poor PPG quality before inference (e.g., extreme motion or missing peaks), or hold the previous label.

---

## 6. Dependencies

All Python dependencies required for preprocessing, training, evaluation, and ONNX export are listed in `requirements.txt`. Install them with your preferred environment manager before opening the notebook. The notebook assumes the versions specified there (NumPy, pandas, scikit-learn, SciPy, matplotlib, seaborn, onnx, onnxruntime, joblib, Jupyter).

---

## 7. Reproducibility Checklist

1. Download WESAD and place the raw subject files under `/data` (original `.pkl` and questionnaire files).
2. Open `notebooks/train_model.ipynb`.
3. Run the **Preprocessing** cell (which calls `utils/preprocessing.py`) to produce `preprocessed/X.npy` and `preprocessed/y.npy`.
4. Run the training and evaluation cells.
5. Confirm that the notebook saved:

- `models/rf_model.pkl`
- `models/feature_scaler_train.pkl`
- `models/stress_model.onnx`

6. Copy the ONNX model and the scaler values into the mobile project. Ensure the feature order and standardization in the app match the notebook.

---

## 8. Limitations and Notes

- The model is trained **only on wrist signals** (ACC, TEMP, BVP) from WESAD; it does not use chest modalities or EDA in this configuration.
- Real-world performance depends on device hardware, wearing conditions, ambient temperature, and movement. Expect **domain shift** relative to Empatica E4. For best results, collect a small set of your own labeled windows (baseline vs. stress) with the exact same features and perform light re-training or calibration.
- Acute stress detection benefits from heart-rate variability features; if available from your device, consider extending the feature set (e.g., RR intervals → RMSSD/SDNN) and retraining.

---

## 9. Citation

Schmidt, P., Reiss, A., Duerichen, R., Marberger, C., & Van Laerhoven, K. (2018). Introducing WESAD, a Multimodal Dataset for Wearable Stress and Affect Detection. _Proceedings of the 20th ACM International Conference on Multimodal Interaction (ICMI ’18)_. https://doi.org/10.1145/3242969.3242985
