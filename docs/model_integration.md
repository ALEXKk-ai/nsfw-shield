# NSFW Shield — Model Integration Guide

## Image Classification Models

### Primary: MobileNet NSFW v2
- **Format**: TensorFlow Lite (`.tflite`)
- **Input**: 224×224 RGB bitmap
- **Output**: 5-class probability array
- **Classes**: `safe`, `sketchy`, `explicit_sexual`, `explicit_violence`, `other`
- **Asset path**: `assets/nsfw_mobilenet.tflite`

### Fallback Behavior
If the model file is missing at runtime, `ImageClassifier` returns all-zero scores (ALLOW).

### Adding Your Own Model
1. Place `.tflite` file in `app/src/main/assets/`
2. Update `MODEL_FILENAME` in `ImageClassifier.kt`
3. Verify input shape matches `INPUT_SIZE` (224) and `PIXEL_SIZE` (3)
4. Map output indices in `OUTPUT_LABELS`

## Text Classification

### Two-Layer Pipeline
1. **Keyword Database** (fast path) — regex-based pattern matching across 6 categories
2. **NLP Model** (slow path, optional) — ML Kit text recognition for deeper analysis

### Adding Categories
Add patterns to `KEYWORD_PATTERNS` map in `TextClassifier.kt`:
```kotlin
ContentCategory.NEW_CATEGORY to listOf("pattern1", "pattern2", ...)
```

## Video/Audio

### Video Handler
- Uses `MediaMetadataRetriever` to extract keyframes
- Configurable: `KEYFRAME_INTERVAL_MS`, `MAX_KEYFRAMES`
- Each keyframe passed through `ImageClassifier`

### Audio Handler
- Placeholder for Whisper-based transcription
- Transcribed text routed to `TextClassifier`

## Context Rescoring

The `ContextRescorer` reduces false positives by analyzing:
- Medical/educational keywords → score reduced by 40-60%
- Safe domains (CDC, NIH, Wikipedia, etc.) → score reduced by 50%
- App-level context (gallery vs browser vs messaging)
