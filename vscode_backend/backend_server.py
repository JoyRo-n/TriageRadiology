"""
AI Triage Radiology - Python REST API Backend (VS Code)
======================================================
Run this script in VS Code to host the AI Triage Radiology backend REST API.

Installation:
    pip install flask flask-cors

Usage:
    python backend_server.py

Endpoints:
    GET  /api/health
    POST /api/triage
"""

import time
import random
from datetime import datetime
from flask import Flask, request, jsonify

app = Flask(__name__)

# Mock AI Model findings database for simulation
AI_FINDINGS_DATABASE = {
    "pneumothorax": {
        "triage_category": "CRITICAL",
        "primary_finding": "Pneumothorax (Left Upper Lobe)",
        "confidence": 0.952,
        "risk_score": 9.7,
        "recommendation": "STAT Radiologist alert! High probability of tension pneumothorax. Immediate clinical triage and chest tube evaluation required.",
        "findings": [
            {"condition": "Pneumothorax", "probability": 0.952, "severity": "HIGH"},
            {"condition": "Pleural Effusion", "probability": 0.380, "severity": "MEDIUM"}
        ]
    },
    "pneumonia": {
        "triage_category": "URGENT",
        "primary_finding": "Lobar Pneumonia (Right Lower Lobe)",
        "confidence": 0.891,
        "risk_score": 7.5,
        "recommendation": "Urgent Radiologist consultation within 2 hours. Initiate empirical antibiotic therapy based on clinical guidelines.",
        "findings": [
            {"condition": "Pneumonia", "probability": 0.891, "severity": "HIGH"},
            {"condition": "Consolidation", "probability": 0.730, "severity": "MEDIUM"}
        ]
    },
    "tuberculosis": {
        "triage_category": "URGENT",
        "primary_finding": "Apical Infiltrate & Cavitary Lesion (Suspected TB)",
        "confidence": 0.874,
        "risk_score": 7.9,
        "recommendation": "Urgent review. Place patient under respiratory isolation and order GeneXpert / AFB sputum smear.",
        "findings": [
            {"condition": "Tuberculosis Infiltrate", "probability": 0.874, "severity": "HIGH"},
            {"condition": "Cavitary Lesion", "probability": 0.650, "severity": "MEDIUM"}
        ]
    },
    "normal": {
        "triage_category": "NORMAL",
        "primary_finding": "No Significant Acute Cardiopulmonary Abnormality",
        "confidence": 0.968,
        "risk_score": 1.1,
        "recommendation": "Standard routine queue. Lung fields clear, no focal consolidation, cardiomegaly, or pneumothorax.",
        "findings": [
            {"condition": "Normal Chest Radiograph", "probability": 0.968, "severity": "LOW"}
        ]
    }
}

@app.route('/api/health', methods=['GET'])
def health_check():
    return jsonify({
        "status": "online",
        "service": "AI Triage Radiology API",
        "version": "1.0.0",
        "timestamp": datetime.now().isoformat()
    }), 200

@app.route('/api/triage', methods=['POST'])
def perform_triage():
    # Simulate processing time for AI deep learning model inference
    time.sleep(0.5)

    data = request.get_json(silent=True) or {}
    patient_id = data.get('patient_id', 'UNKNOWN')
    patient_name = data.get('patient_name', 'Anonymous')
    age = data.get('age', 40)
    gender = data.get('gender', 'Male')
    modality = data.get('modality', 'Chest X-Ray')

    # Determine simulated finding based on patient ID or random
    patient_id_lower = str(patient_id).lower()
    if "pneumothorax" in patient_id_lower or "101" in patient_id_lower:
        result = AI_FINDINGS_DATABASE["pneumothorax"]
    elif "pneumonia" in patient_id_lower or "102" in patient_id_lower:
        result = AI_FINDINGS_DATABASE["pneumonia"]
    elif "tb" in patient_id_lower or "tuberculosis" in patient_id_lower or "103" in patient_id_lower:
        result = AI_FINDINGS_DATABASE["tuberculosis"]
    elif "normal" in patient_id_lower or "104" in patient_id_lower:
        result = AI_FINDINGS_DATABASE["normal"]
    else:
        # Default to critical or urgent for demonstration
        result = random.choice(list(AI_FINDINGS_DATABASE.values()))

    response_payload = {
        "status": "success",
        "patient_id": patient_id,
        "patient_name": patient_name,
        "age": age,
        "gender": gender,
        "modality": modality,
        "triage_category": result["triage_category"],
        "primary_finding": result["primary_finding"],
        "confidence": result["confidence"],
        "risk_score": result["risk_score"],
        "recommendation": result["recommendation"],
        "findings": result["findings"],
        "processed_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    }

    print(f"[{datetime.now().strftime('%H:%M:%S')}] Triage Processed for {patient_id} ({patient_name}): {result['triage_category']} - {result['primary_finding']}")
    return jsonify(response_payload), 200

if __name__ == '__main__':
    print("=" * 60)
    print("Starting AI Triage Radiology Backend Server in VS Code...")
    print("Listening on http://0.0.0.0:5000")
    print("Android Emulator Base URL: http://10.0.2.2:5000/")
    print("=" * 60)
    app.run(host='0.0.0.0', port=5000, debug=True)
