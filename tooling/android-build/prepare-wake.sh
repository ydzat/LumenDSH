#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
: "${DEPS_DIR:?Set DEPS_DIR}"
source_root="$(cd "$(dirname "$0")/../.." && pwd)"
wake="$DEPS_DIR/wake-1.13.8"
assets="$source_root/apps/android/assets/wake"
mkdir -p "$wake" "$assets"
fetch() {
    local file="$1" hash="$2" url="$3"
    if [ ! -f "$file" ]; then curl -fL --retry 2 "$url" -o "$file.part"; mv "$file.part" "$file"; fi
    printf '%s  %s\n' "$hash" "$file" | sha256sum -c -
}
fetch "$wake/runtime.aar" 633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96 https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar
fetch "$wake/java.jar" 77b7b047fade4eadada96b568eb92615049aaf1dc317c7244e46c1ea38b9a63b https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-jvm-1.13.8.jar
fetch "$wake/model.tar.bz2" b2f7c89690dc8ce4c6ed6afeab7cd800c36ad1421fb6b6302b4a4b194cf7f35f https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2
(cd "$wake" && jar xf runtime.aar jni/arm64-v8a/libonnxruntime.so jni/arm64-v8a/libsherpa-onnx-jni.so)
tar -xOf "$wake/model.tar.bz2" sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx > "$assets/encoder.onnx"
printf '%s  %s\n' dd784973fc9d2fabb3b800d6dcd20fc3b0ca84f8e2415afe54b032878e447f4d "$assets/encoder.onnx" | sha256sum -c -
tar -xOf "$wake/model.tar.bz2" sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/decoder-epoch-12-avg-2-chunk-16-left-64.onnx > "$assets/decoder.onnx"
printf '%s  %s\n' fb581d6734511676e246e0dff2fea01b31b0913176cb3ca64576dbab0a177774 "$assets/decoder.onnx" | sha256sum -c -
tar -xOf "$wake/model.tar.bz2" sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx > "$assets/joiner.onnx"
printf '%s  %s\n' f79760052b87239e325f0567c752ad3130b30d92effb847d4307743c20c59a24 "$assets/joiner.onnx" | sha256sum -c -
tar -xOf "$wake/model.tar.bz2" sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/tokens.txt > "$assets/tokens.txt"
printf '%s  %s\n' 72316508d9119696145abc6f1f8cdc46287535c34e5ce7e595f845cb1499cf2e "$assets/tokens.txt" | sha256sum -c -
tar -xOf "$wake/model.tar.bz2" sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/README.md > "$assets/MODEL-README.md"
printf '%s  %s\n' dd0ec68b5d9ee736f016fc982078bd3cdbccc69057e9910bdd45580992d13367 "$assets/MODEL-README.md" | sha256sum -c -
cat > "$assets/files.json" <<'JSON'
{"encoder.onnx": "dd784973fc9d2fabb3b800d6dcd20fc3b0ca84f8e2415afe54b032878e447f4d", "decoder.onnx": "fb581d6734511676e246e0dff2fea01b31b0913176cb3ca64576dbab0a177774", "joiner.onnx": "f79760052b87239e325f0567c752ad3130b30d92effb847d4307743c20c59a24", "tokens.txt": "72316508d9119696145abc6f1f8cdc46287535c34e5ce7e595f845cb1499cf2e", "MODEL-README.md": "dd0ec68b5d9ee736f016fc982078bd3cdbccc69057e9910bdd45580992d13367"}
JSON

fetch "$assets/speaker.onnx" f682b514c05d947ee3fa91cd6ec6c5c7543479a128373fa29b1faedccd21fd11 https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx
fetch "$assets/silero_vad.onnx" 2623a2953f6ff3d2c1e61740c6cdb7168133479b267dfef114a4a3cc5bdd788f https://raw.githubusercontent.com/snakers4/silero-vad/v5.1/src/silero_vad/data/silero_vad.onnx
