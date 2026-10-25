"""Reuse pinned hoyoTTS Chinese normalization, G2P and tone sandhi on Termux."""
import argparse
import json
from pathlib import Path
import re
import sys


def prepare(text, frontend, dependencies):
    sys.path[:0] = [str(dependencies), str(frontend)]
    from text.chinese import text_normalize, g2p
    from text import cleaned_text_to_sequence
    if re.search(r'[A-Za-z]', text):
        raise ValueError('This character model supports Chinese text; use system speech for English')
    normalized = text_normalize(text)
    if not re.search(r'[\u4e00-\u9fa5]', normalized):
        raise ValueError('No supported Chinese speech content')
    chunks = []
    current = ''
    for char in normalized:
        current += char
        if char in '.!?…,' or len(current) >= 48:
            if re.search(r'[\u4e00-\u9fa5]', current):
                chunks.append(current)
            current = ''
    if current:
        chunks.append(current)
    rows = []
    for segment in chunks:
        phones, tones, repetitions = g2p(segment)
        phones, tones, languages = cleaned_text_to_sequence(phones, tones, 'ZH')
        def blanks(values):
            output = [0] * (len(values) * 2 + 1)
            output[1::2] = values
            return output
        repetitions = [n * 2 for n in repetitions]
        repetitions[0] += 1
        phones, tones, languages = map(blanks, (phones, tones, languages))
        if sum(repetitions) != len(phones):
            raise ValueError('Phoneme alignment mismatch')
        rows.append(dict(text=segment, phones=phones, tones=tones,
                         language=languages, word2ph=repetitions))
    return rows


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--frontend', type=Path, required=True)
    parser.add_argument('--dependencies', type=Path, required=True)
    parser.add_argument('--text-file', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        rows = prepare(args.text_file.read_text(encoding='utf-8'), args.frontend, args.dependencies)
        args.output.write_text(json.dumps(rows, ensure_ascii=False), encoding='utf-8')
    except ValueError as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(2)
