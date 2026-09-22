import os
import uuid
import shutil
import subprocess
from pathlib import Path

from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse

app = FastAPI(title="My Transcriber Audio Compressor")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

BASE = Path("/tmp/my-transcriber")
BASE.mkdir(parents=True, exist_ok=True)

MAX_UPLOAD = 250 * 1024 * 1024
TARGET_SIZE = 23 * 1024 * 1024


def run_cmd(cmd):
    result = subprocess.run(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )

    if result.returncode != 0:
        raise RuntimeError(result.stderr[-4000:])

    return result


def get_duration(path):
    result = run_cmd([
        "ffprobe",
        "-v", "error",
        "-show_entries", "format=duration",
        "-of", "default=noprint_wrappers=1:nokey=1",
        str(path)
    ])

    try:
        return float(result.stdout.strip())
    except Exception:
        raise RuntimeError("Audio duration မဖတ်နိုင်ပါ။")


def choose_bitrate(duration):
    if duration <= 0:
        return 64000

    # Target ~23 MB with a little safety margin.
    bitrate = int((TARGET_SIZE * 8 * 0.90) / duration)

    # Keep Opus quality reasonable.
    bitrate = max(24000, min(96000, bitrate))

    return bitrate


@app.get("/")
def home():
    return {
        "status": "online",
        "service": "My Transcriber Audio Compressor"
    }


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/compress")
async def compress(file: UploadFile = File(...)):

    if not file.filename:
        raise HTTPException(400, "File မတွေ့ပါ။")

    work_id = uuid.uuid4().hex

    input_ext = Path(file.filename).suffix.lower()
    if not input_ext:
        input_ext = ".bin"

    input_path = BASE / f"{work_id}{input_ext}"
    output_path = BASE / f"{work_id}.webm"

    try:
        # Save upload to disk in chunks.
        total = 0

        with open(input_path, "wb") as f:
            while True:
                chunk = await file.read(1024 * 1024)

                if not chunk:
                    break

                total += len(chunk)

                if total > MAX_UPLOAD:
                    raise HTTPException(
                        413,
                        "ဖိုင်အရွယ်အစား 250MB ထက်မကျော်ရပါ။"
                    )

                f.write(chunk)

        if total == 0:
            raise HTTPException(400, "ဖိုင်ဗလာဖြစ်နေပါတယ်။")

        duration = get_duration(input_path)
        bitrate = choose_bitrate(duration)

        # Fast single-pass Opus compression.
        run_cmd([
            "ffmpeg",
            "-y",
            "-i", str(input_path),

            "-vn",

            "-c:a", "libopus",
            "-b:a", str(bitrate),

            "-application", "audio",
            "-vbr", "on",

            "-map_metadata", "-1",

            str(output_path)
        ])

        if not output_path.exists():
            raise RuntimeError("Compressed file မထွက်လာပါ။")

        output_size = output_path.stat().st_size

        # If still above 24MB, retry once with lower bitrate.
        if output_size > 24 * 1024 * 1024 and bitrate > 24000:

            bitrate2 = max(24000, int(bitrate * 0.70))

            run_cmd([
                "ffmpeg",
                "-y",
                "-i", str(input_path),
                "-vn",
                "-c:a", "libopus",
                "-b:a", str(bitrate2),
                "-application", "audio",
                "-vbr", "on",
                "-map_metadata", "-1",
                str(output_path)
            ])

        final_size = output_path.stat().st_size

        filename = Path(file.filename).stem
        download_name = f"{filename}_compressed.webm"

        return FileResponse(
            path=str(output_path),
            media_type="audio/webm",
            filename=download_name,
            background=None
        )

    except HTTPException:
        raise

    except Exception as e:
        raise HTTPException(
            500,
            f"FFmpeg compression error: {str(e)}"
        )

    finally:
        # Input file can be deleted immediately.
        try:
            if input_path.exists():
                input_path.unlink()
        except Exception:
            pass
