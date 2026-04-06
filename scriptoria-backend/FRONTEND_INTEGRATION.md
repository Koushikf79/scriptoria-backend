# Scriptoria WebSocket – Frontend Integration Guide

## WebSocket Connection

```javascript
// Connect to the analysis pipeline
const ws = new WebSocket('ws://localhost:8080/ws/analyze');

ws.onopen = () => {
  // Send screenplay + optional market
  ws.send(JSON.stringify({
    screenplay: `INT. COFFEE SHOP - DAY\n\nJANE sits alone, staring at her phone...\n\nEXT. ROOFTOP - NIGHT\n\nJOHN looks out over the city...`,
    market: 'TOLLYWOOD'   // TOLLYWOOD | BOLLYWOOD | HOLLYWOOD | KOREAN | GENERAL
  }));
};

ws.onmessage = (event) => {
  const msg = JSON.parse(event.data);

  switch (msg.type) {

    case 'PROGRESS':
      // { type, stage, percentage, message }
      console.log(`[${msg.stage}] ${msg.percentage}% — ${msg.message}`);
      updateProgressBar(msg.percentage);
      break;

    case 'ANALYSIS':
      // { type, data: ScriptAnalysisResponse }
      renderSceneTable(msg.data.scenes);
      renderMetricCards(msg.data);
      break;

    case 'EMOTION':
      // { type, data: EmotionAnalysisResponse }
      renderEmotionChart(msg.data.arc);
      break;

    case 'BUDGET':
      // { type, data: BudgetSimulationResponse }
      renderBudgetCards(msg.data);
      break;

    case 'COMPLETE':
      console.log('✅ Full pipeline complete');
      ws.close();
      break;

    case 'ERROR':
      console.error('Pipeline error:', msg.message);
      break;
  }
};
```

---

## Director Mode (REST)

After the WS pipeline completes, when the user selects a scene and clicks "Explore Visually":

```javascript
const scene = selectedScene; // SceneDto from ANALYSIS payload

const res = await fetch('http://localhost:8080/api/v1/storyboard/generate', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    sceneNumber: scene.sceneNumber,
    sceneDescription: scene.description,
    location: scene.location,
    timeOfDay: scene.timeOfDay,
    dominantEmotion: scene.dominantEmotion,
    actionIntensity: scene.actionIntensity
  })
});

const storyboard = await res.json();
// storyboard.variations → array of 6 ShotVariation objects
// storyboard.directorNote → AI director recommendation

renderStoryboardCards(storyboard.variations);
```

---

## WebSocket Event Reference

| type       | payload fields                                      |
|------------|-----------------------------------------------------|
| PROGRESS   | stage, percentage (0-100), message                  |
| ANALYSIS   | data: ScriptAnalysisResponse                        |
| EMOTION    | data: EmotionAnalysisResponse                       |
| BUDGET     | data: BudgetSimulationResponse                      |
| COMPLETE   | —                                                   |
| ERROR      | message                                             |

### PROGRESS stages (in order)
1. `SCRIPT_ANALYSIS` (10% → 35%)
2. `EMOTION_ANALYSIS` (40% → 65%)
3. `BUDGET_SIMULATION` (70% → 95%)
4. `COMPLETE` (100%)

---

## ScriptAnalysisResponse – Key Fields

```json
{
  "totalScenes": 42,
  "genre": "Crime Thriller",
  "scriptTone": "Gritty urban realism with dark humor",
  "allCharacters": ["ARJUN", "PRIYA", "INSPECTOR"],
  "nightScenesCount": 17,
  "vfxScenesCount": 4,
  "avgActionIntensity": 6.2,
  "avgEmotionalIntensity": 7.1,
  "locationFrequency": { "ROOFTOP": 5, "COFFEE SHOP": 3 },
  "scenes": [
    {
      "sceneNumber": 1,
      "location": "COFFEE SHOP",
      "timeOfDay": "DAY",
      "interior": "INT",
      "characters": ["ARJUN", "PRIYA"],
      "description": "Arjun confronts Priya about the missing evidence.",
      "actionIntensity": 3.0,
      "emotionalIntensity": 8.5,
      "productionComplexity": 2.0,
      "dominantEmotion": "TENSION",
      "hasVfx": false,
      "hasStunt": false,
      "hasLargecrowd": false
    }
  ]
}
```

---

## BudgetSimulationResponse – Key Fields

```json
{
  "market": "TOLLYWOOD",
  "currency": "INR",
  "costDrivers": [
    "17 night scenes increase lighting & generator cost significantly",
    "4 VFX scenes require post-production investment"
  ],
  "low":  { "tier": "LOW",  "totalBudget": 50000000,  "castBudget": 15000000, ... },
  "mid":  { "tier": "MID",  "totalBudget": 200000000, ... },
  "high": { "tier": "HIGH", "totalBudget": 800000000, ... }
}
```

---

## StoryboardResponse – Key Fields

```json
{
  "sceneNumber": 12,
  "directorNote": "The rooftop confrontation calls for a Dutch angle...",
  "variations": [
    {
      "shotType": "WIDE_SHOT",
      "lightingStyle": "NIGHT_NEON",
      "lens": "24mm wide",
      "cameraMovement": "DOLLY_IN",
      "mood": "Claustrophobic despite the open sky",
      "composition": "Subject silhouetted against city lights, deep negative space above",
      "colorGrading": "Teal shadows, amber highlights",
      "detailedPrompt": "Camera starts wide on the rain-soaked rooftop..."
    }
  ]
}
```
