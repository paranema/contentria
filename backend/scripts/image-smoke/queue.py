"""이미지 실행 검증용 Cloudflare Queues 응답. 실제 서비스에는 사용하지 않는다."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json

state = {"message": None, "acked": False, "pulls": 0}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def do_GET(self):
        self.reply(state)

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        if self.path == "/enqueue":
            state.update(message=body, acked=False)
        elif self.path.endswith("/pull"):
            state["pulls"] += 1
            messages = [] if state["message"] is None or state["acked"] else [
                {"lease_id": "smoke-lease", "body": state["message"]}
            ]
            self.reply({"success": True, "result": {"messages": messages}})
            return
        elif self.path.endswith("/ack"):
            state["acked"] = body.get("acks") == [{"lease_id": "smoke-lease"}]
        self.reply({"success": True, "result": {}})

    def reply(self, value):
        data = json.dumps(value).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
