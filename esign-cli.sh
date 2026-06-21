#!/usr/bin/env bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec java -cp "$DIR/dist/eSignASPLibrary5_10.jar:$DIR/lib/*" com.emudhra.esign.cli.ESignCLI "$@"
