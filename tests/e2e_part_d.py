"""Tests reels de la partie D : lancer Maven clean verify avant ce script."""
from pathlib import Path
from decimal import Decimal
import json
import os
import subprocess
import tempfile
import time
import unittest
import urllib.parse
import uuid
from e2e_part_c import request, free_port

ROOT=Path(__file__).resolve().parents[1]

class PartD(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory(prefix="adria-part-d-")
        cls.processes=[];cls.logs=[]
        cls.addClassCleanup(cls.cleanup)
        cls.discovery,cls.config,cls.account,cls.transaction,cls.gateway=[free_port() for _ in range(5)]
        cls.start("discovery-service",cls.discovery,[
            "--eureka.server.use-read-only-response-cache=false"])
        config_process=cls.start("config-service",cls.config)
        _,cls.packaged_config=request(cls.config,"/transaction-service/default")
        cls.stop(config_process)
        repository=Path(cls.temp.name)/"config"
        repository.mkdir()
        (repository/"application.yml").write_text("bank:\n  transaction:\n    max-amount: 125\n",encoding="utf-8")
        cls.start("config-service",cls.config,[
            "--spring.cloud.config.server.native.search-locations="+repository.as_uri()+"/"])
        shared=["--spring.profiles.active=cloud",
            f"--eureka.client.service-url.defaultZone=http://127.0.0.1:{cls.discovery}/eureka/",
            "--eureka.client.registry-fetch-interval-seconds=1",
            "--eureka.instance.lease-renewal-interval-in-seconds=1",
            "--spring.cloud.loadbalancer.cache.ttl=1s"]
        cls.account_process=cls.start("account-service",cls.account,shared)
        cls.start("transaction-service",cls.transaction,shared+[
            "--bank.transaction.recovery.enabled=false"])
        cls.start("gateway-service",cls.gateway,shared)
        deadline=time.monotonic()+90
        while time.monotonic()<deadline:
            apps=cls.applications()
            if {"ACCOUNT-SERVICE","TRANSACTION-SERVICE","GATEWAY-SERVICE"}.issubset(apps):
                code,_=request(cls.gateway,"/api/accounts")
                if code==200:break
            time.sleep(1)
        else:raise AssertionError("Les services ne sont pas decouverts ou routables via Eureka.")

    @classmethod
    def start(cls,name,port,extra=()):
        jar=ROOT/name/"target"/f"{name}.jar"
        if not jar.exists():raise RuntimeError("Compiler d'abord avec Maven verify.")
        log=open(Path(cls.temp.name)/f"{name}-{len(cls.logs)}.log","w",encoding="utf-8")
        cls.logs.append(log)
        args=["java","-jar",str(jar),f"--server.port={port}"]
        if name in ("account-service","transaction-service"):
            db=(Path(cls.temp.name)/name).as_posix()
            args.append(f"--spring.datasource.url=jdbc:h2:file:{db};DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0")
        env=os.environ.copy()
        env.pop("ACCOUNT_SERVICE_URL",None)
        env.pop("BANK_TRANSACTION_MAX_AMOUNT",None)
        if name in ("account-service","transaction-service","gateway-service"):
            env["CONFIG_SERVER_URL"]=f"http://127.0.0.1:{cls.config}"
        process=subprocess.Popen([*args,*extra],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
        cls.processes.append(process)
        deadline=time.monotonic()+120
        while time.monotonic()<deadline:
            if process.poll() is not None:break
            try:
                code,body=request(port,"/actuator/health")
                if code==200 and body["status"]=="UP":return process
            except (OSError,ValueError):pass
            time.sleep(.4)
        log.flush()
        raise RuntimeError(Path(log.name).read_text(encoding="utf-8",errors="replace")[-16000:])

    @classmethod
    def stop(cls,process):
        if process.poll() is None:
            process.terminate()
            try:process.wait(timeout=15)
            except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)

    @classmethod
    def cleanup(cls):
        for process in reversed(cls.processes):cls.stop(process)
        for log in cls.logs:log.close()
        cls.temp.cleanup()

    @classmethod
    def applications(cls):
        _,body=request(cls.discovery,"/eureka/apps",headers={"Accept":"application/json"})
        apps=body.get("applications",{}).get("application",[])
        if isinstance(apps,dict):apps=[apps]
        return {app["name"]:app for app in apps}

    def create_account(self,balance="1000"):
        code,body=request(self.gateway,"/api/accounts",{
            "customerReference":"CLOUD","currency":"EUR","balance":balance})
        self.assertEqual(code,201,body)
        return body["id"]

    def command(self,source,amount="25",target=None):
        return {"requestId":str(uuid.uuid4()),"accountId":source,"targetAccountId":target,
            "amount":amount,"currency":"EUR","merchantCategory":"ECOMMERCE","country":"FR"}

    def test_01_packaged_shared_configuration_is_1000(self):
        sources=self.packaged_config["propertySources"]
        self.assertTrue(any(s["source"].get("bank.transaction.max-amount")==1000 for s in sources),sources)

    def test_02_eureka_registers_the_three_clients(self):
        apps=self.applications()
        for name in ("ACCOUNT-SERVICE","TRANSACTION-SERVICE","GATEWAY-SERVICE"):
            self.assertIn(name,apps)
            instances=apps[name]["instance"]
            if isinstance(instances,dict):instances=[instances]
            self.assertTrue(any(instance["status"]=="UP" for instance in instances))

    def test_03_gateway_routes_a_real_transfer_and_feign_uses_discovery(self):
        source,target=self.create_account(),self.create_account("0")
        code,body=request(self.gateway,"/api/transactions",self.command(source,"125",target))
        self.assertEqual(code,201,body);self.assertEqual(body["status"],"COMPLETED")
        _,account=request(self.gateway,f"/api/accounts/{source}")
        self.assertEqual(Decimal(str(account["balance"])),Decimal("875"))
        _,account=request(self.gateway,f"/api/accounts/{target}")
        self.assertEqual(Decimal(str(account["balance"])),Decimal("125"))
        _,page=request(self.gateway,f"/api/transactions?accountId={source}")
        self.assertEqual(page["content"][0]["id"],body["id"])

    def test_04_transaction_service_uses_the_remote_ceiling(self):
        _,config=request(self.config,"/transaction-service/cloud")
        self.assertTrue(any(s["source"].get("bank.transaction.max-amount")==125 for s in config["propertySources"]))
        source=self.create_account()
        code,body=request(self.gateway,"/api/transactions",self.command(source,"126"))
        self.assertEqual(code,422,body)
        _,account=request(self.gateway,f"/api/accounts/{source}")
        self.assertEqual(Decimal(str(account["balance"])),Decimal("1000"))

    def test_05_graphql_routes_are_separate_and_operational(self):
        source=self.create_account()
        query="query($id:ID!){account(id:$id){id currency}}"
        code,body=request(self.gateway,"/accounts/graphql",{"query":query,"variables":{"id":source}})
        self.assertEqual(code,200,body);self.assertNotIn("errors",body)
        command=self.command(source);command.pop("targetAccountId")
        query="mutation($input:SubmitTransactionInput!){createTransaction(input:$input){id status}}"
        code,body=request(self.gateway,"/transactions/graphql",{"query":query,"variables":{"input":command}})
        self.assertEqual(code,200,body);self.assertNotIn("errors",body)
        self.assertEqual(body["data"]["createTransaction"]["status"],"COMPLETED")

    def test_06_mcp_routes_support_discovery_and_execution(self):
        headers={"Accept":"application/json, text/event-stream","MCP-Protocol-Version":"2025-06-18"}
        for prefix in ("accounts","transactions"):
            code,body=request(self.gateway,f"/{prefix}/mcp",{
                "jsonrpc":"2.0","id":1,"method":"tools/list","params":{}},headers=headers)
            self.assertEqual(code,200,body);self.assertEqual(len(body["result"]["tools"]),4)
        source=self.create_account()
        args=self.command(source);args.pop("targetAccountId")
        code,body=request(self.gateway,"/transactions/mcp",{
            "jsonrpc":"2.0","id":2,"method":"tools/call",
            "params":{"name":"transaction_submit","arguments":args}},headers=headers)
        self.assertEqual(code,200,body);self.assertFalse(body["result"].get("isError",False),body)
        _,account=request(self.gateway,f"/api/accounts/{source}")
        self.assertEqual(Decimal(str(account["balance"])),Decimal("975"))

    def test_07_internal_routes_are_not_published_by_gateway(self):
        for path in ("/internal/movements","/accounts/internal/movements","/account-service/internal/movements"):
            code,body=request(self.gateway,path,{})
            self.assertEqual(code,404,(path,body))

    def test_08_missing_required_config_prevents_gateway_startup(self):
        log=open(Path(self.temp.name)/"missing-config.log","w",encoding="utf-8")
        type(self).logs.append(log)
        bad_port=free_port()
        process=subprocess.Popen(["java","-jar",str(ROOT/"gateway-service/target/gateway-service.jar"),
            f"--server.port={free_port()}",f"--spring.config.import=configserver:http://127.0.0.1:{bad_port}"],
            cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
        type(self).processes.append(process)
        process.wait(timeout=45)
        self.assertNotEqual(process.returncode,0)
        log.flush()
        self.assertIn("ConfigClientFailFastException",Path(log.name).read_text(encoding="utf-8",errors="replace"))

    def test_09_missing_registered_backend_returns_503(self):
        app=self.applications()["ACCOUNT-SERVICE"]
        instances=app["instance"]
        if isinstance(instances,dict):instances=[instances]
        self.stop(type(self).account_process)
        for instance in instances:
            path="/eureka/apps/ACCOUNT-SERVICE/"+urllib.parse.quote(instance["instanceId"],safe="")
            request(self.discovery,path,method="DELETE",headers={"Accept":"application/json"})
        deadline=time.monotonic()+35
        while time.monotonic()<deadline:
            code,body=request(self.gateway,"/api/accounts")
            if code==503:break
            time.sleep(1)
        self.assertEqual(code,503,body)

if __name__=="__main__":unittest.main(verbosity=2)
