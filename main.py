import os
from http.server import HTTPServer
from api.download import handler

port = int(os.environ.get("PORT", 8000))

server = HTTPServer(("0.0.0.0", port), handler)

print(f"Server running on port {port}", flush=True)

server.serve_forever()
