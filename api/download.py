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

    try:
        host = urlparse(url).hostname or ""
        host = host.lower()

        return (
            host == "youtube.com"
            or host.endswith(".youtube.com")
            or host == "youtu.be"
            or host == "youtube-nocookie.com"
            or host.endswith(".youtube-nocookie.com")
            or host == "bilibili.com"
            or host.endswith(".bilibili.com")
            or host == "b23.tv"
            or host.endswith(".b23.tv")
            or host == "douyin.com"
            or host.endswith(".douyin.com")
            or host == "iesdouyin.com"
            or host.endswith(".iesdouyin.com")
        )
    except Exception:
        return False


def make_response(handler, status, data):
    body = json.dumps(
        data,
        ensure_ascii=False
    ).encode("utf-8")

    handler.send_response(status)

    handler.send_header(
        "Content-Type",
        "application/json; charset=utf-8"
    )

    handler.send_header(
        "Access-Control-Allow-Origin",
        "*"
    )

    handler.send_header(
        "Access-Control-Allow-Methods",
        "GET, POST, OPTIONS"
    )

    handler.send_header(
        "Access-Control-Allow-Headers",
        "Content-Type"
    )

    handler.send_header(
        "Cache-Control",
        "no-store"
    )

    handler.send_header(
        "Content-Length",
        str(len(body))
    )

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


def filesize_mb(value):
    if not value:
        return None

    try:
        return round(float(value) / 1024 / 1024, 2)
    except Exception:
        return None


def clean_format(item):
    return {
        "format_id": item.get("format_id"),
        "ext": item.get("ext"),
        "resolution": item.get("resolution"),
        "width": item.get("width"),
        "height": item.get("height"),
        "fps": item.get("fps"),
        "vcodec": item.get("vcodec"),
        "acodec": item.get("acodec"),
        "abr": item.get("abr"),
        "tbr": item.get("tbr"),
        "filesize": item.get("filesize"),
        "filesize_approx": item.get("filesize_approx"),
        "filesize_mb": filesize_mb(
            item.get("filesize")
            or item.get("filesize_approx")
        ),
        "url": item.get("url"),
    }


def extract_video(url):
    options = {
        "quiet": True,
        "no_warnings": False,
        "skip_download": True,
        "noplaylist": True,

        # YouTube JavaScript challenge solver
        "js_runtimes": {
            "deno": {}
        },

        # Allow yt-dlp to obtain EJS when necessary
        "remote_components": {
            "ejs:npm"
        },

        # Browser-like headers
        "http_headers": {
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/140.0.0.0 Safari/537.36"
            ),
            "Accept-Language": "en-US,en;q=0.9",
        },

        "retries": 3,
        "fragment_retries": 3,

        "socket_timeout": 20,

        # Do not download playlists
        "extract_flat": False,
    }

    with yt_dlp.YoutubeDL(options) as ydl:
        info = ydl.extract_info(
            url,
            download=False
        )

    formats = []

    for item in info.get("formats", []):
        media_url = item.get("url")

        if not media_url:
            continue

        formats.append(
            clean_format(item)
        )

    # Remove duplicate format URLs
    unique_formats = []
    seen_urls = set()

    for item in formats:
        media_url = item.get("url")

        if media_url in seen_urls:
            continue

        seen_urls.add(media_url)
        unique_formats.append(item)

    return {
        "success": True,

        "title": info.get("title"),

        "thumbnail": info.get("thumbnail"),

        "duration": info.get("duration"),

        "duration_string": format_duration(
            info.get("duration")
        ),

        "uploader": info.get("uploader"),

        "channel": info.get("channel"),

        "webpage_url": info.get("webpage_url"),

        "id": info.get("id"),

        "extractor": info.get("extractor_key"),

        "ext": info.get("ext"),

        "width": info.get("width"),

        "height": info.get("height"),

        "view_count": info.get("view_count"),

        "formats": unique_formats,
    }


class handler(BaseHTTPRequestHandler):

    def do_OPTIONS(self):
        self.send_response(204)

        self.send_header(
            "Access-Control-Allow-Origin",
            "*"
        )

        self.send_header(
            "Access-Control-Allow-Methods",
            "GET, POST, OPTIONS"
        )

        self.send_header(
            "Access-Control-Allow-Headers",
            "Content-Type"
        )

        self.end_headers()

    def do_GET(self):
        try:
            parsed = urlparse(self.path)

            params = parse_qs(
                parsed.query
            )

            url = params.get(
                "url",
                [""]
            )[0].strip()

            if not url:
                make_response(
                    self,
                    200,
                    {
                        "success": True,
                        "message": (
                            "Downloader API is working."
                        ),
                        "usage": (
                            "/api/download"
                            "?url=YOUR_VIDEO_URL"
                        ),
                    }
                )
                return

            if not is_allowed_url(url):
                make_response(
                    self,
                    400,
                    {
                        "success": False,
                        "error": (
                            "Only YouTube, Bilibili "
                            "and Douyin URLs are supported."
                        ),
                    }
                )
                return

            result = extract_video(url)

            make_response(
                self,
                200,
                result
            )

        except Exception as error:

            message = str(error)

            make_response(
                self,
                500,
                {
                    "success": False,
                    "error": message,
                }
            )

    def do_POST(self):
        try:
            length = int(
                self.headers.get(
                    "Content-Length",
                    0
                )
            )

            raw_body = self.rfile.read(
                length
            )

            try:
                data = json.loads(
                    raw_body.decode("utf-8")
                )
            except Exception:
                data = {}

            url = str(
                data.get(
                    "url",
                    ""
                )
            ).strip()

            if not url:
                make_response(
                    self,
                    400,
                    {
                        "success": False,
                        "error": (
                            "URL မထည့်ရသေးပါ။"
                        ),
                    }
                )
                return

            if not is_allowed_url(url):
                make_response(
                    self,
                    400,
                    {
                        "success": False,
                        "error": (
                            "YouTube, Bilibili "
                            "သို့မဟုတ် Douyin link ပဲ "
                            "ထည့်ပါ။"
                        ),
                    }
                )
                return

            result = extract_video(url)

            make_response(
                self,
                200,
                result
            )

        except Exception as error:

            make_response(
                self,
                500,
                {
                    "success": False,
                    "error": str(error),
                }
            )
