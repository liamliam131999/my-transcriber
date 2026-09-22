import os
from http.server import HTTPServer
from api.download import Handler

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    HTTPServer(("0.0.0.0", port), Handler).serve_forever()
