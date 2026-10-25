//! Staged hoyoTTS inference using the project's pinned ort runtime and tokenizer.
use anyhow::{Result, anyhow, ensure};
use ort::{ep::CPU, inputs, session::Session, value::Tensor};
use serde::{Deserialize, Serialize};
use std::{path::Path, fs};

#[derive(Deserialize, Serialize)]
struct Row {
    text: String, phones: Vec<i64>, tones: Vec<i64>, language: Vec<i64>, word2ph: Vec<usize>,
}
fn session(path: &Path) -> Result<Session> {
    Session::builder().map_err(|e|anyhow!("{e}"))?
        .with_execution_providers([CPU::default().with_arena_allocator(false).build()]).map_err(|e|anyhow!("{e}"))?
        .with_intra_threads(2).map_err(|e|anyhow!("{e}"))?
        .with_prepacking(false).map_err(|e|anyhow!("{e}"))?
        .with_memory_pattern(false).map_err(|e|anyhow!("{e}"))?
        .with_intra_op_spinning(false).map_err(|e|anyhow!("{e}"))?
        .with_inter_op_spinning(false).map_err(|e|anyhow!("{e}"))?
        .commit_from_file(path).map_err(|e|anyhow!("{e}"))
}
fn main() -> Result<()> {
    let args: Vec<_> = std::env::args().collect();
    ensure!(args.len()==5,"Usage: hoyo_native prepare|synthesize models rows.json output");
    let models=Path::new(&args[2]);
    let rows: Vec<Row>=serde_json::from_slice(&fs::read(&args[3])?)?;
    ensure!(!rows.is_empty() && rows.len()<=1000,"Invalid segment count");
    for row in &rows {
        let n=row.phones.len();
        ensure!(n>0 && n<=512 && row.tones.len()==n && row.language.len()==n
            && row.word2ph.iter().sum::<usize>()==n,"Invalid phoneme alignment");
    }
    let features=Path::new(&args[3]).parent().ok_or_else(||anyhow!("Missing request directory"))?;
    if args[1]=="prepare" {
        let mut bert=session(&models.join("bert.onnx"))?;
        let tokenizer=tokenizers::Tokenizer::from_file(models.join("tokenizer.json")).map_err(|e|anyhow!("{e}"))?;
        for (i,row) in rows.iter().enumerate() {
            let encoded=tokenizer.encode(row.text.as_str(),true).map_err(|e|anyhow!("{e}"))?;
            let ids:Vec<i64>=encoded.get_ids().iter().map(|&x|x as i64).collect();
            let length=ids.len();
            ensure!(length==row.word2ph.len(),"Tokenizer alignment mismatch");
            let output=bert.run(inputs![
                "input_ids"=>Tensor::from_array(([1,length],ids))?,
                "attention_mask"=>Tensor::from_array(([1,length],vec![1i64;length]))?,
                "token_type_ids"=>Tensor::from_array(([1,length],vec![0i64;length]))?
            ])?;
            let (shape, values)=output["bert_feature"].try_extract_tensor::<f32>()?;
            ensure!(shape.as_ref()==[length as i64,1024],"Unexpected BERT output");
            let n=row.phones.len();
            let mut bytes=Vec::with_capacity(4*1024*n);
            for channel in 0..1024 {
                for (token,&repeat) in row.word2ph.iter().enumerate() {
                    let value=values[token*1024+channel];
                    ensure!(value.is_finite(),"Nonfinite BERT feature");
                    for _ in 0..repeat { bytes.extend_from_slice(&value.to_le_bytes()); }
                }
            }
            fs::write(features.join(format!("{i}.features")),bytes)?;
        }
        fs::write(&args[4],b"prepared")?;
    } else if args[1]=="synthesize" {
        let mut model=session(&models.join("hoyotts.onnx"))?;
        let mut wav=hound::WavWriter::create(&args[4],hound::WavSpec {
            channels:1,sample_rate:44100,bits_per_sample:16,sample_format:hound::SampleFormat::Int
        })?;
        let mut total=0usize;
        for (i,row) in rows.into_iter().enumerate() {
            let n=row.phones.len();
            let bytes=fs::read(features.join(format!("{i}.features")))?;
            ensure!(bytes.len()==4*1024*n,"Invalid feature file");
            let features:Vec<f32>=bytes.chunks_exact(4).map(|x|f32::from_le_bytes(x.try_into().unwrap())).collect();
            let out=model.run(inputs![
                "phones"=>Tensor::from_array(([1,n],row.phones))?,
                "lengths"=>Tensor::from_array(([1],vec![n as i64]))?,
                "speaker"=>Tensor::from_array(([1],vec![221i64]))?,
                "tones"=>Tensor::from_array(([1,n],row.tones))?,
                "language"=>Tensor::from_array(([1,n],row.language))?,
                "bert"=>Tensor::from_array(([1,1024,n],features))?,
                "noise"=>Tensor::from_array((Vec::<usize>::new(),vec![0.6f32]))?,
                "noise_w"=>Tensor::from_array((Vec::<usize>::new(),vec![0.8f32]))?
            ])?;
            let (_,audio)=out["audio"].try_extract_tensor::<f32>()?;
            ensure!(!audio.is_empty() && audio.iter().all(|x|x.is_finite()),"Invalid waveform");
            total+=audio.len();
            ensure!(total<=44100*300,"Generated speech exceeds five minutes");
            if i>0 { for _ in 0..4410 {wav.write_sample(0i16)?;} }
            for &x in audio {wav.write_sample((x.clamp(-1.0,1.0)*32767.0) as i16)?;}
        }
        wav.finalize()?;
    } else {return Err(anyhow!("Unknown stage"));}
    Ok(())
}
