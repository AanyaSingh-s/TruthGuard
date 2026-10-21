# TruthGuard Frontend Application

The React + TypeScript + Vite frontend provides the user interface for TruthGuard, displaying retrieved evidence, candidate answers, provenance traces, contradiction alerts, and explicit abstention reasons.

## Responsibilities
- User query submission and parameter configuration.
- Real-time visualization of evidence chunks, confidence metrics, and source provenance.
- Clear rendering of detected corpus contradictions and conflict graphs.
- Explicit display of abstention reasons without masking inconsistencies.
- Live service health telemetry across Backend and AI services.

## Technology Stack
- **Framework**: React 18
- **Language**: TypeScript 5.6
- **Build Tool**: Vite 5.4
- **Styling**: Modern CSS design tokens, dark slate theme

## Running Locally

### 1. Install Dependencies
```bash
cd frontend
npm install
```

### 2. Run the Development Server
```bash
cd frontend
npm run dev
```

The application will start on `http://localhost:3000` (or `http://localhost:5173`).

### 3. Build for Production
```bash
cd frontend
npm run build
```

This compiles TypeScript and outputs production bundles to `dist/`.
