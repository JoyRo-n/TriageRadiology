# AI Triage Radiology - VS Code Backend Guide

This directory contains the Python REST API server designed to be executed directly inside **Visual Studio Code (VS Code)**.

## Quick Start in VS Code

1. Open VS Code and navigate to this `vscode_backend` folder.
2. Install Python dependencies:
   ```bash
   pip install flask flask-cors
   ```
3. Run the backend server:
   ```bash
   python backend_server.py
   ```
4. The server will start on `http://localhost:5000`.

## Connecting from Android Studio / Android App

- **Android Studio Emulator**: Use `http://10.0.2.2:5000/` (default pre-configured in the app).
- **Physical Phone**: Connect phone to the same Wi-Fi network as your PC, find your PC's IP address (e.g. `http://192.168.1.50:5000/`), and enter it in the app's **Backend Settings** menu (top right settings gear).

## API Endpoints

- `GET /api/health` -> Returns backend online status.
- `POST /api/triage` -> Accepts patient details + scan data, returns AI Triage Category (`CRITICAL`, `URGENT`, `NORMAL`), confidence score, primary findings, risk score, and recommended clinical actions.
