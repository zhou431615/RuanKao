@echo off
chcp 65001 >nul
title RuanKao 启动器

setlocal enabledelayedexpansion

set "ProjectRoot=%~dp0"
set "ProjectRoot=%ProjectRoot:~0,-1%"
set "RunDir=%ProjectRoot%\.run"
set "BackendPidFile=%RunDir%\backend.pid"
set "FrontendPidFile=%RunDir%\frontend.pid"
set "BackendLog=%RunDir%\backend.log"
set "BackendErrorLog=%RunDir%\backend.error.log"
set "FrontendLog=%RunDir%\frontend.log"
set "FrontendErrorLog=%RunDir%\frontend.error.log"

:: ========================================
:: 显示标题
:: ========================================
echo ========================================
echo         RuanKao 启动脚本
echo ========================================
echo.

:: ========================================
:: 处理停止命令
:: ========================================
if /i "%1"=="-Stop" goto :stop_service
if /i "%1"=="--Stop" goto :stop_service
if /i "%1"=="stop" goto :stop_service
if /i "%1"=="-stop" goto :stop_service

:: ========================================
:: 检查端口占用
:: ========================================
echo [信息] 检查端口占用情况...
set "PORT_BUSY=0"

netstat -ano | findstr ":8080 " | findstr "LISTENING" >nul
if not errorlevel 1 (
    echo [警告] 端口 8080 已被占用！
    set "PORT_BUSY=1"
)

netstat -ano | findstr ":5173 " | findstr "LISTENING" >nul
if not errorlevel 1 (
    echo [警告] 端口 5173 已被占用！
    set "PORT_BUSY=1"
)

if "%PORT_BUSY%"=="1" (
    echo.
    echo ========================================
    echo 警告：部分端口已被占用！
    echo 请先关闭占用端口的程序，或运行：
    echo   start.bat -Stop
    echo 来停止已启动的服务。
    echo ========================================
    echo.
    pause
    exit /b 1
)

:: ========================================
:: 检查 JDK 环境
:: ========================================
echo [信息] 检查 Java 环境...
set "JAVA_HOME_FOUND="

:: 优先使用 JAVA_HOME
if not "%JAVA_HOME%"=="" (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_HOME_FOUND=%JAVA_HOME%"
        echo [信息] 使用 JAVA_HOME: !JAVA_HOME_FOUND!
        goto :java_ok
    )
)

:: 检查常见 JDK 安装位置
set "JDK_PATHS=C:\Program Files\Java\jdk-17;C:\Program Files\Java\jdk-21;C:\Program Files\Java\jdk-23;%USERPROFILE%\.jdks\corretto-21.0.12;%USERPROFILE%\.jdks\corretto-17;%USERPROFILE%\.jdks\corretto-11"
for %%j in (%JDK_PATHS%) do (
    if exist "%%j\bin\java.exe" (
        set "JAVA_HOME_FOUND=%%j"
        echo [信息] 在 %%j 找到 JDK
        goto :java_ok
    )
)

:: 检查 PATH 中的 Java
where java.exe >nul 2>&1
if not errorlevel 1 (
    for /f "delims=" %%i in ('where java.exe 2^>nul') do (
        set "JAVA_HOME_FOUND=%%~dpi.."
        echo [信息] 在 PATH 中找到 Java: %%i
        goto :java_ok
    )
)

echo [错误] 未找到 JDK 17+！
echo 请安装 JDK 17 或更高版本，并设置 JAVA_HOME 环境变量。
pause
exit /b 1

:java_ok
if not "!JAVA_HOME_FOUND!"=="" (
    set "JAVA_HOME=!JAVA_HOME_FOUND!"
)

:: ========================================
:: 检查 Node.js
:: ========================================
echo [信息] 检查 Node.js 环境...
where node.exe >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Node.js！
    echo 请安装 Node.js 18 或更高版本，并确保 node.exe 在 PATH 中。
    pause
    exit /b 1
)
for /f "delims=" %%i in ('node --version 2^>nul') do echo [信息] Node.js 版本: %%i

:: ========================================
:: 检查前端依赖
:: ========================================
echo [信息] 检查前端依赖...
if exist "%ProjectRoot%\frontend\node_modules" (
    echo [信息] 前端依赖已安装。
) else (
    echo [警告] 前端依赖未安装！
    echo 请先运行: cd frontend ^&^& npm install
    echo.
    pause
    exit /b 1
)

:: ========================================
:: 创建 .run 目录
:: ========================================
if not exist "%RunDir%" (
    mkdir "%RunDir%"
    echo [信息] 创建目录: %RunDir%
)

:: ========================================
:: 查找 JAR 文件（排除 -plain.jar）
:: ========================================
set "JAR_FILE="
if exist "%ProjectRoot%\build\libs" (
    for %%f in ("%ProjectRoot%\build\libs\*.jar") do (
        set "filename=%%~nxf"
        :: 检查是否不是 -plain.jar
        echo !filename! | findstr /v "\-plain\.jar$" >nul
        if not errorlevel 1 (
            set "JAR_FILE=%%f"
            goto :jar_found
        )
    )
)
:jar_found

:: ========================================
:: 启动后端
:: ========================================
echo.
echo [信息] 正在启动后端服务 (http://localhost:8080)...
echo [信息] 这可能需要 20-60 秒，请耐心等待...

:: 根据是否有 JAR 文件决定启动方式
if not "%JAR_FILE%"=="" (
    echo [信息] 使用 JAR 文件: %JAR_FILE%
    :: 使用 start /b 在后台运行 Java
    start /b "" "%JAVA_HOME%\bin\java.exe" -jar "%JAR_FILE%" > "%BackendLog%" 2> "%BackendErrorLog%"
) else (
    echo [信息] 未找到可执行的 JAR 文件，正在使用 Gradle 打包并运行...
    echo [信息] 执行: gradlew.bat bootRun
    :: 使用 start /b 运行 gradlew
    start /b "" cmd /c cd /d "%ProjectRoot%" ^&^& .\gradlew.bat bootRun --console=plain > "%BackendLog%" 2> "%BackendErrorLog%"
)

:: 等待后端启动
set "BACKEND_READY=0"
set "WAIT_COUNT=0"
:wait_backend
set /a WAIT_COUNT+=1
if %WAIT_COUNT% gtr 120 (
    echo [错误] 后端启动超时！
    echo 请检查日志: %BackendErrorLog%
    goto :error_exit
)

:: 检查端口是否可用
netstat -ano | findstr ":8080 " | findstr "LISTENING" >nul
if not errorlevel 1 (
    set "BACKEND_READY=1"
    echo [信息] 后端启动成功！
) else (
    timeout /t 1 /nobreak >nul
    goto :wait_backend
)

:: 获取后端 PID
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080 " ^| findstr "LISTENING"') do (
    echo %%a > "%BackendPidFile%"
    goto :backend_pid_done
)
:backend_pid_done

:: ========================================
:: 启动前端
:: ========================================
echo.
echo [信息] 正在启动前端服务 (http://localhost:5173)...

set "VITE_CMD=%ProjectRoot%\frontend\node_modules\.bin\vite.cmd"
if not exist "%VITE_CMD%" (
    echo [错误] 未找到 vite.cmd，请先运行 npm install
    goto :error_exit
)

:: 启动前端进程
start /b "" cmd /c cd /d "%ProjectRoot%\frontend" ^&^& "%VITE_CMD%" --host 0.0.0.0 > "%FrontendLog%" 2> "%FrontendErrorLog%"

:: 等待前端启动
set "FRONTEND_READY=0"
set "WAIT_COUNT=0"
:wait_frontend
set /a WAIT_COUNT+=1
if %WAIT_COUNT% gtr 60 (
    echo [错误] 前端启动超时！
    echo 请检查日志: %FrontendErrorLog%
    goto :error_exit
)

netstat -ano | findstr ":5173 " | findstr "LISTENING" >nul
if not errorlevel 1 (
    set "FRONTEND_READY=1"
    echo [信息] 前端启动成功！
) else (
    timeout /t 1 /nobreak >nul
    goto :wait_frontend
)

:: 获取前端 PID
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":5173 " ^| findstr "LISTENING"') do (
    echo %%a > "%FrontendPidFile%"
    goto :frontend_pid_done
)
:frontend_pid_done

:: ========================================
:: 启动成功
:: ========================================
echo.
echo ========================================
echo          启动成功！服务正在运行中
echo ========================================
echo.
echo 访问地址：
echo   前端: http://localhost:5173
echo   API:  http://localhost:8080/api
echo   Swagger: http://localhost:8080/swagger-ui.html
echo.
echo 日志文件位于: %RunDir%
echo   - backend.log
echo   - backend.error.log
echo   - frontend.log
echo   - frontend.error.log
echo.
echo 停止服务请运行: start.bat -Stop
echo ========================================
echo.
pause
exit /b 0

:: ========================================
:: 停止服务
:: ========================================
:stop_service
echo [信息] 正在停止 RuanKao 服务...

:: 读取并停止后端进程
if exist "%BackendPidFile%" (
    set /p BACKEND_PID=<"%BackendPidFile%"
    if not "!BACKEND_PID!"=="" (
        taskkill /PID !BACKEND_PID! /F 2>nul
        if not errorlevel 1 (echo [信息] 已停止后端进程 PID: !BACKEND_PID!) else (echo [信息] 后端进程已不存在)
    )
    del "%BackendPidFile%" 2>nul
)

:: 读取并停止前端进程
if exist "%FrontendPidFile%" (
    set /p FRONTEND_PID=<"%FrontendPidFile%"
    if not "!FRONTEND_PID!"=="" (
        taskkill /PID !FRONTEND_PID! /F 2>nul
        if not errorlevel 1 (echo [信息] 已停止前端进程 PID: !FRONTEND_PID!) else (echo [信息] 前端进程已不存在)
    )
    del "%FrontendPidFile%" 2>nul
)

:: 强制清理端口占用进程
echo [信息] 清理端口占用...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080 " ^| findstr "LISTENING"') do (
    taskkill /PID %%a /F 2>nul
    if not errorlevel 1 (echo [信息] 已清理端口 8080 进程 PID: %%a)
)
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":5173 " ^| findstr "LISTENING"') do (
    taskkill /PID %%a /F 2>nul
    if not errorlevel 1 (echo [信息] 已清理端口 5173 进程 PID: %%a)
)

echo.
echo [信息] 停止操作完成！
pause
exit /b 0

:: ========================================
:: 错误退出
:: ========================================
:error_exit
echo.
echo ========================================
echo           启动失败！
echo ========================================
echo.
echo 请检查日志文件:
echo   - %BackendLog%
echo   - %BackendErrorLog%
echo   - %FrontendLog%
echo   - %FrontendErrorLog%
echo.
pause
exit /b 1