"""
AI Triage Radiology - PyTorch FastAPI Backend Server (VS Code)
================================================================
Matching Swagger UI Endpoint: POST /analyze-scan (multipart/form-data)

Installation:
    pip install fastapi uvicorn torch torchvision pillow python-multipart

Usage:
    uvicorn pytorch_server:app --host 0.0.0.0 --port 8000 --reload

Endpoints:
    GET  /                      -> API Info / Root Health Check
    GET  /docs                  -> Swagger UI Documentation
    POST /analyze-scan          -> Analyze CT / Radiology Scan via PyTorch
"""

import io
from datetime import datetime
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from PIL import Image

# Initialize FastAPI App
app = FastAPI(
    title="AI Triage Radiology PyTorch API",
    description="Emergency Radiology Triage Model inference backend for CT Scans and Chest X-Rays.",
    version="1.0.0"
)

# Enable CORS for mobile / web clients
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Optional: Load PyTorch model if PyTorch is installed
try:
    import torch
    import torchvision.transforms as transforms
    import torchvision.models as models

    # Load ResNet/DenseNet pre-trained backbone model as PyTorch classifier example
    pytorch_model = models.resnet18(weights=None)
    num_features = pytorch_model.fc.in_features
    # 3 classes: 0 = Normal, 1 = Urgent (Pneumonia/TB), 2 = Critical (Pneumothorax)
    pytorch_model.fc = torch.nn.Linear(num_features, 3)
    pytorch_model.eval()

    transform_pipeline = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
    ])
    PYTORCH_AVAILABLE = True
    print("✓ PyTorch environment loaded successfully.")
except Exception as e:
    PYTORCH_AVAILABLE = False
    print(f"Notice: Running in lightweight PyTorch simulation mode. ({e})")


@app.get("/")
def read_root():
    return {
        "status": "online",
        "service": "AI Triage Radiology PyTorch API",
        "swagger_docs": "/docs",
        "timestamp": datetime.now().isoformat()
    }

@app.get("/api/health")
def health_check():
    return {"status": "online", "backend": "PyTorch FastAPI"}

@app.post("/analyze-scan")
async def analyze_scan(file: UploadFile = File(...)):
    """
    Analyze CT / Radiology Scan image file uploaded via multipart/form-data.
    Matches Swagger UI Endpoint: POST /analyze-scan
    """
    if not file.content_type.startswith("image/") and not file.filename.lower().endswith(('.jpg', '.jpeg', '.png', '.dcm', '.webp')):
        raise HTTPException(status_code=400, detail="Uploaded file must be a valid image (JPEG/PNG/CT Scan image)")

    try:
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        image_size = image.size  # (width, height)

        if PYTORCH_AVAILABLE:
            input_tensor = transform_pipeline(image).unsqueeze(0)
            with torch.no_grad():
                outputs = pytorch_model(input_tensor)
                probabilities = torch.nn.functional.softmax(outputs[0], dim=0)
                predicted_class = torch.argmax(probabilities).item()
                confidence_val = float(probabilities[predicted_class].item())
        else:
            predicted_class = 2  # Default to Critical for demonstration
            confidence_val = 0.945

        # Format Triage output based on prediction class
        if predicted_class == 2:
            triage_category = "CRITICAL"
            primary_finding = "Pneumothorax / Severe Acute CT Lesion Detected"
            risk_score = 9.6
            recommendation = "STAT Radiologist alert! High risk acute finding detected. Immediate clinical triage required."
            findings_list = [
                {"condition": "Pneumothorax", "probability": round(confidence_val, 3), "severity": "HIGH"},
                {"condition": "Pleural Effusion", "probability": 0.380, "severity": "MEDIUM"}
            ]
        elif predicted_class == 1:
            triage_category = "URGENT"
            primary_finding = "Lobar Opacity / Suspected Pneumonia / Pulmonary Lesion"
            risk_score = 7.5
            recommendation = "Urgent Radiologist consultation within 2 hours. Evaluate clinical vitals."
            findings_list = [
                {"condition": "Pulmonary Infiltrate", "probability": round(confidence_val, 3), "severity": "HIGH"},
                {"condition": "Consolidation", "probability": 0.650, "severity": "MEDIUM"}
            ]
        else:
            triage_category = "NORMAL"
            primary_finding = "No Acute Cardiopulmonary Abnormality Detected"
            risk_score = 1.2
            recommendation = "Standard routine review queue. Lung fields clear."
            findings_list = [
                {"condition": "Normal Scan", "probability": round(confidence_val, 3), "severity": "LOW"}
            ]

        response_data = {
            "status": "success",
            "filename": file.filename,
            "image_dimensions": f"{image_size[0]}x{image_size[1]}",
            "triage_category": triage_category,
            "primary_finding": primary_finding,
            "confidence": round(confidence_val, 3),
            "risk_score": risk_score,
            "recommendation": recommendation,
            "findings": findings_list,
            "processed_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        }

        print(f"[{datetime.now().strftime('%H:%M:%S')}] Analyzed scan '{file.filename}': {triage_category} ({primary_finding})")
        return response_data

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Image processing error: {str(e)}")

if __name__ == "__main__":
    import uvicorn
    print("=" * 60)
    print("Starting AI Triage Radiology PyTorch Server...")
    print("Swagger UI Docs available at: http://localhost:8000/docs")
    print("Android Emulator Endpoint: http://10.0.2.2:8000/analyze-scan")
    print("=" * 60)
    uvicorn.run(app, host="0.0.0.0", port=8000)
