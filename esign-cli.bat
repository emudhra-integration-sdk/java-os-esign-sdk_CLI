@echo off
setlocal
set SCRIPT_DIR=%~dp0
java -cp "%SCRIPT_DIR%dist\eSignASPLibrary5_11.jar;%SCRIPT_DIR%lib\*" com.emudhra.esign.cli.ESignCLI %*
endlocal
