#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
source_root="$(cd "$(dirname "$0")/../.." && pwd)"
destination="$source_root/apps/android/assets/default-pet"
revision=db2f6306ce78f6559ab27155d7bfaf00bf43809d
base="https://raw.githubusercontent.com/legeling/awesome-codex-pet/$revision/pets/silver-wolf--lingxiaotian"
mkdir -p "$destination"
fetch() {
    local name="$1" hash="$2"
    if [ ! -f "$destination/$name" ]; then
        curl -fL --max-time 120 -o "$destination/$name.download" "$base/$name"
        printf '%s  %s\n' "$hash" "$destination/$name.download" | sha256sum -c -
        mv "$destination/$name.download" "$destination/$name"
    fi
    printf '%s  %s\n' "$hash" "$destination/$name" | sha256sum -c -
}
fetch pet.json f395110b81abe9178a76f159aba4c1037487b89c969ef9e6ce6c28739d138179
fetch submission.json 4768d98137d5262b47764a47cb14926e55ad73f56be415fc1d216f44bff682cd
fetch spritesheet.webp 6bae5695b739d8503ef5895ba4282e46dcee78dbacf891b50b40d7ef3f237289
