@echo off

for /L %%i in (1,1,50) do (
    start /B powershell -Command "curl http://localhost:8080/api/display/status > $null"  -UseBasicParsing
)

timeout /t 1 > nul