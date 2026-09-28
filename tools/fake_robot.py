#!/usr/bin/env python3
"""Simulates an ESP32 robot for testing the ControlAnything Android app.

Publishes the retained `info` schema with one control/output of every widget
type, streams simulated values to the outputs, and logs whatever the app
publishes to controls/* (nothing yet, until the app's controls become
interactive).

Usage:
    pip install paho-mqtt
    python3 fake_robot.py [--host HOST] [--port PORT] [--interval SECONDS]
"""

import argparse
import json
import random
import time

import paho.mqtt.client as mqtt

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


def on_connect(client, userdata, flags, reason_code, properties=None):
    print(f"Connected (reason_code={reason_code})")
    client.publish(INFO_TOPIC, json.dumps(INFO_PAYLOAD), qos=0, retain=True)
    client.subscribe(f"{CONTROLS_PREFIX}#")
    print(f"Published retained '{INFO_TOPIC}' and subscribed to '{CONTROLS_PREFIX}#'")


def on_message(client, userdata, message):
    payload = message.payload.decode("utf-8", errors="replace")
    print(f"<- {message.topic}: {payload}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=1883)
    parser.add_argument(
        "--interval", type=float, default=1.0, help="seconds between output updates"
    )
    args = parser.parse_args()

    client = mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2, client_id="fake-robot"
    )
    client.on_connect = on_connect
    client.on_message = on_message
    client.connect(args.host, args.port)
    client.loop_start()

    start = time.monotonic()
    battery_voltage = 12.6
    try:
        while True:
            elapsed = time.monotonic() - start

            # Battery slowly drains with a little noise, resets once it gets low.
            battery_voltage -= random.uniform(0.0, 0.01)
            battery_voltage += random.uniform(-0.02, 0.02)
            if battery_voltage < 10.5:
                battery_voltage = 12.6
            client.publish(
                f"{OUTPUTS_PREFIX}battery_voltage",
                f"{battery_voltage:.2f}",
                retain=True,
            )

            # Status LED blinks on a 2-second cycle.
            status_on = int(elapsed) % 2 == 0
            client.publish(
                f"{OUTPUTS_PREFIX}status_led",
                "true" if status_on else "false",
                retain=True,
            )

            print(f"-> battery_voltage={battery_voltage:.2f} status_led={status_on}")
            time.sleep(args.interval)
    except KeyboardInterrupt:
        print("\nShutting down")
    finally:
        client.loop_stop()
        client.disconnect()


if __name__ == "__main__":
    main()
