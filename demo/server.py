"""Local synthetic fixtures for the ArbiEdge Android portfolio demo."""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

OPPORTUNITIES = [{
    "country": "Demo",
    "league": "Demo League",
    "match": "Demo United - Sample City",
    "market": "1X2",
    "sourceLabel": "SYNTHETIC DEMO",
    "outcomes": [
        {"selection": "Home", "odds": 2.5, "bookmaker": "Demo A"},
        {"selection": "Draw", "odds": 3.6, "bookmaker": "Demo B"},
        {"selection": "Away", "odds": 3.4, "bookmaker": "Demo C"},
    ],
}]
LIST_PATHS = {"/ai-picks", "/pdf-ai-picks", "/app-notifications"}
STATUS_PATHS = {"/api/auto-big-status", "/api/arb-scan-stats"}

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = urlparse(self.path).path
        status = 200
        if path in {"/arbs", "/arb-history-app"}:
            payload = OPPORTUNITIES
        elif path in LIST_PATHS:
            payload = []
        elif path in STATUS_PATHS:
            payload = {"demo": True, "running": False}
        else:
            status, payload = 404, {"error": "No demo fixture for this endpoint"}
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

if __name__ == "__main__":
    print("Synthetic ArbiEdge demo API: http://127.0.0.1:8000", flush=True)
    ThreadingHTTPServer(("127.0.0.1", 8000), Handler).serve_forever()
