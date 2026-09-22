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

    # YouTube နှင့် Douyin နှစ်ခုစလုံးအတွက် cookies လိုအပ်မှုကို ရှောင်ရှားရန်နှင့် extractor options များကို အကောင်းဆုံးဖြစ်အောင် ပြင်ဆင်ထားသည်
    ydl_opts = {
        'format': 'best',
        'noplaylist': True,
        'extractor_args': {
            'youtube': {'player_client': ['ios', 'android', 'web']},
            'douyin': {}
        },
        'socket_timeout': 30,
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            video_title = info.get('title', 'Downloaded Video')
            
            # ဗီဒီယို direct link ရှာရန်
            download_url = info.get('url')
            if not download_url and 'formats' in info:
                # အကောင်းဆုံး format ကို ရွေးချယ်ပေးရန်
                formats = info.get('formats', [])
                for f in formats:
                    if f.get('url'):
                        download_url = f.get('url')
                        break

            if not download_url:
                return jsonify({'success': False, 'error': 'ဒေါင်းလုဒ်လင့်ခ် ရှာမတွေ့ပါ။'}), 500
            
            return jsonify({
                'success': True,
                'title': video_title,
                'download_url': download_url
            })
    except Exception as e:
        error_message = str(e)
        if "cookies" in error_message.lower():
            error_message = "ဆာဗာဘက်တွင် Cookies ကန့်သတ်ချက်ရှိနေပါသည်။ ကျေးဇူးပြု၍ ခဏစောင့်ပါ သို့မဟုတ် အခြားလင့်ခ်ဖြင့် ပြန်ကြိုးစားပါ။"
        return jsonify({'success': False, 'error': error_message}), 500

if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5000))
    app.run(host='0.0.0.0', port=port)
