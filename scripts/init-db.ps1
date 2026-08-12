# init-db.ps1：xLumen 数据库初始化脚本（M01，GLOBAL.md §6.3 / BACKEND.md §17.1）
# 用法：./scripts/init-db.ps1 -EnvFile "./backend/xlumen-server/config/.env" [-Reset]
# 行为：解析 .env 的 KEY=VALUE 行（跳过 # 注释），读取 XLUMEN_DB_URL/XLUMEN_DB_USERNAME/XLUMEN_DB_PASSWORD，
#       按文件名编号顺序执行 backend/xlumen-server/sql/init/ 全部脚本。
# -Reset：数据库名必须是个人开发库（xlumen_dev）或 xlumen_test，执行前显示服务器地址和数据库名并要求二次确认；
#         禁止对共享或正式数据执行重置。
param(
    [string]$EnvFile = "../backend/xlumen-server/config/.env",
    [switch]$Reset
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# ---------- 定位仓库根（脚本位于 <root>/scripts/） ----------
$RepoRoot = Split-Path -Parent $PSScriptRoot
$EnvFilePath = if ([System.IO.Path]::IsPathRooted($EnvFile)) { $EnvFile } else { Join-Path (Get-Location) $EnvFile }
if (-not (Test-Path $EnvFilePath)) {
    throw ".env 文件不存在：$EnvFilePath（先从 config/.env.example 复制并填写真实值）"
}

# ---------- 解析 .env（KEY=VALUE，跳过 # 注释；UTF-8 无 BOM） ----------
$envVars = @{}
Get-Content -Path $EnvFilePath -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $idx = $line.IndexOf('=')
        if ($idx -gt 0) {
            $envVars[$line.Substring(0, $idx).Trim()] = $line.Substring($idx + 1).Trim()
        }
    }
}

$dbUrl = $envVars['XLUMEN_DB_URL']
$dbUser = $envVars['XLUMEN_DB_USERNAME']
$dbPass = $envVars['XLUMEN_DB_PASSWORD']
if (-not $dbUrl -or -not $dbUser) {
    throw ".env 缺少 XLUMEN_DB_URL 或 XLUMEN_DB_USERNAME"
}

# ---------- 从 JDBC URL 解析 host/port/database ----------
if ($dbUrl -match 'jdbc:mysql://([^:/]+):?(\d+)?/([^?]+)?') {
    $dbHost = $Matches[1]
    $dbPort = if ($Matches[2]) { $Matches[2] } else { '3306' }
    $dbName = if ($envVars['XLUMEN_DB_NAME']) { $envVars['XLUMEN_DB_NAME'] } else { $Matches[3] }
} else {
    throw "XLUMEN_DB_URL 格式无法解析：$dbUrl"
}

Write-Host "==> 目标服务器：${dbHost}:${dbPort}，数据库：$dbName，用户：$dbUser"

# ---------- 定位 mysql 客户端 ----------
$mysql = (Get-Command mysql -ErrorAction SilentlyContinue).Source
if (-not $mysql) {
    $candidates = @(
        "$env:MYSQL_HOME\bin\mysql.exe",
        "D:\calwen\environment\mysql-8.4.0-winx64\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
    )
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c)) { $mysql = $c; break }
    }
}
if (-not $mysql) {
    throw "未找到 mysql 客户端：请安装 MySQL 8.4 客户端并加入 PATH，或设置 MYSQL_HOME"
}

# ---------- -Reset 安全闸门 ----------
if ($Reset) {
    if ($dbName -notin @('xlumen_dev', 'xlumen_test')) {
        throw "-Reset 仅允许个人开发库 xlumen_dev 或测试库 xlumen_test，当前为：$dbName（禁止对共享或正式数据执行重置）"
    }
    Write-Warning "即将 DROP DATABASE [$dbName]（服务器 ${dbHost}:${dbPort}）并重新初始化！"
    $confirm = Read-Host "确认执行请输入数据库名 [$dbName]"
    if ($confirm -ne $dbName) {
        Write-Host "已取消。"
        return
    }
    $env:MYSQL_PWD = $dbPass
    try {
        & $mysql --host=$dbHost --port=$dbPort --user=$dbUser --default-character-set=utf8mb4 `
            -e "DROP DATABASE IF EXISTS ``$dbName``;"
    } finally {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

# ---------- 按编号顺序执行 sql/init/ 全部脚本 ----------
$sqlDir = Join-Path $RepoRoot "backend\xlumen-server\sql\init"
if (-not (Test-Path $sqlDir)) {
    throw "SQL 目录不存在：$sqlDir"
}
$scripts = Get-ChildItem -Path $sqlDir -Filter *.sql | Sort-Object Name
if ($scripts.Count -eq 0) {
    throw "sql/init/ 目录为空"
}

$env:MYSQL_PWD = $dbPass
try {
    foreach ($script in $scripts) {
        Write-Host "==> 执行 $($script.Name)"
        if ($script.Name -like '00_*') {
            # 00_database.sql 负责建库，连接时不指定默认库（服务器已预建库时该脚本幂等可跳过）
            Get-Content -Path $script.FullName -Raw -Encoding UTF8 | & $mysql `
                --host=$dbHost --port=$dbPort --user=$dbUser --default-character-set=utf8mb4
        } else {
            Get-Content -Path $script.FullName -Raw -Encoding UTF8 | & $mysql `
                --host=$dbHost --port=$dbPort --user=$dbUser --database=$dbName --default-character-set=utf8mb4
        }
        if ($LASTEXITCODE -ne 0) {
            throw "脚本执行失败：$($script.Name)（退出码 $LASTEXITCODE）"
        }
    }
} finally {
    # 密码通过 MYSQL_PWD 传递，执行完毕立即清理（不写入命令行与日志）
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

Write-Host "==> 数据库初始化完成：${dbHost}:${dbPort}/$dbName（共 $($scripts.Count) 个脚本）"
