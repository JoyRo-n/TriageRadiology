# PyTorch AI Triage Radiology Backend Guide (VS Code)

This directory contains the PyTorch FastAPI REST API server matching the Swagger UI specification (`POST /analyze-scan` with `multipart/form-data` file upload).

## Quick Start in VS Code

1. Open VS Code terminal in `pytorch_backend` folder.
2. Install dependencies:
   ```bash
   pip install fastapi uvicorn torch torchvision pillow python-multipart
   ```
3. Run the PyTorch backend server:
   ```bash
   uvicorn pytorch_server:app --host 0.0.0.0 --port 8000 --reload
   ```
4. Open Swagger UI in browser: `http://localhost:8000/docs`

## Connecting from Android App

1. In the Android App, tap the top-right **Backend Settings** gear icon.
2. Tap the **FastAPI :8000** button (`http://10.0.2.2:8000/`).
3. Tap **Save**.
4. The status chip will change to **PyTorch Backend Online (Live API)**!
5. Now you can capture CT Scan photos using **Take Photo of CT Scan** or choose images from gallery and tap **Analyze Radiology Scan with AI** to send real multipart POST requests to PyTorch!
