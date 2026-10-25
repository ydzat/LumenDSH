#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
: "${OWNER_FIXTURE_DIR:?Set an ignored test-only asset directory}"
mkdir -p "$OWNER_FIXTURE_DIR"
fetch() {
    local name="$1" hash="$2"
    if [ ! -f "$OWNER_FIXTURE_DIR/$name" ]; then
        curl -fL --retry 2 "https://modelscope.cn/api/v1/models/iic/speech_campplus_sv_zh-cn_16k-common/repo?Revision=v1.0.0&FilePath=examples/$name" -o "$OWNER_FIXTURE_DIR/$name"
    fi
    printf '%s  %s\n' "$hash" "$OWNER_FIXTURE_DIR/$name" | sha256sum -c -
}
fetch speaker1_a_cn_16k.wav 5f20ce0ddc378ca3239d3ce864b1142726a46a1221ae553912e4e142045df58b
fetch speaker1_b_cn_16k.wav 20745dc08a4281894d146140b99b9ef7417ac681119b7f7202f553cdf1a85f65
fetch speaker2_a_cn_16k.wav 8a6cffa452df32ef10503f7992f22ffcdd7f16c4e0273d13311bc5cdcb13abf4
