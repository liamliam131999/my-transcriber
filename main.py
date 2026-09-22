from flask import Flask, request, jsonify
from flask_cors import CORS
import yt_dlp
import os

app = Flask(__name__)
CORS(app)

@app.route('/download', methods=['POST'])
def download_video():
    data = request.get_json()
    url = data.get('url')
    
    if not url:
        return jsonify({'success': False, 'error': 'ဗီဒီယိုလင့်ခ် ထည့်သွင်းရန် လိုအပ်ပါသည်။'}), 400

    # YouTube Bot ကန့်သတ်ချက်ကို ကျော်လွှားရန် options များ ထည့်သွင်းထားသည်
    ydl_opts = {
        'format': 'best',
        'noplaylist': True,
        'user_agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'extractor_args': {'youtube': {'player_client': ['android', 'web']}}
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            video_title = info.get('title', 'Downloaded Video')
            download_url = info.get('url')
            
            return jsonify({
                'success': True,
                'title': video_title,
                'download_url': download_url
            })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5000))
    app.run(host='0.0.0.0', port=port)
