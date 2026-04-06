# 🎬 Scriptoria Backend

AI-Powered Film Pre-Production Intelligence System

---

## ✅ Setup (2 minutes)

### 1. Get a FREE OpenRouter API key
Go to **https://openrouter.ai** → Sign up → Copy your key (starts with `sk-or-...`)
No credit card needed. Free credits included.

### 2. Paste your key

Open `src/main/resources/application.yml` and replace:
```yaml
openrouter:
  api-key: PASTE_YOUR_OPENROUTER_KEY_HERE
```
with your actual key. Or set as environment variable:
```bash
export OPENROUTER_API_KEY=sk-or-v1-...your-key...
```

### 3. Run
```bash
mvn spring-boot:run
```

### 4. Verify
- Health:    http://localhost:8080/api/v1/health
- Swagger:   http://localhost:8080/swagger-ui.html

---

## Architecture

```
Frontend React
    │
    ├── WebSocket  ws://localhost:8080/ws/analyze
    │     Send:    { "screenplay": "...", "market": "TOLLYWOOD" }
    │     Receive: PROGRESS → ANALYSIS → EMOTION → BUDGET → COMPLETE
    │
    └── REST  POST /api/v1/storyboard/generate
              Send:    { sceneNumber, sceneDescription, location, ... }
              Receive: 6 cinematic shot variations
```

## AI Model
- Provider: **OpenRouter**
- Model: **openai/gpt-oss-20b:nitro**
- Streaming: WebSocket pipeline streams progress events while AI processes
- All 4 features use AI: Script Analysis, Emotion Arc, Budget Simulation, Storyboard

## Market Options
| Value       | Currency | Range               |
|-------------|----------|---------------------|
| TOLLYWOOD   | INR      | ₹2 Cr – ₹200 Cr     |
| BOLLYWOOD   | INR      | ₹3 Cr – ₹500 Cr     |
| HOLLYWOOD   | USD      | $1M – $250M         |
| KOREAN      | KRW      | ₩1B – ₩60B          |
| GENERAL     | USD      | $500K – $120M       |
