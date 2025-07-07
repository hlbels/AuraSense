import pickle
import numpy as np
from scipy.stats import entropy

def load_subject(filepath):
    import pickle
    with open(filepath, 'rb') as file:
        data = pickle.load(file, encoding='latin1')

    chest = data.get('signal', {}).get('chest', {})
    key_map = {
        'ACC': 'ACC',
        'TEMP': 'Temp',
        'EDA': 'EDA'
    }

    for label, key in key_map.items():
        if key not in chest:
            raise KeyError(f"{filepath} is missing key: {key}")

    acc = chest[key_map['ACC']]
    temp = chest[key_map['TEMP']]
    eda = chest[key_map['EDA']]
    label = data['label']

    return acc, temp, eda, label



def sliding_window(data, window_size, step_size):
    """
    Generate overlapping windows for a 1D or 2D signal.
    """
    for start in range(0, len(data) - window_size + 1, step_size):
        yield data[start:start + window_size]

def extract_features_from_window(acc, temp, eda):
    """
    Extract statistical features from 60s window.
    - acc: Nx3 accelerometer
    - temp: temperature vector
    - eda: EDA vector
    Returns: feature dictionary
    """
    acc_mag = np.linalg.norm(acc, axis=1)
    features = {
        'acc_mean': np.mean(acc_mag),
        'acc_std': np.std(acc_mag),
        'temp_mean': np.mean(temp),
        'temp_std': np.std(temp),
        'eda_mean': np.mean(eda),
        'eda_std': np.std(eda),
        'eda_entropy': entropy(np.histogram(eda, bins=10)[0] + 1)
    }
    return features

def process_subject(filepath, window_size=256, step_size=128):
    """
    Load, window, and extract features + labels from a single subject.
    Returns: X (features), y (binary labels: 0 = baseline, 1 = stress)
    """
    acc, temp, eda, labels = load_subject(filepath)
    X, y = [], []

    for acc_win, temp_win, eda_win, label_win in zip(
        sliding_window(acc, window_size, step_size),
        sliding_window(temp, window_size, step_size),
        sliding_window(eda, window_size, step_size),
        sliding_window(labels, window_size, step_size)
    ):
        label_mode = np.bincount(label_win.flatten()).argmax()
        if label_mode in [1, 2]:  # 1 = baseline, 2 = stress
            features = extract_features_from_window(acc_win, temp_win, eda_win)
            X.append(list(features.values()))
            y.append(0 if label_mode == 1 else 1)

    return X, y
