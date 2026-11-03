#!/usr/bin/env bash
# Runs on-device OCR over eval/data/<id>/<image> and pulls ocr.txt / ocr_lines.json back.
# Requires one Android device (or emulator) connected via adb. See eval/README.md.
set -euo pipefail

cd "$(dirname "$0")/.."
DATA_DIR="eval/data"
PACKAGE="com.localbudget.app"
REMOTE_DIR="/sdcard/Android/data/$PACKAGE/files/eval"

if [ ! -d "$DATA_DIR" ]; then
  echo "No $DATA_DIR directory. Put receipts in $DATA_DIR/<id>/ first." >&2
  exit 1
fi

./gradlew :app:installDebug :app:installDebugAndroidTest

adb shell rm -rf "$REMOTE_DIR"
adb shell mkdir -p "$REMOTE_DIR"
for dir in "$DATA_DIR"/*/; do
  id="$(basename "$dir")"
  adb shell mkdir -p "$REMOTE_DIR/$id"
  for image in "$dir"*.jpg "$dir"*.jpeg "$dir"*.png "$dir"*.webp "$dir"*.JPG "$dir"*.JPEG "$dir"*.PNG; do
    [ -e "$image" ] && adb push "$image" "$REMOTE_DIR/$id/" > /dev/null
  done
done

adb shell am instrument -w \
  -e class "$PACKAGE.eval.OcrDumpTest" \
  "$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner"

for dir in "$DATA_DIR"/*/; do
  id="$(basename "$dir")"
  for f in ocr.txt ocr_lines.json; do
    adb pull "$REMOTE_DIR/$id/$f" "$dir$f" > /dev/null 2>&1 || echo "warning: no $f for $id (no image?)" >&2
  done
done

# Receipt photos are personal data: don't leave copies on the device
adb shell rm -rf "$REMOTE_DIR"

echo "Done. Next: ./gradlew :app:testDebugUnitTest --tests '*ScanAccuracyReportTest*'"
