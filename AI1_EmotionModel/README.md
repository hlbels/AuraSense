# AI1: Emotion Detection from Wearable Sensor Data (WESAD-Based)

This folder contains the implementation of **AI1**, the first AI component of our COEN/ELEC 390 capstone project. It trains a neural network to detect emotional discomfort (stress vs baseline) using sensor signals from the **WESAD** dataset and exports the model to `.tflite` format for Android integration.

---

## What This Model Does

- Uses real physiological signals: **accelerometer**, **skin temperature**, and **electrodermal activity (EDA)**
- Extracts sliding window features from `.pkl` files
- Classifies 60-second windows as:
  - `0` → Baseline
  - `1` → Stress
- Exports a lightweight `.tflite` model for mobile use

---

## Project Structure

```bash

AI1_EmotionModel/
├── data/ # WESAD subject .pkl files (not included)
├── notebooks/
│ └── train_model.ipynb # Full training, evaluation, and export notebook
├── utils/
│ └── preprocessing.py # Feature extraction and windowing logic
├── models/
│ └── stress_model.tflite # Output: model for Android app
├── requirements.txt # Reproducible environment
└── README.md # You're here
```

---

## Setup Instructions

> Requires Python 3.10  
> Dependencies listed in `requirements.txt`

### 1. Clone the repository:

```bash
git clone https://github.com/hlbels/COEN-ELEC-390-Project/AI1_EmotionModel
```

### 2. Create virtual environment:

```bash
python -m venv .venv
.venv\Scripts\activate        # Windows
# source .venv/bin/activate   # macOS/Linux
```

### 3. Install required packages:

```bash
pip install -r requirements.txt
```

### 4. Place the WESAD data:

- Download WESAD from the official UCI repository: [https://archive.ics.uci.edu/ml/datasets/WESAD](https://archive.ics.uci.edu/ml/datasets/WESAD)

- Place all .pkl files inside AI1_EmotionModel/data/

### 5. Run the notebook:

```bash
jupyter notebook AI1_EmotionModel/notebooks/train_model.ipynb
```

---

## Output

- models/stress_model.tflite: final trained model

- Accuracy, F1-score, and confusion matrix shown in notebook

- Model ready to integrate into AI2 (Android app)

---

## References

- Schmidt et al., "Introducing WESAD, a Multimodal Dataset for Wearable Stress and Affect Detection", UbiComp 2018.

- TensorFlow, scikit-learn, NumPy, Pandas, Matplotlib

## Credits

Developed as part of the COEN/ELEC 390 Project  
Concordia University – Winter/Summer 2025  
Lead AI1 Developer: Hala Belamri
