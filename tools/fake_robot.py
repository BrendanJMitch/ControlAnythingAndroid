#!/usr/bin/env python3
"""Simulates an ESP32 robot for testing the ControlAnything Android app.

Mirrors the embedded library's WiFiTransport: hosts a WebSocket server, advertises
it over mDNS as `_controlanything._tcp`, and exchanges `topic:value` text frames.
On every new connection it sends the retained topics (always `info`, plus the
outputs with --retain-outputs), then streams simulated output values, emits the
occasional `log/*` message, and logs whatever the app publishes to controls/*.

Usage:
    pip install websockets zeroconf
    python3 fake_robot.py [--port PORT] [--path PATH] [--interval SECONDS]
                          [--advertise-ip IP] [--retain-outputs]

Run it on a machine on the same network as the phone. --advertise-ip picks the
address announced over mDNS when the auto-detected one is wrong (e.g. with VPNs
or multiple network adapters).
"""

import argparse
import asyncio
import json
import random
import socket
import time

from websockets.asyncio.server import broadcast, serve
from zeroconf import IPVersion, ServiceInfo
from zeroconf.asyncio import AsyncZeroconf

SERVICE_TYPE = "_controlanything._tcp.local."
DEFAULT_PORT = 81
DEFAULT_PATH = "/"

INFO_TOPIC = "info"
CONTROLS_PREFIX = "controls/"
OUTPUTS_PREFIX = "outputs/"

INFO_PAYLOAD = {
    "device_id": "esp32-fake-01",
    "device_name": "Fake Rover",
    "project_id": "fake_rover_demo",
    "schema_hash": "demo1",
    "controls": [
        {
            "topic": ["lights"],
            "display_name": "Lights",
            "type": "bool",
            "widget": {"type": "toggle", "default_value": True},
        },
        {
            "topic": ["horn"],
            "display_name": "Horn",
            "type": "bool",
            "widget": {"type": "button", "mode": "rising"},
        },
        {
            "topic": ["speed"],
            "display_name": "Speed",
            "type": "float",
            "widget": {"type": "slider", "min": -1.0, "max": 4.0, "default_value": 1.5},
        },
        {
            "topic": ["pitch"],
            "display_name": "Pitch",
            "type": "float",
            "widget": {
                "type": "slider",
                "min": 0.0,
                "max": 180.0,
                "default_value": 90.0,
                "orientation": "vertical",
            },
        },
        {
            "topic": ["tilt"],
            "display_name": "Tilt",
            "type": "float",
            "widget": {
                "type": "slider",
                "min": 0.0,
                "max": 180.0,
                "default_value": 90.0,
                "orientation": "vertical",
            },
        },
        {
            "topic": ["drive_x", "drive_y"],
            "display_name": "Drive",
            "type": "float",
            "widget": {"type": "joystick"},
        },
    ],
    "outputs": [
        {
            "topic": ["battery_voltage"],
            "display_name": "Battery",
            "type": "float",
            "widget": {"type": "numeric_readout", "suffix": "V"},
        },
        {
            "topic": ["status_led"],
            "display_name": "Status",
            "type": "bool",
            "widget": {"type": "led_indicator", "color": "cyan"},
        },
    ],
}


class FakeRobot:
    def __init__(self, path, retain_outputs):
        self.path = path
        self.retain_outputs = retain_outputs
        self.clients = set()
        # Insertion-ordered, like the embedded library's retained list: info first.
        self.retained = {INFO_TOPIC: json.dumps(INFO_PAYLOAD)}

    def publish(self, topic, value, retained=False):
        if retained:
            self.retained[topic] = value
        broadcast(self.clients, f"{topic}:{value}")

    async def handle(self, websocket):
        if websocket.request.path != self.path:
            print(f"Rejected connection on unexpected path {websocket.request.path!r}")
            await websocket.close(code=1008, reason="wrong path")
            return

        peer = websocket.remote_address
        print(f"Client connected: {peer}")
        self.clients.add(websocket)
        try:
            for topic, value in self.retained.items():
                await websocket.send(f"{topic}:{value}")
            async for frame in websocket:
                self.on_frame(frame)
        finally:
            self.clients.discard(websocket)
            print(f"Client disconnected: {peer}")

    @staticmethod
    def on_frame(frame):
        if isinstance(frame, bytes):
            print(f"<- (ignored binary frame, {len(frame)} bytes)")
            return
        topic, sep, value = frame.partition(":")
        if not sep or not topic:
            print(f"<- (malformed) {frame!r}")
        elif topic.startswith(CONTROLS_PREFIX):
            print(f"<- {topic}: {value}")
        else:
            print(f"<- (unexpected topic) {topic}: {value}")

    async def stream_outputs(self, interval):
        start = time.monotonic()
        battery_voltage = 12.6
        tick = 0
        while True:
            elapsed = time.monotonic() - start

            # Battery slowly drains with a little noise, resets once it gets low.
            battery_voltage -= random.uniform(0.0, 0.01)
            battery_voltage += random.uniform(-0.02, 0.02)
            if battery_voltage < 10.5:
                battery_voltage = 12.6

            # Status LED blinks on a 2-second cycle.
            status_on = int(elapsed) % 2 == 0

            self.publish(f"{OUTPUTS_PREFIX}battery_voltage", f"{battery_voltage:.2f}", self.retain_outputs)
            self.publish(f"{OUTPUTS_PREFIX}status_led", "true" if status_on else "false", self.retain_outputs)

            # The real library publishes logs on log/<level>; the app should ignore them for now.
            if tick % 10 == 0:
                self.publish("log/info", f"uptime {int(elapsed)}s")
            tick += 1

            if self.clients:
                print(f"-> battery_voltage={battery_voltage:.2f} status_led={status_on}")
            await asyncio.sleep(interval)


def detect_local_ip():
    """The address of the interface that would route off-host (no packet is actually sent)."""
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        try:
            s.connect(("10.255.255.255", 1))
            return s.getsockname()[0]
        except OSError:
            return "127.0.0.1"


async def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--path", default=DEFAULT_PATH, help="WebSocket path; advertised via the 'path' TXT entry")
    parser.add_argument("--interval", type=float, default=1.0, help="seconds between output updates")
    parser.add_argument("--advertise-ip", default=None, help="IPv4 address to announce over mDNS")
    parser.add_argument("--retain-outputs", action="store_true", help="also resend last outputs on connect")
    args = parser.parse_args()

    robot = FakeRobot(args.path, args.retain_outputs)
    ip = args.advertise_ip or detect_local_ip()
    service = ServiceInfo(
        SERVICE_TYPE,
        f"{INFO_PAYLOAD['device_name']}.{SERVICE_TYPE}",
        addresses=[socket.inet_aton(ip)],
        port=args.port,
        properties={"path": args.path} if args.path != DEFAULT_PATH else {},
        # A dedicated host name, not this machine's: that one already answers with every
        # adapter's address (VPN, VM, link-local), and the app would take the first IPv4 it sees.
        server="fake-rover.local.",
    )

    # ping_interval=None: the device doesn't ping; the app does, and pongs are automatic.
    async with serve(robot.handle, "0.0.0.0", args.port, ping_interval=None):
        zeroconf = AsyncZeroconf(ip_version=IPVersion.V4Only)
        await zeroconf.async_register_service(service)
        print(f"Serving ws://{ip}:{args.port}{args.path} and advertising {SERVICE_TYPE}")
        try:
            await robot.stream_outputs(args.interval)
        finally:
            await zeroconf.async_unregister_service(service)
            await zeroconf.async_close()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nShutting down")
