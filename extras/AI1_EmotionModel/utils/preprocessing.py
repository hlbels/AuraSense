# preprocessing.py
import os
import pickle
import numpy as np
import pandas as pd
from scipy.signal import butter, lfilter
from scipy.stats import mode
import warnings
warnings.filterwarnings("ignore", category=FutureWarning)

# ----------------------------
# Config
# ----------------------------
DATA_DIR = "../data"          # folder containing S*.pkl
SUBJECTS = [2,3,4,5,6,7,8,9,10,11,13,14,15,16,17]
FS = {'ACC':32, 'BVP':64, 'TEMP':4, 'label':700}
WIN_SEC = 30                # 30-second windows (non-overlapping)
PURITY = 0.80               # require >=80% of samples in a window to share same label
OUT_X = "X.npy"
OUT_Y = "y.npy"
OUT_SCALER = "feature_scaler.pkl"

# Label mapping used by WESAD (keep consistent end-to-end)
# 0 = baseline, 1 = amusement, 2 = stress
VALID_LABELS = (0, 1, 2)

# ----------------------------
# Helpers
# ----------------------------
def butter_lowpass(cut, fs, order=4):
    b, a = butter(order, cut/(0.5*fs), btype='low')
    return b, a

def lpf(x, cut, fs, order=4):
    if x is None or len(x) == 0:
        return x
    b, a = butter_lowpass(cut, fs, order)
    return lfilter(b, a, x)

def load_subject(path_pkl):
    with open(path_pkl, 'rb') as f:
        d = pickle.load(f, encoding='latin1')
    wrist = d['signal']['wrist']  # dict: 'ACC'(n,3), 'BVP'(n,), 'EDA', 'TEMP'(n,)
    lbl = d['label']              # (m,) at 700 Hz; values in {0,1,2} etc.
    return wrist, lbl

def to_df(wrist, lbl):
    """Align ACC/BVP/TEMP with the 700 Hz label stream on a shared datetime index."""
    # Build dataframes
    acc = pd.DataFrame(wrist['ACC'], columns=['ACC_x','ACC_y','ACC_z'])
    bvp = pd.DataFrame(wrist['BVP'], columns=['BVP'])
    tmp = pd.DataFrame(wrist['TEMP'], columns=['TEMP'])
    lab = pd.DataFrame(lbl, columns=['label'])

    # Optional light filtering (helps wrist signals)
    for c in ['ACC_x','ACC_y','ACC_z']:
        acc[c] = lpf(acc[c].values, cut=10, fs=FS['ACC'])

    # Time indices (seconds from start -> datetime)
    acc.index = pd.to_datetime(np.arange(len(acc))/FS['ACC'], unit='s')
    bvp.index = pd.to_datetime(np.arange(len(bvp))/FS['BVP'], unit='s')
    tmp.index = pd.to_datetime(np.arange(len(tmp))/FS['TEMP'], unit='s')
    lab.index = pd.to_datetime(np.arange(len(lab))/FS['label'], unit='s')

    # Join on outer time axis, forward-fill label to sensor timestamps
    df = acc.join(bvp, how='outer').join(tmp, how='outer').join(lab, how='outer')
    df['label'] = df['label'].ffill()
    # Ensure label is integer
    df['label'] = df['label'].astype('float').round().astype('int')
    return df

def window_iter(df, win_sec=WIN_SEC):
    start = df.index.min()
    end   = df.index.max()
    step = pd.to_timedelta(win_sec, unit='s')
    t = start
    while t + step <= end:
        yield df.loc[t:t+step], (t, t+step)
        t += step  # non-overlapping

def label_window(lbl_series):
    """Return (majority_label, purity) for a window using the 700 Hz label stream."""
    arr = lbl_series.values.astype(int)
    # keep only valid classes
    arr = arr[np.isin(arr, VALID_LABELS)]
    if arr.size == 0:
        return None, 0.0
    m = mode(arr, keepdims=True).mode[0]
    purity = (arr == m).mean()
    return int(m), float(purity)

def extract_features(win):
    """
    Compute 10 features in the exact order expected by the Android app:
    [ACC_x_mean, ACC_y_mean, ACC_z_mean, TEMP_mean, BVP_mean,
     ACC_x_std,  ACC_y_std,  ACC_z_std,  TEMP_std,  BVP_std]
    """
    cols_mean = ['ACC_x','ACC_y','ACC_z','TEMP','BVP']
    cols_std  = ['ACC_x','ACC_y','ACC_z','TEMP','BVP']
    feats = []
    # means
    for c in cols_mean:
        x = win[c].dropna().values
        feats.append(np.mean(x) if x.size else np.nan)
    # stds (population std, ddof=0)
    for c in cols_std:
        x = win[c].dropna().values
        feats.append(np.std(x, ddof=0) if x.size else np.nan)
    return np.array(feats, dtype=float)

def process_subject(pkl_path):
    wrist, lbl = load_subject(pkl_path)
    df = to_df(wrist, lbl)
    X, y = [], []
    for win, _ in window_iter(df, WIN_SEC):
        m, purity = label_window(win['label'])
        if m is None or purity < PURITY:
            continue
        feats = extract_features(win)
        if np.isnan(feats).any():
            continue
        X.append(feats)
        y.append(m)
    X = np.array(X, dtype=float)
    y = np.array(y, dtype=int)
    return X, y

# ----------------------------
# Main
# ----------------------------
def main():
    allX, ally = [], []
    for sid in SUBJECTS:
        p = os.path.join(DATA_DIR, f"S{sid}.pkl")
        if not os.path.exists(p):
            print(f"[skip] missing {p}")
            continue
        Xi, yi = process_subject(p)
        if Xi.size == 0:
            print(f"[warn] S{sid} yielded 0 windows after purity/NaN filters.")
            continue
        print(f"S{sid}: X{Xi.shape}, y{yi.shape}, dist={np.bincount(yi, minlength=3)}")
        allX.append(Xi)
        ally.append(yi)

    if not allX:
        raise RuntimeError("No data produced. Check DATA_DIR and subject files.")

    X = np.vstack(allX)
    y = np.hstack(ally)

    # Save raw (unscaled) features and labels
    np.save(OUT_X, X)
    np.save(OUT_Y, y)

    # Fit scaler on training features (global mean/std per column)
    means = X.mean(axis=0)
    stds = X.std(axis=0, ddof=0)
    # avoid divide-by-zero
    stds[stds == 0] = 1.0

    scaler = {
        "means": means.astype(float),
        "stds": stds.astype(float),
        "feature_order": [
            "acc_x_mean","acc_y_mean","acc_z_mean","temp_mean","bvp_mean",
            "acc_x_std","acc_y_std","acc_z_std","temp_std","bvp_std"
        ],
        "win_sec": WIN_SEC,
        "purity": PURITY
    }

    with open(OUT_SCALER, "wb") as f:
        pickle.dump(scaler, f)

    print(f"\nSaved: {OUT_X} (shape {X.shape}), {OUT_Y} (shape {y.shape})")
    print(f"Saved: {OUT_SCALER}")
    print("Label distribution:", dict(zip(range(3), np.bincount(y, minlength=3))))

if __name__ == "__main__":
    main()
