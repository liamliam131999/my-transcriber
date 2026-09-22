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

    # cookies.txt ကို ချိတ်ဆက်ပေးထားသော yt_opts configuration
    ydl_opts = {
        'format': 'best',
        'noplaylist': True,
        'cookiefile': 'cookies.txt',  # YouTube bot စစ်ဆေးမှုကို ကျော်လွှတ်ရန် Cookies ဖိုင်ချိတ်ဆက်ခြင်း
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
            error_message = "ဆာဗာဘက်တွင် Cookies သက်တမ်းကုန်ဆုံးနေပါသည် သို့မဟုတ် မှားယွင်းနေပါသည်။ ကျေးဇူးပြု၍ cookies.txt ကို အသစ်လဲလှယ်ပေးပါ။"
        return jsonify({'success': False, 'error': error_message}), 500

if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5000))
    app.run(host='0.0.0.0', port=port)
