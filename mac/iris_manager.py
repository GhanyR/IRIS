#!/usr/bin/env python3
"""Local-only Mac dashboard for managing an attached IRIS Motorola."""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import threading
import time
import webbrowser
from dataclasses import dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Iterable


PACKAGE = "app.nophoneinbed"
ACTIVITY = f"{PACKAGE}/.MainActivity"
SERVICE = f"{PACKAGE}.runtime.TrackerForegroundService"
DEFAULT_DEVICE = "10.20.10.50:5555"
ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
SOAK_SCRIPT = ROOT / "tools/iris_soak_monitor.sh"
ACTION_MAP = {
    "start": f"{PACKAGE}.action.MANAGER_START",
    "stop": f"{PACKAGE}.action.MANAGER_STOP",
    "test_alarm": f"{PACKAGE}.action.MANAGER_TEST_ALARM",
}


@dataclass(frozen=True)
class AdbDevice:
    serial: str
    state: str
    details: str = ""


def parse_adb_devices(output: str) -> list[AdbDevice]:
    devices: list[AdbDevice] = []
    for line in output.splitlines():
        fields = line.strip().split(maxsplit=2)
        if len(fields) >= 2 and fields[0] != "List":
            devices.append(AdbDevice(fields[0], fields[1], fields[2] if len(fields) > 2 else ""))
    return devices


def choose_serial(devices: Iterable[AdbDevice]) -> str | None:
    ready = [device.serial for device in devices if device.state == "device"]
    return next((serial for serial in ready if ":" in serial), ready[0] if ready else None)


def parse_lock_state(output: str) -> bool | None:
    match = re.search(r"\bdeviceLocked=(\d)", output)
    return match.group(1) == "1" if match else None


def parse_battery(output: str) -> dict[str, Any]:
    def value(label: str) -> str | None:
        match = re.search(rf"^\s*{re.escape(label)}:\s*(.+)$", output, re.MULTILINE)
        return match.group(1).strip() if match else None

    level = value("level")
    temperature = value("temperature")
    power = "None"
    for label, name in (("AC powered", "AC"), ("USB powered", "USB"), ("Wireless powered", "Wireless")):
        if value(label) == "true":
            power = name
            break
    return {
        "level": int(level) if level and level.isdigit() else None,
        "temperature_c": round(int(temperature) / 10, 1) if temperature and temperature.isdigit() else None,
        "power": power,
    }


def parse_iris_log(line: str) -> dict[str, Any]:
    aliases = {
        "state": "state",
        "detections": "detections",
        "confidence": "confidence",
        "bestConfidence": "confidence",
        "bestKind": "kind",
        "bestOverlap": "overlap",
        "inference_ms": "inference_ms",
        "inferenceMs": "inference_ms",
        "fps": "fps",
        "thermal": "thermal",
    }
    result: dict[str, Any] = {}
    for key, raw in re.findall(r"([A-Za-z_]+)=([^\s]+)", line):
        target = aliases.get(key)
        if not target:
            continue
        if target in {"detections", "inference_ms", "thermal"}:
            try:
                result[target] = int(float(raw))
            except ValueError:
                pass
        elif target in {"confidence", "overlap", "fps"}:
            try:
                result[target] = round(float(raw), 3)
            except ValueError:
                pass
        else:
            result[target] = raw
    return result


def parse_service_active(output: str) -> bool:
    return SERVICE in output or f"{PACKAGE}/.runtime.TrackerForegroundService" in output


class CommandRunner:
    def run(self, args: list[str], timeout: float = 8) -> subprocess.CompletedProcess[str]:
        try:
            return subprocess.run(args, capture_output=True, text=True, timeout=timeout, check=False)
        except (OSError, subprocess.TimeoutExpired) as error:
            return subprocess.CompletedProcess(args, 127, "", str(error))


class IrisController:
    def __init__(self, runner: CommandRunner | None = None) -> None:
        self.runner = runner or CommandRunner()
        self.adb = shutil.which("adb") or "/opt/homebrew/bin/adb"
        self.scrcpy = shutil.which("scrcpy") or "/opt/homebrew/bin/scrcpy"
        self._last_action = "Belum ada aksi"
        self._soak_process: subprocess.Popen[str] | None = None
        self._soak_log: Path | None = None
        self._lock = threading.Lock()

    def _run(self, args: list[str], timeout: float = 8) -> subprocess.CompletedProcess[str]:
        return self.runner.run(args, timeout)

    def _adb(self, serial: str, *args: str, timeout: float = 8) -> subprocess.CompletedProcess[str]:
        return self._run([self.adb, "-s", serial, *args], timeout)

    def devices(self) -> list[AdbDevice]:
        return parse_adb_devices(self._run([self.adb, "devices", "-l"]).stdout)

    def serial(self) -> str | None:
        return choose_serial(self.devices())

    def _preference_exists(self, serial: str, filename: str, needle: str) -> bool:
        result = self._adb(serial, "shell", "run-as", PACKAGE, "cat", f"shared_prefs/{filename}")
        return result.returncode == 0 and needle in result.stdout

    def status(self) -> dict[str, Any]:
        serial = self.serial()
        base: dict[str, Any] = {
            "connected": bool(serial),
            "serial": serial,
            "adb_available": Path(self.adb).exists(),
            "scrcpy_available": Path(self.scrcpy).exists(),
            "last_action": self._last_action,
            "apk_available": APK.exists(),
            "privacy": "Inference offline; app tidak punya izin INTERNET; frame tidak di-upload/disimpan",
        }
        if not serial:
            base.update({"service_active": False, "calibrated": False, "armed": False})
            return base

        model = self._adb(serial, "shell", "getprop", "ro.product.model").stdout.strip()
        lock_dump = self._adb(serial, "shell", "dumpsys", "trust").stdout
        battery = parse_battery(self._adb(serial, "shell", "dumpsys", "battery").stdout)
        service_dump = self._adb(serial, "shell", "dumpsys", "activity", "services", PACKAGE).stdout
        package_dump = self._adb(serial, "shell", "dumpsys", "package", PACKAGE).stdout
        version_match = re.search(r"versionName=([^\s]+)", package_dump)
        log_lines = self._adb(serial, "logcat", "-d", "-v", "time", "-s", "IRIS:I", "*:S").stdout.splitlines()
        latest_log = next((line for line in reversed(log_lines) if "state=" in line), "")
        base.update(
            {
                "model": model or "Android",
                "locked": parse_lock_state(lock_dump),
                "battery": battery,
                "service_active": parse_service_active(service_dump),
                "version": version_match.group(1) if version_match else None,
                "calibrated": self._preference_exists(serial, "bed_calibration.xml", "calibration_v1"),
                "armed": self._preference_exists(serial, "tracking_state.xml", 'value="true"'),
                "runtime": parse_iris_log(latest_log),
                "latest_log": latest_log[-400:] if latest_log else "Belum ada log runtime IRIS",
            }
        )
        with self._lock:
            base["soak_running"] = self._soak_process is not None and self._soak_process.poll() is None
            base["soak_log"] = str(self._soak_log) if self._soak_log else None
        return base

    def _require_device(self) -> str:
        serial = self.serial()
        if not serial:
            raise RuntimeError("Motorola belum tersambung. Hubungkan USB sekali atau klik Connect Wi-Fi.")
        return serial

    def connect(self) -> str:
        devices = self.devices()
        wireless = next((d.serial for d in devices if d.state == "device" and ":" in d.serial), None)
        if wireless:
            return f"Wi-Fi ADB sudah aktif: {wireless}"
        usb = next((d.serial for d in devices if d.state == "device" and ":" not in d.serial), None)
        if usb:
            route = self._adb(usb, "shell", "ip", "route").stdout
            match = re.search(r"\bsrc\s+(\d+\.\d+\.\d+\.\d+)", route)
            if match:
                target = f"{match.group(1)}:5555"
                self._adb(usb, "tcpip", "5555")
                time.sleep(2)
                result = self._run([self.adb, "connect", target], timeout=12)
                if result.returncode == 0 and "failed" not in result.stdout.lower():
                    return f"Wi-Fi ADB tersambung: {target}"
        target = os.environ.get("IRIS_DEVICE", DEFAULT_DEVICE)
        result = self._run([self.adb, "connect", target], timeout=12)
        if result.returncode != 0 or "failed" in (result.stdout + result.stderr).lower():
            raise RuntimeError(f"Tidak bisa connect ke {target}. Pastikan Mac dan Motorola di Wi-Fi yang sama.")
        return f"Wi-Fi ADB tersambung: {target}"

    def activity_action(self, action: str) -> str:
        serial = self._require_device()
        command = ["shell", "am", "start", "-n", ACTIVITY]
        if action == "open":
            label = "IRIS dibuka"
        elif action in ACTION_MAP:
            command += ["-a", ACTION_MAP[action]]
            label = {
                "start": "Perintah mulai dikirim",
                "stop": "Perintah stop dikirim",
                "test_alarm": "Tes alarm 2 detik dikirim",
            }[action]
        else:
            raise ValueError("Aksi Android tidak dikenal")
        result = self._adb(serial, *command, timeout=12)
        if result.returncode != 0 or "Error:" in result.stdout:
            raise RuntimeError((result.stdout + result.stderr).strip() or "Android menolak perintah")
        return f"{label} di {serial}"

    def live_view(self) -> str:
        serial = self._require_device()
        if not Path(self.scrcpy).exists():
            raise RuntimeError("scrcpy belum terpasang. Jalankan: brew install scrcpy")
        self.activity_action("open")
        subprocess.Popen(
            [
                self.scrcpy,
                f"--serial={serial}",
                "--no-audio",
                "--stay-awake",
                "--window-title=IRIS — Live View",
            ],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            start_new_session=True,
        )
        return f"Kalibrasi / Live View dibuka untuk {serial}; klik 4 sudut lalu drag titik dengan mouse"

    def install(self) -> str:
        serial = self._require_device()
        if not APK.exists():
            raise RuntimeError("APK belum dibangun. Jalankan build terlebih dulu.")
        result = self._adb(serial, "install", "-r", str(APK), timeout=180)
        if result.returncode != 0 or "Success" not in result.stdout:
            raise RuntimeError((result.stdout + result.stderr).strip() or "Install gagal")
        return f"APK terbaru terpasang di {serial}"

    def start_soak(self, duration: int) -> str:
        serial = self._require_device()
        duration = max(60, min(duration, 86_400))
        with self._lock:
            if self._soak_process is not None and self._soak_process.poll() is None:
                raise RuntimeError("Soak test lain masih berjalan")
            stamp = time.strftime("%Y%m%d-%H%M%S")
            self._soak_log = Path(f"/tmp/iris-soak-{stamp}.tsv")
            console_log = Path(f"/tmp/iris-soak-{stamp}.log")
            environment = os.environ.copy()
            environment.update(
                {
                    "IRIS_ADB_SERIAL": serial,
                    "IRIS_SOAK_SECONDS": str(duration),
                    "IRIS_SOAK_OUTPUT": str(self._soak_log),
                }
            )
            stream = console_log.open("w", encoding="utf-8")
            self._soak_process = subprocess.Popen(
                [str(SOAK_SCRIPT)],
                cwd=ROOT,
                env=environment,
                stdout=stream,
                stderr=subprocess.STDOUT,
                text=True,
                start_new_session=True,
            )
            stream.close()
        return f"Soak test {duration // 60} menit dimulai; hasil: {self._soak_log}"

    def act(self, action: str, payload: dict[str, Any]) -> dict[str, Any]:
        try:
            if action == "connect":
                message = self.connect()
            elif action in {"open", "start", "stop", "test_alarm"}:
                message = self.activity_action(action)
            elif action == "live":
                message = self.live_view()
            elif action == "install":
                message = self.install()
            elif action == "soak":
                message = self.start_soak(int(payload.get("seconds", 600)))
            else:
                raise ValueError("Aksi tidak diizinkan")
            self._last_action = message
            return {"ok": True, "message": message}
        except (RuntimeError, ValueError) as error:
            self._last_action = str(error)
            return {"ok": False, "message": str(error)}


HTML = r"""<!doctype html>
<html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="icon" href="data:,">
<title>IRIS Manager</title>
<style>
:root{color-scheme:dark;--bg:#07100f;--panel:#101b19;--line:#263633;--ink:#f2f7f5;--muted:#98aaa6;--green:#54e1a6;--amber:#ffc766;--red:#ff756f;--blue:#7fc6ff}*{box-sizing:border-box}body{margin:0;background:radial-gradient(circle at 80% 0,#17302a 0,transparent 32%),var(--bg);font:15px -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color:var(--ink)}main{max-width:1180px;margin:auto;padding:34px 24px 60px}.top{display:flex;justify-content:space-between;gap:20px;align-items:flex-start;margin-bottom:24px}h1{font-size:36px;letter-spacing:.04em;margin:0}.sub{color:var(--muted);margin-top:6px}.chips{display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end}.chip{border:1px solid var(--line);background:#112420;padding:7px 10px;border-radius:99px;font-size:12px}.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.card{background:linear-gradient(145deg,#12201e,#0d1715);border:1px solid var(--line);border-radius:16px;padding:17px;min-height:126px}.label{font-size:12px;color:var(--muted);text-transform:uppercase;letter-spacing:.08em}.value{font-size:22px;font-weight:700;margin-top:9px}.detail{color:var(--muted);font-size:13px;margin-top:7px;line-height:1.4}.good{color:var(--green)}.warn{color:var(--amber)}.bad{color:var(--red)}section{margin-top:22px}.panel{background:#0c1615;border:1px solid var(--line);border-radius:18px;padding:20px}h2{font-size:18px;margin:0 0 14px}.actions{display:flex;gap:10px;flex-wrap:wrap}button{appearance:none;border:1px solid #344742;background:#162522;color:var(--ink);border-radius:11px;padding:11px 15px;font-weight:650;cursor:pointer}button.primary{background:var(--green);color:#04110d;border-color:var(--green)}button.danger{border-color:#74413e;color:#ffaaa6}button:hover{filter:brightness(1.14)}button:disabled{opacity:.4;cursor:not-allowed}.split{display:grid;grid-template-columns:1.15fr .85fr;gap:16px;margin-top:18px}.checklist{display:grid;gap:10px}.step{display:flex;gap:10px;line-height:1.45;color:#d6e1de}.num{min-width:25px;height:25px;border:1px solid #3c5550;border-radius:50%;display:grid;place-items:center;color:var(--green);font-size:12px}pre{white-space:pre-wrap;word-break:break-word;background:#07100f;border-radius:10px;padding:13px;color:#b8c9c5;min-height:88px;margin:0;font:12px ui-monospace,monospace}.truth{border-left:3px solid var(--amber);padding-left:12px;color:#d8e2df;line-height:1.5}@media(max-width:850px){.grid{grid-template-columns:repeat(2,1fr)}.split{grid-template-columns:1fr}.top{display:block}.chips{justify-content:flex-start;margin-top:14px}}@media(max-width:520px){.grid{grid-template-columns:1fr}}
</style></head><body><main>
<div class="top"><div><h1>IRIS MANAGER</h1><div class="sub">In-bed Recognition & Intervention System · Mac control center</div></div><div class="chips"><span class="chip">OFFLINE AI</span><span class="chip">NO FRAME UPLOAD</span><span class="chip" id="updated">memuat…</span></div></div>
<div class="grid">
 <div class="card"><div class="label">Motorola</div><div class="value" id="device">Memuat…</div><div class="detail" id="connection"></div></div>
 <div class="card"><div class="label">Monitoring</div><div class="value" id="monitoring">—</div><div class="detail" id="calibration"></div></div>
 <div class="card"><div class="label">Vision state</div><div class="value" id="vision">—</div><div class="detail" id="visionDetail"></div></div>
 <div class="card"><div class="label">Power & thermal</div><div class="value" id="power">—</div><div class="detail" id="temperature"></div></div>
</div>
<section class="panel"><h2>Kontrol</h2><div class="actions">
 <button class="primary" onclick="act('live')">Kalibrasi / Live View</button><button onclick="act('open')">Buka Setup</button><button onclick="act('connect')">Connect Wi-Fi</button><button onclick="act('start')">Mulai Monitoring</button><button class="danger" onclick="act('stop')">Stop</button><button onclick="act('test_alarm')">Tes Alarm 2 dtk</button><button onclick="act('install')">Install APK Terbaru</button><button onclick="act('soak',{seconds:600})">Soak 10 Menit</button><button onclick="act('soak',{seconds:1800})">Soak 30 Menit</button>
</div></section>
<div class="split"><section class="panel"><h2>Tes fisik yang wajib selesai</h2><div class="checklist">
 <div class="step"><span class="num">1</span><span>Unlock Motorola, buka Live View, dan arahkan kamera sampai seluruh kasur terlihat.</span></div>
 <div class="step"><span class="num">2</span><span>Klik 4 sudut kasur dari Live View, lalu drag setiap titik dengan mouse sampai garis cyan pas di tepi kasur.</span></div>
 <div class="step"><span class="num">3</span><span>Taruh satu HP: IRIS harus menjadi ALARM dan bunyi.</span></div>
 <div class="step"><span class="num">4</span><span>Keluarkan HP: tunggu maksimal sekitar 4 detik; harus CLEAR dan diam.</span></div>
 <div class="step"><span class="num">5</span><span>Masukkan ulang dengan sudut/case berbeda: harus ALARM lagi. Lalu jalankan soak test.</span></div>
 <div class="step"><span class="num">6</span><span>Sambungkan Motorola ke charger dinding untuk operasi 24 jam.</span></div>
 </div></section><section class="panel"><h2>Diagnosis</h2><pre id="log">Menunggu status…</pre><div class="truth" style="margin-top:14px">Tidak ada computer vision yang 100% untuk HP yang tertutup total, di luar frame, terlalu kecil, atau ruangan gelap total. IRIS memilih FAULT/WATCH saat tidak yakin, bukan memberi CLEAR palsu.</div></section></div>
</main><script>
const $=id=>document.getElementById(id); const cls=(el,name)=>{el.className='value '+name};
async function refresh(){try{const s=await fetch('/api/status',{cache:'no-store'}).then(r=>r.json());$('updated').textContent='update '+new Date().toLocaleTimeString();$('device').textContent=s.connected?(s.model||'Android'):'DISCONNECTED';cls($('device'),s.connected?'good':'bad');$('connection').textContent=s.connected?`${s.serial} · ${s.locked?'LOCKED':'unlocked'} · IRIS ${s.version||'?'}`:'Klik Connect Wi-Fi atau pasang USB';$('monitoring').textContent=s.service_active?'ACTIVE':'STOPPED';cls($('monitoring'),s.service_active?'good':'bad');$('calibration').textContent=`${s.calibrated?'calibrated':'belum kalibrasi'} · ${s.armed?'armed':'not armed'}${s.soak_running?' · soak running':''}`;const r=s.runtime||{};$('vision').textContent=r.state||'NO DATA';cls($('vision'),r.state==='ALARM'?'bad':r.state==='CLEAR'?'good':r.state?'warn':'');$('visionDetail').textContent=r.state?`${r.detections??0} objek · conf ${r.confidence??0} · ${r.inference_ms??0} ms · ${r.fps??0} FPS`:'Start monitoring untuk data live';const b=s.battery||{};$('power').textContent=b.level==null?'—':`${b.level}% · ${b.power}`;cls($('power'),b.power==='None'?'warn':'good');$('temperature').textContent=b.temperature_c==null?'':`${b.temperature_c}°C${s.locked?' · HP terkunci':''}`;$('log').textContent=`${s.last_action}\n\n${s.latest_log||s.privacy}`;}catch(e){$('log').textContent='Manager error: '+e.message;}}
async function act(name,payload={}){$('log').textContent='Menjalankan '+name+'…';try{const r=await fetch('/api/action/'+name,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)}).then(r=>r.json());$('log').textContent=(r.ok?'✓ ':'✕ ')+r.message;}catch(e){$('log').textContent='✕ '+e.message;}setTimeout(refresh,700)}refresh();setInterval(refresh,3000);
</script></body></html>"""


class IrisRequestHandler(BaseHTTPRequestHandler):
    controller: IrisController

    def _send_json(self, payload: dict[str, Any], status: HTTPStatus = HTTPStatus.OK) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        if self.path == "/":
            body = HTML.encode("utf-8")
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif self.path == "/api/status":
            self._send_json(self.controller.status())
        else:
            self._send_json({"ok": False, "message": "Not found"}, HTTPStatus.NOT_FOUND)

    def do_POST(self) -> None:
        if not self.path.startswith("/api/action/"):
            self._send_json({"ok": False, "message": "Not found"}, HTTPStatus.NOT_FOUND)
            return
        try:
            length = min(int(self.headers.get("Content-Length", "0")), 4096)
            payload = json.loads(self.rfile.read(length) or b"{}")
        except (ValueError, json.JSONDecodeError):
            self._send_json({"ok": False, "message": "JSON tidak valid"}, HTTPStatus.BAD_REQUEST)
            return
        result = self.controller.act(self.path.rsplit("/", 1)[-1], payload)
        self._send_json(result, HTTPStatus.OK if result["ok"] else HTTPStatus.BAD_REQUEST)

    def log_message(self, format: str, *args: Any) -> None:
        return


def serve(port: int, open_browser: bool) -> None:
    IrisRequestHandler.controller = IrisController()
    server = ThreadingHTTPServer(("127.0.0.1", port), IrisRequestHandler)
    url = f"http://127.0.0.1:{server.server_port}"
    print(f"IRIS Manager aktif di {url}", flush=True)
    print("Tutup jendela Terminal ini untuk menghentikan dashboard (monitoring HP tetap berjalan).", flush=True)
    if open_browser:
        threading.Timer(0.4, lambda: webbrowser.open(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


def main() -> None:
    parser = argparse.ArgumentParser(description="IRIS local Mac manager")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--no-browser", action="store_true")
    args = parser.parse_args()
    serve(args.port, not args.no_browser)


if __name__ == "__main__":
    main()
