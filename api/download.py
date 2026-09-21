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

    if not (url.startswith("http://") or url.startswith("https://")):
        return False

    return any(domain in url for domain in ALLOWED_DOMAINS)


def handler(request):
    headers = {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
        "Access-Control-Allow-Headers": "Content-Type",
        "Content-Type": "application/json; charset=utf-8",
    }

    if request.method == "OPTIONS":
        return {
            "statusCode": 204,
            "headers": headers,
            "body": "",
        }

    try:
        if request.method == "POST":
            try:
                body = request.body
                if isinstance(body, bytes):
                    body = body.decode("utf-8")

                data = json.loads(body or "{}")
            except Exception:
                data = {}

            url = str(data.get("url", "")).strip()

        else:
            url = str(
                request.query.get("url", "")
                if hasattr(request, "query")
                else ""
            ).strip()

        if not url:
            return response(
                400,
                {"success": False, "error": "URL မထည့်ရသေးပါ။"},
                headers,
            )

        if not is_allowed_url(url):
            return response(
                400,
                {
                    "success": False,
                    "error": "YouTube, Bilibili သို့မဟုတ် Douyin link ပဲ ထည့်ပါ။",
                },
                headers,
            )

        ydl_opts = {
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            "noplaylist": True,
            "extract_flat": False,
        }

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)

        formats = []

        for f in info.get("formats", []):
            format_url = f.get("url")

            if not format_url:
                continue

            formats.append({
                "format_id": f.get("format_id"),
                "ext": f.get("ext"),
                "resolution": f.get("resolution"),
                "width": f.get("width"),
                "height": f.get("height"),
                "fps": f.get("fps"),
                "vcodec": f.get("vcodec"),
                "acodec": f.get("acodec"),
                "filesize": f.get("filesize"),
                "filesize_approx": f.get("filesize_approx"),
                "tbr": f.get("tbr"),
                "url": format_url,
            })

        result = {
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

        return response(200, result, headers)

    except Exception as e:
        return response(
            500,
            {
                "success": False,
                "error": str(e),
            },
            headers,
        )


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

    if hours > 0:
        return f"{hours:02d}:{minutes:02d}:{secs:02d}"

    return f"{minutes:02d}:{secs:02d}"


def response(status, data, headers):
    return {
        "statusCode": status,
        "headers": headers,
        "body": json.dumps(data, ensure_ascii=False),
                          }
