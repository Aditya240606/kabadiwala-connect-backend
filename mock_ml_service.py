"""
Mock ML Service for Kabadiwala Connect (SIH 2026 Problem Statement 26229).

Provides a lightweight, zero-dependency HTTP server mimicking the FastAPI ML inference
endpoint contract (POST /api/v1/predict) for backend development, integration, and runtime testing.

DOES NOT modify or touch the e-waste ML training pipeline or dataset.
"""

import json
import re
import sys
from http.server import HTTPServer, BaseHTTPRequestHandler

PORT = 8000


class MockMlRequestHandler(BaseHTTPRequestHandler):

    def do_GET(self):
        if self.path in ("/", "/docs", "/health"):
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({
                "status": "healthy",
                "service": "mock-ewaste-classifier",
                "version": "dev-prototype"
            }).encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path.startswith("/api/v1/predict"):
            content_length = int(self.headers.get("Content-Length", 0))
            if content_length > 0:
                body = self.rfile.read(content_length)
            elif self.headers.get("Transfer-Encoding", "").lower() == "chunked":
                body = b""
                while True:
                    line = self.rfile.readline().strip()
                    if not line:
                        break
                    chunk_len = int(line, 16)
                    if chunk_len == 0:
                        self.rfile.readline()
                        break
                    body += self.rfile.read(chunk_len)
                    self.rfile.readline()
            else:
                body = b""

            content_type = self.headers.get("Content-Type", "")

            predicted_class = "PCB"
            confidence = 0.95

            # Inspect if a specific class hint is passed in query string or payload
            if b"battery" in body.lower() or "class=battery" in self.path.lower():
                predicted_class = "Battery"
                confidence = 0.92
            elif b"smartphone" in body.lower() or "class=smartphone" in self.path.lower():
                predicted_class = "Smartphone"
                confidence = 0.96
            elif b"display" in body.lower() or "class=display" in self.path.lower():
                predicted_class = "Flat-Panel-Monitor"
                confidence = 0.89

            response_payload = {
                "model_name": "mock-ewaste-classifier",
                "model_version": "dev",
                "predicted_class": predicted_class,
                "confidence": confidence,
                "inference_time_ms": 40,
                "needs_confirmation": True
            }

            resp_bytes = json.dumps(response_payload).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(resp_bytes)))
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(resp_bytes)
            self.wfile.flush()
        else:
            self.send_response(404)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"error": "Not Found"}).encode("utf-8"))

    def log_message(self, format, *args):
        # Concise console logging
        sys.stderr.write(f"[MockMLService] {self.address_string()} - {format % args}\n")


def run(port=PORT):
    server_address = ("", port)
    httpd = HTTPServer(server_address, MockMlRequestHandler)
    print(f"Mock ML Inference Service listening on http://localhost:{port} (Endpoint: POST /api/v1/predict)")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down Mock ML Service.")
        httpd.server_close()


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else PORT
    run(port)
