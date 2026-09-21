from http.server import BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
import json
import yt_dlp


ALLOWED_DOMAINS = (
    "youtube.com",
    "youtu.be",
    "youtube-nocookie.com",
    "bilibili.com",
    "b23.tv",
    "douyin.com",
    "iesdouyin.com",
)


def is_allowed_url(url):
    url = url.lower().strip()

    if not url.startswith(("http://", "https://")):
        return False

    return any(domain in url for domain in ALLOWED_DOMAINS)


def make_response(handler, status, data):
    body = json.dumps(data, ensure_ascii=False).encode("utf-8")

    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Access-Control-Allow-Origin", "*")
    handler.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
    handler.send_header("Access-Control-Allow-Headers", "Content-Type")
    handler.send_header("Content-Length", str(len(body)))
    handler.end_headers()

    handler.wfile.write(body)


def format_duration(seconds):
    if not seconds:
        return None

    try:
        seconds = int(seconds)
    except Exception:
        return None

    hours = seconds // 3600
    minutes = (seconds % 3600) // 60
    secs = seconds % 60

    if hours:
        return f"{hours:02d}:{minutes:02d}:{secs:02d}"

    return f"{minutes:02d}:{secs:02d}"


def extract_video(url):
    options = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
    }

    with yt_dlp.YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=False)

    formats = []

    for item in info.get("formats", []):
        if not item.get("url"):
            continue

        formats.append({
            "format_id": item.get("format_id"),
            "ext": item.get("ext"),
            "resolution": item.get("resolution"),
            "width": item.get("width"),
            "height": item.get("height"),
            "fps": item.get("fps"),
            "vcodec": item.get("vcodec"),
            "acodec": item.get("acodec"),
            "filesize": item.get("filesize"),
            "filesize_approx": item.get("filesize_approx"),
            "tbr": item.get("tbr"),
            "url": item.get("url"),
        })

    return {
        "success": True,
        "title": info.get("title"),
        "thumbnail": info.get("thumbnail"),
        "duration": info.get("duration"),
        "duration_string": format_duration(info.get("duration")),
        "uploader": info.get("uploader"),
        "webpage_url": info.get("webpage_url"),
        "extractor": info.get("extractor_key"),
        "formats": formats,
    }


class handler(BaseHTTPRequestHandler):

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_GET(self):
        try:
            parsed = urlparse(self.path)
            params = parse_qs(parsed.query)

            url = params.get("url", [""])[0].strip()

            if not url:
                make_response(
                    self,
                    200,
                    {
                        "success": True,
                        "message": "Downloader API is working.",
                        "usage": "/api/download?url=YOUR_VIDEO_URL",
                    },
                )
                return

            if not is_allowed_url(url):
                make_response(
                    self,
                    400,
                    {
                        "success": False,
                        "error": "Only YouTube, Bilibili and Douyin URLs are supported.",
                    },
                )
                return

            result = extract_video(url)
            make_response(self, 200, result)

        except Exception as error:
            make_response(
                self,
                500,
                {
                    "success": False,
                    "error": str(error),
                },
            )

    def do_POST(self):
        try:
            length = int(self.headers.get("Content-Length", 0))

            raw_body = self.rfile.read(length)

            try:
                data = json.loads(raw_body.decode("utf-8"))
            except Exception:
                data = {}

            url = str(data.get("url", "")).strip()

            if not url:
                make_response(
                    self,
                    400,
                    {
                        "success": False,
                        "error": "URL မထည့်ရသေးပါ။",
                    },
                )
                return

            if not is_allowed_url(url):
                make_response(
                    self,
                    400,
                    {
                        "success": False,
                        "error": "YouTube, Bilibili သို့မဟုတ် Douyin link ပဲ ထည့်ပါ။",
                    },
                )
                return

            result = extract_video(url)
            make_response(self, 200, result)

        except Exception as error:
            make_response(
                self,
                500,
                {
                    "success": False,
                    "error": str(error),
                },
            )
