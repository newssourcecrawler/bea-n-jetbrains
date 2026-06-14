# bea-n JetBrains Plugin

bea-n JetBrains Plugin is a thin IDE wrapper for the local bea-n CLI.

It reads selected editor text, sends that text to the local bea-n binary, and shows a small case report for path, message, and device failure evidence.

It is built for noisy developer environments where logs mention Kafka, MQTT, DNS, TLS, HTTP, Bluetooth, IoT devices, or generic path failures.

## What it does

* Reads selected text from the editor.
* Runs the local bea-n CLI in read-only mode.
* Shows a readable case report.
* Copies a bounded prompt packet for assistant handoff.

## What it does not do

bea-n JetBrains Plugin does not connect to brokers, networks, DNS, Bluetooth devices, infrastructure, or services.

It does not collect credentials, rewrite code, rewrite configuration, apply fixes, or perform admin actions.

## CLI requirement

This plugin requires the local bea-n CLI to be installed separately.

The plugin looks for the CLI at:

~/.local/bin/bea-n
/opt/homebrew/bin/bea-n
/usr/local/bin/bea-n

The plugin does not download or run remote code automatically.

## License

This JetBrains wrapper is MIT licensed.

The bea-n CLI is distributed separately.
