"""Tests de la partie C sur deux vrais services Java : executer Maven verify avant ce script."""
from pathlib import Path
from decimal import Decimal
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import concurrent.futures
import json
import socket
import subprocess
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
HTTP = urllib.request.build_opener(urllib.request.ProxyHandler({}))

def request(port, path, body=None, method=None, headers=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f"http://127.0.0.1:{port}{path}", data=data,
            method=method, headers={"Content-Type": "application/json", **(headers or {})})
    try:
        response = HTTP.open(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read().decode()
        if raw.startswith("event:") or raw.startswith("data:"):
            raw = next(line[5:].strip() for line in raw.splitlines() if line.startswith("data:"))
        return response.status, json.loads(raw) if raw else None

def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]

class PartC(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="adria-part-c-")
        cls.processes, cls.handles = [], []
        cls.addClassCleanup(cls.cleanup)
        cls.account_port, cls.transaction_port = free_port(), free_port()
        cls.fail_next_movement = False
        cls.offline = False
        cls.movement_posts = 0
        cls.proxy_lock = threading.Lock()
        owner = cls
        class Proxy(BaseHTTPRequestHandler):
            def log_message(self, *args): pass
            def do_GET(self): self.forward()
            def do_POST(self): self.forward()
            def forward(self):
                length = int(self.headers.get("Content-Length", 0))
                data = self.rfile.read(length) if length else None
                with owner.proxy_lock:
                    offline = owner.offline
                    fail = self.command == "POST" and self.path == "/internal/movements" and owner.fail_next_movement
                    if fail: owner.fail_next_movement = False
                    if self.command == "POST" and self.path == "/internal/movements": owner.movement_posts += 1
                if offline:
                    self.send_response(503); self.end_headers(); return
                req = urllib.request.Request(f"http://127.0.0.1:{owner.account_port}{self.path}",data=data,
                        method=self.command,headers={"Content-Type":"application/json"})
                try:
                    result = HTTP.open(req,timeout=10)
                except urllib.error.HTTPError as error: result=error
                with result:
                    payload=result.read()
                    status=result.status
                # Le debit a ete commite, mais sa confirmation est perdue.
                if fail and status == 200: status,payload=503,b'{}'
                self.send_response(status)
                self.send_header("Content-Type","application/json")
                self.send_header("Content-Length",str(len(payload)))
                self.end_headers();self.wfile.write(payload)
        cls.proxy=ThreadingHTTPServer(("127.0.0.1",0),Proxy)
        threading.Thread(target=cls.proxy.serve_forever,daemon=True).start()
        cls.proxy_port=cls.proxy.server_port
        cls.start_service("account-service",cls.account_port)
        cls.start_transaction()

    @classmethod
    def start_service(cls,name,port,extra=()):
        jar=ROOT/name/"target"/f"{name}.jar"
        if not jar.exists(): raise RuntimeError("Compiler d'abord avec .\\mvnw.cmd verify")
        log=open(Path(cls.temp.name)/f"{name}-{len(cls.processes)}.log","w",encoding="utf-8")
        cls.handles.append(log)
        db=(Path(cls.temp.name)/name).as_posix()
        process=subprocess.Popen(["java","-jar",str(jar),f"--server.port={port}",
            f"--spring.datasource.url=jdbc:h2:file:{db};DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0",*extra],
            cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
        cls.processes.append(process)
        deadline=time.monotonic()+90
        while time.monotonic()<deadline:
            if process.poll() is not None: break
            try:
                code,body=request(port,"/actuator/health")
                if code==200 and body["status"]=="UP":return process
            except (OSError, ValueError): pass
            time.sleep(.3)
        log.flush()
        raise RuntimeError(Path(log.name).read_text(encoding="utf-8",errors="replace")[-12000:])

    @classmethod
    def start_transaction(cls,recovery=False):
        cls.tx=cls.start_service("transaction-service",cls.transaction_port,(
            f"--bank.account-service.url=http://127.0.0.1:{cls.proxy_port}",
            f"--bank.transaction.recovery.enabled={str(recovery).lower()}",
            "--bank.transaction.recovery.delay-ms=500"))

    @classmethod
    def stop_process(cls,process):
        if process.poll() is None:
            process.terminate()
            try:process.wait(timeout=15)
            except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)

    @classmethod
    def cleanup(cls):
        for process in reversed(cls.processes):cls.stop_process(process)
        if hasattr(cls,"proxy"):cls.proxy.shutdown();cls.proxy.server_close()
        for handle in cls.handles:handle.close()
        cls.temp.cleanup()

    def account(self,balance="1000",currency="EUR"):
        code,body=request(self.account_port,"/api/accounts",{
            "customerReference":"E2E","currency":currency,"balance":balance})
        self.assertEqual(code,201,body);return body["id"]

    def balance(self,id):
        code,body=request(self.account_port,f"/api/accounts/{id}")
        self.assertEqual(code,200,body);return Decimal(str(body["balance"]))

    def command(self,account,amount="50",target=None):
        return {"requestId":str(uuid.uuid4()),"accountId":account,"targetAccountId":target,
            "amount":amount,"currency":"EUR","merchantCategory":"ECOMMERCE","country":"FR"}

    def submit(self,command):return request(self.transaction_port,"/api/transactions",command)

    def rpc(self,name,args):
        code,body=request(self.transaction_port,"/mcp",{
            "jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":name,"arguments":args}},
            headers={"Accept":"application/json, text/event-stream","MCP-Protocol-Version":"2025-06-18"})
        self.assertEqual(code,200,body)
        self.assertFalse(body.get("result",{}).get("isError",False),body)
        return body

    def test_01_payment_and_idempotent_replay(self):
        source=self.account();command=self.command(source)
        first,body=self.submit(command);second,replay=self.submit(command)
        self.assertEqual((first,second),(201,201),(body,replay))
        self.assertEqual(body["id"],replay["id"])
        self.assertEqual(body["status"],"COMPLETED")
        self.assertEqual(self.balance(source),Decimal("950"))
        conflict=dict(command,amount="60")
        self.assertEqual(self.submit(conflict)[0],409)
        self.assertEqual(self.balance(source),Decimal("950"))

    def test_02_transfer_and_destination_history(self):
        source,target=self.account("100"),self.account("20")
        code,body=self.submit(self.command(source,"30",target))
        self.assertEqual(code,201,body)
        self.assertEqual((self.balance(source),self.balance(target)),(Decimal("70"),Decimal("50")))
        code,page=request(self.transaction_port,f"/api/transactions?accountId={target}")
        self.assertEqual(code,200,page);self.assertEqual(page["content"][0]["id"],body["id"])

    def test_03_business_rejections_leave_balances_unchanged(self):
        source=self.account("100")
        self.assertEqual(self.submit(self.command(source,"101"))[0],422)
        self.assertEqual(self.submit(self.command(source,"1001"))[0],422)
        self.assertEqual(self.submit(self.command(source,"0"))[0],400)
        self.assertEqual(self.submit(self.command(source,"-1"))[0],400)
        self.assertEqual(self.submit(self.command(source,"0.001"))[0],422)
        mismatch=dict(self.command(source),currency="MAD")
        self.assertEqual(self.submit(mismatch)[0],422)
        self.assertEqual(self.submit(self.command(source,"10",source))[0],422)
        self.assertEqual(self.submit(self.command(str(uuid.uuid4())))[0],422)
        target=self.account("20")
        request(self.account_port,f"/api/accounts/{target}/status",{"status":"BLOCKED"},method="PATCH")
        self.assertEqual(self.submit(self.command(source,"10",target))[0],422)
        self.assertEqual(self.balance(target),Decimal("20"))
        request(self.account_port,f"/api/accounts/{source}/status",{"status":"BLOCKED"},method="PATCH")
        self.assertEqual(self.submit(self.command(source,"10"))[0],422)
        self.assertEqual(self.balance(source),Decimal("100"))

    def test_04_graphql_reuses_the_same_transaction_service(self):
        source=self.account("100")
        command=self.command(source,"10");command.pop("targetAccountId")
        query="mutation($input:SubmitTransactionInput!){createTransaction(input:$input){id status amount createdAt}}"
        code,body=request(self.transaction_port,"/graphql",{"query":query,"variables":{"input":command}})
        self.assertEqual(code,200,body);self.assertNotIn("errors",body)
        self.assertEqual(body["data"]["createTransaction"]["status"],"COMPLETED")
        id=body["data"]["createTransaction"]["id"]
        query="query($id:ID!){transaction(id:$id){id status}}"
        _,body=request(self.transaction_port,"/graphql",{"query":query,"variables":{"id":id}})
        self.assertNotIn("errors",body);self.assertEqual(body["data"]["transaction"]["id"],id)
        self.assertEqual(self.balance(source),Decimal("90"))

    def test_05_mcp_executes_and_lists_transactions(self):
        source=self.account("100");command=self.command(source,"10");command.pop("targetAccountId")
        self.rpc("transaction_submit",command)
        self.assertEqual(self.balance(source),Decimal("90"))
        result=self.rpc("transaction_list",{"accountId":source,"page":0,"size":20})
        text=result["result"]["content"][0]["text"]
        page=json.loads(text)
        self.rpc("transaction_get",{"id":page["content"][0]["id"]})
        code,body=request(self.transaction_port,"/mcp",{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}},
            headers={"Accept":"application/json, text/event-stream","MCP-Protocol-Version":"2025-06-18"})
        self.assertEqual(code,200,body);self.assertEqual(len(body["result"]["tools"]),4)

    def test_06_concurrent_orders_never_overdraw(self):
        source=self.account("100")
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            results=list(executor.map(self.submit,[self.command(source,"80"),self.command(source,"80")]))
        self.assertEqual(sorted(code for code,_ in results),[201,422],results)
        self.assertEqual(self.balance(source),Decimal("20"))

    def test_07_lost_confirmation_is_recovered_after_restart(self):
        source=self.account("100");command=self.command(source,"30")
        with self.proxy_lock:
            type(self).fail_next_movement=True
            before=type(self).movement_posts
        code,body=self.submit(command)
        self.assertEqual(code,202,body);self.assertEqual(body["status"],"PENDING")
        self.assertEqual(self.balance(source),Decimal("70"))
        self.stop_process(type(self).tx)
        type(self).start_transaction(recovery=True)
        deadline=time.monotonic()+25
        while time.monotonic()<deadline:
            _,current=request(self.transaction_port,f"/api/transactions/{body['id']}")
            if current["status"]=="COMPLETED":break
            time.sleep(.3)
        self.assertEqual(current["status"],"COMPLETED",current)
        self.assertEqual(self.balance(source),Decimal("70"))
        self.assertEqual(type(self).movement_posts-before,1)
        type(self).stop_process(type(self).tx)
        type(self).start_transaction(recovery=False)

    def test_08_service_outage_keeps_a_durable_pending_order(self):
        source=self.account("100")
        type(self).offline=True
        try:code,body=self.submit(self.command(source,"25"))
        finally:type(self).offline=False
        self.assertEqual(code,202,body);self.assertEqual(body["status"],"PENDING")
        self.assertEqual(self.balance(source),Decimal("100"))
        code,retried=request(self.transaction_port,f"/api/transactions/{body['id']}/retry",{})
        self.assertEqual(code,200,retried);self.assertEqual(retried["status"],"COMPLETED")
        self.assertEqual(self.balance(source),Decimal("75"))

    def test_09_concurrent_replays_share_one_transaction(self):
        source=self.account("100");command=self.command(source,"10")
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
            results=list(executor.map(self.submit,[command]*4))
        self.assertEqual([code for code,_ in results],[201]*4,results)
        self.assertEqual(len({body["id"] for _,body in results}),1)
        self.assertEqual(self.balance(source),Decimal("90"))

if __name__=="__main__":unittest.main(verbosity=2)
